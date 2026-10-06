import MycoCore
import SwiftData
import XCTest
@testable import MycoIOS

@MainActor
final class MycoViewModelEnvironmentTests: XCTestCase {
    func testMissingElevationAndHabitatProduceExplicitPartialAnalysis() async throws {
        let today = Self.todayIsoString()
        let forecastPayload = Self.completeForecastPayload(date: today, timezone: "Europe/Rome")
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

        viewModel.select(coordinate: GeoCoordinates(latitude: 50, longitude: 2), name: "Fuori copertura SPUN")
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

        viewModel.select(coordinate: GeoCoordinates(latitude: 42, longitude: 12), name: "Test")
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

        viewModel.select(coordinate: GeoCoordinates(latitude: 42, longitude: 12), name: "Offline")
        try await waitUntil { viewModel.analysis != nil }

        let analysis = try XCTUnwrap(viewModel.analysis)
        XCTAssertTrue(viewModel.isOfflineFallback)
        XCTAssertTrue(analysis.missingSources.contains("meteo live (cache)"))
        XCTAssertTrue(analysis.deterministicFieldNote.contains("meteo live (cache)"))
    }

    func testCachedElevationAndHabitatAreExplicitlyMarkedAsCached() async throws {
        let cache = try makeCacheStore()
        try await cache.put(key: "terrain_42.0000_12.0000", payload: Data(#"{"latitude":[42],"longitude":[12],"elevation":[500]}"#.utf8))
        try await cache.put(key: "habitat_geom_v2_42.0000_12.0000", payload: Data(#"{"score":0.9,"description":"Habitat cached","canopyTypes":["fagus"],"canopyCover":0.7,"forestProximityIndex":0.7}"#.utf8))
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

        viewModel.select(coordinate: GeoCoordinates(latitude: 42, longitude: 12), name: "Cached sources")
        try await waitUntil { viewModel.analysis != nil }

        let analysis = try XCTUnwrap(viewModel.analysis)
        XCTAssertTrue(viewModel.isOfflineFallback)
        XCTAssertTrue(analysis.missingSources.contains("quota DEM live (cache)"))
        XCTAssertTrue(analysis.missingSources.contains("habitat OSM live (cache)"))
        XCTAssertTrue(analysis.missingSources.contains("geometrie habitat OSM incomplete"))
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

        viewModel.select(coordinate: GeoCoordinates(latitude: 42, longitude: 12), name: "Corrupt cache")
        try await waitUntil { !viewModel.isLoadingEnvironment }

        XCTAssertNil(viewModel.analysis)
        XCTAssertNotNil(viewModel.errorMessage)
        XCTAssertFalse(viewModel.isOfflineFallback)
    }

    func testDeterministicClockInjectionControlsTodayAlignment() async throws {
        let fixedDateIso = "2026-10-05"
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        let fixedDate = try XCTUnwrap(formatter.date(from: fixedDateIso))

        let forecastPayload = Self.completeForecastPayload(date: fixedDateIso, timezone: "UTC")
        let weatherLoader = TestHTTPDataLoader { request in (forecastPayload, httpResponse(for: request)) }
        let unavailable = TestHTTPDataLoader { _ in throw URLError(.notConnectedToInternet) }

        let viewModel = MycoViewModel(
            openMeteo: OpenMeteoClient(apiClient: APIClient(loader: weatherLoader)),
            overpass: OverpassClient(apiClient: APIClient(loader: unavailable)),
            clock: { fixedDate }
        )

        viewModel.select(coordinate: GeoCoordinates(latitude: 42, longitude: 12), name: "Fixed Clock Test")
        try await waitUntil { viewModel.analysis != nil }

        let analysis: AnalysisResult = try XCTUnwrap(viewModel.analysis)
        XCTAssertTrue(analysis.isCalculable)
        XCTAssertEqual(viewModel.environmentalDays.first?.date, fixedDate)
    }

    private var forecastPayload: Data {
        let today = Self.todayIsoString()
        return Self.completeForecastPayload(date: today, timezone: "Europe/Rome")
    }

    private static func completeForecastPayload(date: String, timezone: String) -> Data {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: timezone) ?? TimeZone(secondsFromGMT: 0)!
        let dateFormatter = DateFormatter()
        dateFormatter.calendar = calendar
        dateFormatter.locale = Locale(identifier: "en_US_POSIX")
        dateFormatter.timeZone = calendar.timeZone
        dateFormatter.dateFormat = "yyyy-MM-dd HH:mm"
        let start = dateFormatter.date(from: "\(date) 00:00") ?? Date()
        let end = calendar.date(byAdding: .day, value: 1, to: start) ?? start.addingTimeInterval(24 * 60 * 60)
        let hourlyDates = Array(stride(from: start, to: end, by: 60 * 60))
        dateFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm"
        let localTimes = hourlyDates.map { dateFormatter.string(from: $0) }
        let repeatedTimes = Set(localTimes.filter { time in localTimes.filter { $0 == time }.count > 1 })
        dateFormatter.dateFormat = "XXX"
        let times = zip(hourlyDates, localTimes).map { (date, localTime) in
            repeatedTimes.contains(localTime) ? "\(localTime)\(dateFormatter.string(from: date))" : localTime
        }
        let payload: [String: Any] = [
            "latitude": 42.0,
            "longitude": 12.0,
            "elevation": 450.0,
            "timezone": timezone,
            "hourly": [
                "time": times,
                "temperature_2m": Array(repeating: 16.0, count: times.count),
                "relative_humidity_2m": Array(repeating: 80.0, count: times.count),
                "precipitation": Array(repeating: 1.0, count: times.count),
                "soil_moisture_0_to_7cm": Array(repeating: 0.35, count: times.count),
                "soil_moisture_7_to_28cm": Array(repeating: 0.42, count: times.count),
                "et0_fao_evapotranspiration": Array(repeating: 0.1, count: times.count),
            ],
            "daily": [
                "time": [date],
                "weather_code": [3],
                "precipitation_sum": [Double(times.count)],
                "temperature_2m_max": [16.0],
                "temperature_2m_min": [16.0],
            ],
        ]
        return (try? JSONSerialization.data(withJSONObject: payload)) ?? Data()
    }

    private static func todayIsoString() -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Europe/Rome") ?? TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }

    private func makeCacheStore() throws -> CacheStore {
        let schema = Schema([CacheEntry.self])
        let container = try ModelContainer(for: schema, configurations: ModelConfiguration(schema: schema, isStoredInMemoryOnly: true))
        return CacheStore(modelContainer: container)
    }

    private func waitUntil(
        timeout: Duration = .seconds(5),
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
