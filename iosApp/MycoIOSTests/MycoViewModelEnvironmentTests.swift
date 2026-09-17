import CoreLocation
import SwiftData
import XCTest
@testable import MycoIOS

@MainActor
final class MycoViewModelEnvironmentTests: XCTestCase {
    func testMissingElevationAndHabitatProduceExplicitPartialAnalysis() async throws {
        let forecastPayload = Data("""
        {"latitude":42.0,"longitude":12.0,"elevation":450.0,"timezone":"Europe/Rome","hourly":{"time":["2026-09-13T12:00"],"temperature_2m":[16.0],"relative_humidity_2m":[80.0],"precipitation":[3.0],"soil_moisture_0_to_7cm":[0.35],"soil_moisture_7_to_28cm":[0.42],"et0_fao_evapotranspiration":[0.2]},"daily":{"time":["2026-09-13"],"weather_code":[3],"precipitation_sum":[3.0],"temperature_2m_max":[18.0],"temperature_2m_min":[14.0]}}
        """.utf8)
        let weatherLoader = TestHTTPDataLoader { request in
            if request.url?.path.contains("elevation") == true { throw URLError(.notConnectedToInternet) }
            return (forecastPayload, httpResponse(for: request))
        }
        let habitatLoader = TestHTTPDataLoader { _ in throw URLError(.notConnectedToInternet) }
        let viewModel = MycoViewModel(
            openMeteo: OpenMeteoClient(
                apiClient: APIClient(loader: weatherLoader),
                forecastURL: URL(string: "https://weather.test/forecast")!,
                elevationURL: URL(string: "https://weather.test/elevation")!
            ),
            overpass: OverpassClient(
                apiClient: APIClient(loader: habitatLoader),
                endpoints: [URL(string: "https://overpass.test/api")!]
            )
        )

        viewModel.select(coordinate: CLLocationCoordinate2D(latitude: 50, longitude: 2), name: "Fuori copertura SPUN")
        try await waitUntil { viewModel.analysis != nil }

        let analysis = try XCTUnwrap(viewModel.analysis)
        XCTAssertTrue(analysis.missingSources.contains("quota DEM"))
        XCTAssertTrue(analysis.missingSources.contains("habitat OSM"))
        XCTAssertTrue(analysis.missingSources.contains("SPUN"))
        XCTAssertTrue(analysis.deterministicFieldNote.contains("risultato è parziale"))
        XCTAssertNil(viewModel.errorMessage)
    }

    func testMissingWeatherWithoutCacheProducesUnavailableState() async throws {
        let unavailable = TestHTTPDataLoader { _ in throw URLError(.notConnectedToInternet) }
        let viewModel = MycoViewModel(
            openMeteo: OpenMeteoClient(apiClient: APIClient(loader: unavailable)),
            overpass: OverpassClient(apiClient: APIClient(loader: unavailable))
        )

        viewModel.select(coordinate: CLLocationCoordinate2D(latitude: 42, longitude: 12), name: "Test")
        try await Task.sleep(for: .milliseconds(200))

        XCTAssertNil(viewModel.analysis)
        XCTAssertNotNil(viewModel.errorMessage)
        XCTAssertFalse(viewModel.isLoadingEnvironment)
    }

    func testCachedWeatherProducesAnalysisAndExplicitOfflineSource() async throws {
        let cache = try makeCacheStore()
        try await cache.put(key: "weather_42.0000_12.0000", payload: forecastPayload)
        let unavailable = TestHTTPDataLoader { _ in throw URLError(.notConnectedToInternet) }
        let viewModel = MycoViewModel(
            openMeteo: OpenMeteoClient(apiClient: APIClient(loader: unavailable)),
            overpass: OverpassClient(apiClient: APIClient(loader: unavailable)),
            cacheStore: cache
        )

        viewModel.select(coordinate: CLLocationCoordinate2D(latitude: 42, longitude: 12), name: "Offline")
        try await waitUntil { viewModel.analysis != nil }

        let analysis = try XCTUnwrap(viewModel.analysis)
        XCTAssertTrue(viewModel.isOfflineFallback)
        XCTAssertTrue(analysis.missingSources.contains("meteo live (cache)"))
        XCTAssertTrue(analysis.deterministicFieldNote.contains("meteo live (cache)"))
    }

