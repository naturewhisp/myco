import XCTest
@testable import MycoIOS

@MainActor
final class SpunBundleServiceTests: XCTestCase {
    func testBundledItalyAtlasIsReadableThroughSharedParser() async throws {
        let result = try await SpunBundleService().sample(latitude: 45, longitude: 10, radiusMeters: 100_000)
        let sample = try XCTUnwrap(result)

        XCTAssertEqual(sample.regionCode, "ALP")
        XCTAssertTrue((0 ... 1).contains(sample.ecmScore))
        XCTAssertTrue((0 ... 1).contains(sample.hyphalScore))
    }

    func testConcurrentSamplesParseTheGridOnlyOnce() async throws {
        let service = SpunBundleService()
        let samples = try await withThrowingTaskGroup(of: SpunSampleValue?.self) { group in
            for longitude in stride(from: 9.5, through: 10.5, by: 0.1) {
                group.addTask {
                    try await service.sample(latitude: 45, longitude: longitude, radiusMeters: 10_000)
                }
            }

            var samples: [SpunSampleValue] = []
            for try await sample in group {
                if let sample { samples.append(sample) }
            }
            return samples
        }

        XCTAssertFalse(samples.isEmpty)
        let loadCount = await service.cachedGridLoadCount()
        XCTAssertEqual(loadCount, 1)
    }

    func testWarmSPUNOperationsAreMeasuredAndReuseTheParsedGrid() async throws {
        let service = SpunBundleService()
        let clock = ContinuousClock()

        let firstLoad = try await elapsed(using: clock) {
            try await service.sample(latitude: 45, longitude: 10, radiusMeters: 10_000)
        }
        let subsequentSample = try await elapsed(using: clock) {
            try await service.sample(latitude: 45.1, longitude: 10.1, radiusMeters: 10_000)
        }
        let speciesChange = try await elapsed(using: clock) {
            try await service.heatmap(
                latitude: 45,
                longitude: 10,
                weatherScore: 70,
                seasonalityScore: 0.8,
                altitudeScore: 0.9,
                speciesID: "boletus_edulis",
                isDark: false
            )
        }
        let paletteRefresh = try await elapsed(using: clock) {
            try await service.heatmap(
                latitude: 45,
                longitude: 10,
                weatherScore: 70,
                seasonalityScore: 0.8,
                altitudeScore: 0.9,
                speciesID: "boletus_edulis",
                isDark: true
            )
        }

        let loadCount = await service.cachedGridLoadCount()
        XCTAssertEqual(loadCount, 1)
        recordTiming(
            firstLoad: firstLoad,
            subsequentSample: subsequentSample,
            speciesChange: speciesChange,
            paletteRefresh: paletteRefresh
        )
    }

    private func elapsed<Value: Sendable>(
        using clock: ContinuousClock,
        operation: @escaping @Sendable () async throws -> Value
    ) async throws -> Duration {
        let start = clock.now
        _ = try await operation()
        return start.duration(to: clock.now)
    }

    private func recordTiming(
        firstLoad: Duration,
        subsequentSample: Duration,
        speciesChange: Duration,
        paletteRefresh: Duration
    ) {
        let attachment = XCTAttachment(
            string: """
            first SPUN load: \(firstLoad)
            subsequent sample: \(subsequentSample)
            species-change heatmap: \(speciesChange)
            palette-refresh heatmap: \(paletteRefresh)
            """
        )
        attachment.name = "SPUN operation timings"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
