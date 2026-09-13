import XCTest
@testable import MycoIOS

@MainActor
final class SpunBundleServiceTests: XCTestCase {
    func testBundledItalyAtlasIsReadableThroughSharedParser() throws {
        let sample = try XCTUnwrap(SpunBundleService().sample(latitude: 45, longitude: 10, radiusMeters: 100_000))

        XCTAssertEqual(sample.regionCode, "ALP")
        XCTAssertTrue((0 ... 1).contains(sample.ecmScore))
        XCTAssertTrue((0 ... 1).contains(sample.hyphalScore))
    }
}
