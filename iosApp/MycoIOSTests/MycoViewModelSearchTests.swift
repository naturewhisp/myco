import CoreLocation
import Foundation
import XCTest
@testable import MycoIOS

@MainActor
final class MycoViewModelSearchTests: XCTestCase {
    func testNewSearchCancelsStaleResult() async throws {
        let viewModel = MycoViewModel(locationSearch: DelayedSearchService())

        viewModel.submitSearch(query: "prima")
        viewModel.submitSearch(query: "seconda")
        try await Task.sleep(for: .milliseconds(100))

        XCTAssertEqual(viewModel.searchResults.map(\.name), ["seconda"])
        XCTAssertFalse(viewModel.isSearching)
    }

    func testLocationBWinsWhenLocationANonCooperativeRequestsFinishLast() async throws {
        let gate = FirstBatchGate(blockedRequestCount: 3)
        let viewModel = makeViewModel(networkGate: gate)

        viewModel.select(coordinate: CLLocationCoordinate2D(latitude: 42, longitude: 12), name: "A")
        await gate.waitUntilBlocked()
        viewModel.select(coordinate: CLLocationCoordinate2D(latitude: 43, longitude: 13), name: "B")
        try await waitUntil { viewModel.forecast?.latitude == 43 && viewModel.analysis != nil }

        await gate.releaseFirstBatch()
        try await waitUntil { !viewModel.isLoadingEnvironment }

        XCTAssertEqual(viewModel.selectedLocation?.name, "B")
        XCTAssertEqual(viewModel.forecast?.latitude, 43)
    }

    func testSpeciesBWinsWhenSpeciesARequestsFinishLast() async throws {
        let gate = FirstBatchGate(blockedRequestCount: 3)
        let viewModel = makeViewModel(networkGate: gate)
        let speciesB = try XCTUnwrap(viewModel.speciesCatalog.first { $0.id == "macrolepiota_procera" })

        viewModel.select(coordinate: CLLocationCoordinate2D(latitude: 42, longitude: 12), name: "Species race")
        await gate.waitUntilBlocked()
        viewModel.chooseSpecies(speciesB)
        try await waitUntil { viewModel.analysis != nil && !viewModel.isLoadingEnvironment }

        await gate.releaseFirstBatch()
        try await waitUntil { !viewModel.isLoadingEnvironment }

        XCTAssertEqual(viewModel.selectedSpecies.id, speciesB.id)
        XCTAssertTrue(
            viewModel.analysis?.factors.contains { $0.label == "Idoneità suolo/margine" } == true,
            "The final factor set must belong to the saprotrophic species B, not stale species A."
        )
    }

    func testDelayedFoundationNoteForLocationADoesNotOverwriteLocationB() async throws {
        let noteGenerator = NonCooperativeFieldNoteGenerator()
        let viewModel = makeViewModel(fieldNoteGenerator: noteGenerator)

        viewModel.select(coordinate: CLLocationCoordinate2D(latitude: 42, longitude: 12), name: "A")
        await noteGenerator.waitUntilFirstCallBegins()
        viewModel.select(coordinate: CLLocationCoordinate2D(latitude: 43, longitude: 13), name: "B")
        try await waitUntil { viewModel.fieldNote == "AI-B" }

        await noteGenerator.releaseFirstCall()
        try await waitUntil { !viewModel.isLoadingEnvironment }

        XCTAssertEqual(viewModel.selectedLocation?.name, "B")
        XCTAssertEqual(viewModel.fieldNote, "AI-B")
    }

    func testFullyOnlineEnvironmentHasNoOfflineFallbackOrLiveSourceMarkers() async throws {
        let viewModel = makeViewModel()

        viewModel.select(coordinate: CLLocationCoordinate2D(latitude: 42, longitude: 12), name: "Online")
        try await waitUntil { viewModel.analysis != nil && !viewModel.isLoadingEnvironment }

        let analysis = try XCTUnwrap(viewModel.analysis)
        XCTAssertFalse(viewModel.isOfflineFallback)
        XCTAssertFalse(analysis.missingSources.contains { $0.contains("live") })
    }

