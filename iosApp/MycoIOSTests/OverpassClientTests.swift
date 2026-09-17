import CoreLocation
import Foundation
import XCTest
@testable import MycoIOS

final class OverpassClientTests: XCTestCase {
    func testSaprotrophicQueryTargetsOpenHabitatsAtDefaultRadius() {
        XCTAssertEqual(OverpassClient.defaultHabitatRadiusMeters, 1_500)
        let query = OverpassClient.specificHabitatQuery(
            around: CLLocationCoordinate2D(latitude: 41.9, longitude: 12.5),
            radiusMeters: OverpassClient.defaultHabitatRadiusMeters,
            preferredCanopyTypes: [],
            ecologicalCategory: .saprotrophic
        )

        XCTAssertTrue(query.contains("[\"landuse\"~\"meadow|grass|pasture\"](around:1500,41.9,12.5)"))
        XCTAssertTrue(query.contains("[\"natural\"~\"grassland|heath\"](around:1500,41.9,12.5)"))
        XCTAssertFalse(query.contains("[\"natural\"=\"wood\"]"))
        XCTAssertTrue(query.hasSuffix("out center tags;"))
    }

    func testTreeAssociatedQueryTargetsForestAndPreferredCanopyGenera() {
        let query = OverpassClient.specificHabitatQuery(
            around: CLLocationCoordinate2D(latitude: 45.4642, longitude: 9.19),
            radiusMeters: 2_000,
            preferredCanopyTypes: ["quercus", "FAGUS", "unknown"],
            ecologicalCategory: .treeAssociated
        )

        XCTAssertTrue(query.contains("[\"natural\"=\"wood\"](around:2000,45.4642,9.19)"))
        XCTAssertTrue(query.contains("[\"landuse\"=\"forest\"](around:2000,45.4642,9.19)"))
        XCTAssertTrue(query.contains("[\"genus\"~\"Quercus|Fagus\"](around:2000,45.4642,9.19)"))
        XCTAssertTrue(query.hasSuffix("out center tags;"))
    }

    func testSaprotrophicHabitatSignalsSpecificMatchToSharedCore() async throws {
        let payload = Data("""
        {"version":0.6,"elements":[{"type":"way","id":9,"tags":{"landuse":"meadow"}}]}
        """.utf8)
        let endpoint = URL(string: "https://overpass.test/api")!
        let loader = TestHTTPDataLoader { request in
            (payload, httpResponse(for: request))
        }
        let client = OverpassClient(apiClient: APIClient(loader: loader), endpoints: [endpoint])

        let snapshot = try await client.habitat(
            around: CLLocationCoordinate2D(latitude: 41.9, longitude: 12.5),
            preferredCanopyTypes: [],
            ecologicalCategory: .saprotrophic
        )

        XCTAssertEqual(snapshot.score, 0.6)
        XCTAssertEqual(snapshot.canopyTypes, ["saprotrophic_habitat"])
        XCTAssertEqual(loader.requests.count, 2)
    }

    func testQueryFailsOverToNextEndpoint() async throws {
        let payload = Data("""
        {"version":0.6,"elements":[{"type":"node","id":7,"lat":41.9,"lon":12.5,"tags":{"natural":"wood"}}]}
        """.utf8)
        let first = URL(string: "https://first.test/api")!
        let second = URL(string: "https://second.test/api")!
        let loader = TestHTTPDataLoader { request in
            if request.url?.host == first.host {
                throw URLError(.cannotConnectToHost)
            }
            return (payload, httpResponse(for: request))
        }
        let client = OverpassClient(apiClient: APIClient(loader: loader), endpoints: [first, second])

        let response = try await client.query("[out:json];out;")

        XCTAssertEqual(response.elements.count, 1)
        XCTAssertEqual(loader.requests.map { $0.url?.host }, ["first.test", "second.test"])
    }

    func testAllEndpointFailuresIncludeHosts() async {
        let endpoints = [URL(string: "https://first.test/api")!, URL(string: "https://second.test/api")!]
        let loader = TestHTTPDataLoader { _ in
            throw URLError(.cannotConnectToHost)
        }
        let client = OverpassClient(apiClient: APIClient(loader: loader), endpoints: endpoints)

        do {
            _ = try await client.query("query")
            XCTFail("Expected all-endpoints-failed error")
        } catch let OverpassClientError.allEndpointsFailed(failed) {
            XCTAssertEqual(failed, ["first.test", "second.test"])
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testCancellationIsNotConvertedToEndpointFailure() async {
        let loader = TestHTTPDataLoader { _ in
            throw CancellationError()
        }
        let client = OverpassClient(
            apiClient: APIClient(loader: loader),
            endpoints: [URL(string: "https://first.test/api")!, URL(string: "https://second.test/api")!]
        )

        do {
            _ = try await client.query("query")
            XCTFail("Expected cancellation")
        } catch is CancellationError {
            XCTAssertEqual(loader.requests.count, 1)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }
}
