import CoreLocation
import Foundation
import XCTest
@testable import MycoIOS

final class OverpassClientTests: XCTestCase {
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