    func testCachedElevationAndHabitatAreExplicitlyMarkedAsCached() async throws {
        let cache = try makeCacheStore()
        try await cache.put(key: "terrain_42.0000_12.0000", payload: Data(#"{"latitude":[42],"longitude":[12],"elevation":[500]}"#.utf8))
        try await cache.put(key: "habitat_general_42.0000_12.0000", payload: Data(#"{"score":0.9,"description":"Habitat cached","canopyTypes":["fagus"]}"#.utf8))
        let forecastPayload = forecastPayload
        let weatherLoader = TestHTTPDataLoader { request in
            if request.url?.path.contains("elevation") == true { throw URLError(.notConnectedToInternet) }
            return (forecastPayload, httpResponse(for: request))
        }
        let unavailable = TestHTTPDataLoader { _ in throw URLError(.notConnectedToInternet) }
        let viewModel = MycoViewModel(
            openMeteo: OpenMeteoClient(apiClient: APIClient(loader: weatherLoader)),
            overpass: OverpassClient(apiClient: APIClient(loader: unavailable)),
            cacheStore: cache
        )

        viewModel.select(coordinate: CLLocationCoordinate2D(latitude: 42, longitude: 12), name: "Cached sources")
        try await waitUntil { viewModel.analysis != nil }

        let analysis = try XCTUnwrap(viewModel.analysis)
        XCTAssertTrue(viewModel.isOfflineFallback)
        XCTAssertTrue(analysis.missingSources.contains("quota DEM live (cache)"))
        XCTAssertTrue(analysis.missingSources.contains("habitat OSM live (cache)"))
    }

    func testCorruptCachedWeatherIsIgnoredWithoutFabricatingAnalysis() async throws {
        let cache = try makeCacheStore()
        try await cache.put(key: "weather_42.0000_12.0000", payload: Data("not json".utf8))
        let unavailable = TestHTTPDataLoader { _ in throw URLError(.notConnectedToInternet) }
        let viewModel = MycoViewModel(
            openMeteo: OpenMeteoClient(apiClient: APIClient(loader: unavailable)),
            overpass: OverpassClient(apiClient: APIClient(loader: unavailable)),
            cacheStore: cache
        )

        viewModel.select(coordinate: CLLocationCoordinate2D(latitude: 42, longitude: 12), name: "Corrupt cache")
        try await waitUntil { !viewModel.isLoadingEnvironment }

        XCTAssertNil(viewModel.analysis)
        XCTAssertNotNil(viewModel.errorMessage)
        XCTAssertFalse(viewModel.isOfflineFallback)
    }

    private var forecastPayload: Data {
        Data("""
        {"latitude":42.0,"longitude":12.0,"elevation":450.0,"timezone":"Europe/Rome","hourly":{"time":["2026-09-13T12:00"],"temperature_2m":[16.0],"relative_humidity_2m":[80.0],"precipitation":[3.0],"soil_moisture_0_to_7cm":[0.35],"soil_moisture_7_to_28cm":[0.42],"et0_fao_evapotranspiration":[0.2]},"daily":{"time":["2026-09-13"],"weather_code":[3],"precipitation_sum":[3.0],"temperature_2m_max":[18.0],"temperature_2m_min":[14.0]}}
        """.utf8)
    }

    private func makeCacheStore() throws -> CacheStore {
        let schema = Schema([CacheEntry.self])
        let container = try ModelContainer(for: schema, configurations: ModelConfiguration(schema: schema, isStoredInMemoryOnly: true))
        return CacheStore(modelContainer: container)
    }

    private func waitUntil(
        timeout: Duration = .seconds(3),
        condition: @escaping @MainActor () -> Bool
    ) async throws {
        let deadline = ContinuousClock.now + timeout
        while !condition() {
            guard ContinuousClock.now < deadline else {
                XCTFail("Timed out waiting for asynchronous ViewModel state")
                return
            }
            try await Task.sleep(for: .milliseconds(20))
        }
    }
}