    private func makeViewModel(
        networkGate: FirstBatchGate? = nil,
        fieldNoteGenerator: any FieldNoteGenerating = ImmediateFieldNoteGenerator()
    ) -> MycoViewModel {
        let weatherLoader = NonCooperativeDataLoader(gate: networkGate) { request in
            let requestedLatitude = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?
                .queryItems?
                .first(where: { $0.name == "latitude" })?
                .value
                .flatMap(Double.init)
            let latitude = requestedLatitude == 43 ? 43.0 : 42.0
            if request.url?.path.contains("elevation") == true {
                return Data("{\"latitude\":[\(latitude)],\"longitude\":[12],\"elevation\":[500]}".utf8)
            }
            return forecastPayload(latitude: latitude)
        }
        let habitatLoader = NonCooperativeDataLoader(gate: networkGate) { _ in
            Data("{\"version\":0.6,\"elements\":[]}".utf8)
        }
        return MycoViewModel(
            openMeteo: OpenMeteoClient(
                apiClient: APIClient(loader: weatherLoader),
                forecastURL: URL(string: "https://weather.test/forecast")!,
                elevationURL: URL(string: "https://weather.test/elevation")!
            ),
            overpass: OverpassClient(
                apiClient: APIClient(loader: habitatLoader),
                endpoints: [URL(string: "https://overpass.test/api")!]
            ),
            fieldNoteGenerator: fieldNoteGenerator
        )
    }

    private func waitUntil(
        timeout: Duration = .seconds(5),
        condition: @escaping @MainActor () -> Bool
    ) async throws {
        let deadline = ContinuousClock.now + timeout
        while !condition() {
            guard ContinuousClock.now < deadline else {
                XCTFail("Timed out waiting for deterministic async ViewModel state")
                return
            }
            try await Task.sleep(for: .milliseconds(10))
        }
    }
}

private func forecastPayload(latitude: Double) -> Data {
    Data("""
    {"latitude":\(latitude),"longitude":12.0,"elevation":500.0,"timezone":"Europe/Rome","hourly":{"time":["2026-09-13T12:00"],"temperature_2m":[16.0],"relative_humidity_2m":[80.0],"precipitation":[3.0],"soil_moisture_0_to_7cm":[0.35],"soil_moisture_7_to_28cm":[0.42],"et0_fao_evapotranspiration":[0.2]},"daily":{"time":["2026-09-13"],"weather_code":[3],"precipitation_sum":[3.0],"temperature_2m_max":[18.0],"temperature_2m_min":[14.0]}}
    """.utf8)
}

private final class NonCooperativeDataLoader: HTTPDataLoading, @unchecked Sendable {
    private let gate: FirstBatchGate?
    private let payload: @Sendable (URLRequest) -> Data

    init(gate: FirstBatchGate?, payload: @escaping @Sendable (URLRequest) -> Data) {
        self.gate = gate
        self.payload = payload
    }

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        await gate?.blockFirstBatchIfNeeded()
        return (payload(request), httpResponse(for: request))
    }
}

private actor FirstBatchGate {
    private let blockedRequestCount: Int
    private var blockedRequests = 0
    private var hasReachedBlockedCount = false
    private var startWaiter: CheckedContinuation<Void, Never>?
    private var releaseWaiters: [CheckedContinuation<Void, Never>] = []

    init(blockedRequestCount: Int) {
        self.blockedRequestCount = blockedRequestCount
    }

    func blockFirstBatchIfNeeded() async {
        guard blockedRequests < blockedRequestCount else { return }
        blockedRequests += 1
        if blockedRequests == blockedRequestCount {
            hasReachedBlockedCount = true
            startWaiter?.resume()
            startWaiter = nil
        }
        await withCheckedContinuation { releaseWaiters.append($0) }
    }

    func waitUntilBlocked() async {
        guard !hasReachedBlockedCount else { return }
        await withCheckedContinuation { startWaiter = $0 }
    }

    func releaseFirstBatch() {
        let waiters = releaseWaiters
        releaseWaiters = []
        waiters.forEach { $0.resume() }
    }
}

private actor NonCooperativeFieldNoteGenerator: FieldNoteGenerating {
    private var callCount = 0
    private var firstCallStarted = false
    private var startWaiter: CheckedContinuation<Void, Never>?
    private var firstCallRelease: CheckedContinuation<Void, Never>?

    func enrich(deterministicNote: String) async -> String {
        callCount += 1
        guard callCount == 1 else { return "AI-B" }
        firstCallStarted = true
        startWaiter?.resume()
        startWaiter = nil
        await withCheckedContinuation { firstCallRelease = $0 }
        return "AI-A"
    }

    func waitUntilFirstCallBegins() async {
        guard !firstCallStarted else { return }
        await withCheckedContinuation { startWaiter = $0 }
    }

    func releaseFirstCall() {
        firstCallRelease?.resume()
        firstCallRelease = nil
    }
}

private struct ImmediateFieldNoteGenerator: FieldNoteGenerating {
    func enrich(deterministicNote: String) async -> String { deterministicNote }
}

@MainActor
private struct DelayedSearchService: LocationSearching {
    func search(query: String) async throws -> [LocationSearchResult] {
        try await Task.sleep(for: query == "prima" ? .milliseconds(80) : .milliseconds(5))
        return [
            LocationSearchResult(
                id: query,
                name: query,
                detail: nil,
                coordinate: CLLocationCoordinate2D(latitude: 42, longitude: 12)
            ),
        ]
    }
}
