import Foundation
import XCTest
@testable import MycoIOS

final class APIClientTests: XCTestCase {
    func testSendRejectsUnsuccessfulHTTPStatusAndPreservesBody() async {
        let body = Data("rate limited".utf8)
        let loader = TestHTTPDataLoader { request in
            (body, httpResponse(for: request, statusCode: 429))
        }
        let client = APIClient(loader: loader)
        let request = URLRequest(url: URL(string: "https://example.test/data")!)

        do {
            _ = try await client.send(request)
            XCTFail("Expected an unacceptable status error")
        } catch let APIClientError.unacceptableStatus(code, returnedBody) {
            XCTAssertEqual(code, 429)
            XCTAssertEqual(returnedBody, body)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testDecodeWrapsMalformedPayload() async {
        let loader = TestHTTPDataLoader { request in
            (Data("not-json".utf8), httpResponse(for: request))
        }
        let client = APIClient(loader: loader)
        let request = URLRequest(url: URL(string: "https://example.test/data")!)

        do {
            _ = try await client.decode([String].self, from: request)
            XCTFail("Expected a decoding error")
        } catch let APIClientError.decoding(message) {
            XCTAssertFalse(message.isEmpty)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }
}
