import CoreLocation
import Foundation
import XCTest
@testable import MycoIOS

final class NominatimClientTests: XCTestCase {
    func testSearchDecodesStringCoordinatesAndBuildsRequest() async throws {
        let payload = Data("""
        [{"place_id":42,"display_name":"Roma, Italia","lat":"41.9028","lon":"12.4964","address":{"city":"Roma","country":"Italia","country_code":"it"}}]
        """.utf8)
        let loader = TestHTTPDataLoader { request in
            (payload, httpResponse(for: request))
        }
        let client = NominatimClient(
            apiClient: APIClient(loader: loader),
            baseURL: URL(string: "https://nominatim.test")!,
            userAgent: "MycoTests"
        )

        let places = try await client.search(query: "Roma", limit: 99, locale: Locale(identifier: "it-IT"))

        XCTAssertEqual(places.count, 1)
        XCTAssertEqual(places[0].coordinate.latitude, 41.9028, accuracy: 0.000001)
        XCTAssertEqual(places[0].coordinate.longitude, 12.4964, accuracy: 0.000001)
        let request = try XCTUnwrap(loader.requests.first)
        let components = try XCTUnwrap(URLComponents(url: try XCTUnwrap(request.url), resolvingAgainstBaseURL: false))
        XCTAssertEqual(components.queryItems?.first(where: { $0.name == "limit" })?.value, "50")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Accept-Language"), "it-IT")
        XCTAssertEqual(request.value(forHTTPHeaderField: "User-Agent"), "MycoTests")
    }

    func testEmptySearchDoesNotLoadNetwork() async throws {
        let loader = TestHTTPDataLoader { request in
            XCTFail("Empty queries must not issue a request")
            return (Data(), httpResponse(for: request))
        }
        let client = NominatimClient(apiClient: APIClient(loader: loader), baseURL: URL(string: "https://nominatim.test")!)

        let places = try await client.search(query: " \n\t")
        XCTAssertTrue(places.isEmpty)
        XCTAssertTrue(loader.requests.isEmpty)
    }
}
