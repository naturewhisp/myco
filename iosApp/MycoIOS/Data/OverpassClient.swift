import CoreLocation
import Foundation

enum OverpassClientError: LocalizedError, Sendable {
    case allEndpointsFailed([String])

    var errorDescription: String? {
        switch self {
        case let .allEndpointsFailed(endpoints):
            "No Overpass endpoint completed the request: \(endpoints.joined(separator: ", "))."
        }
    }
}

struct OverpassResponse: Codable, Sendable {
    let version: Double?
    let generator: String?
    let elements: [Element]

    struct Element: Codable, Identifiable, Sendable {
        let type: String
        let id: Int64
        let latitude: Double?
        let longitude: Double?
        let tags: [String: String]?

        enum CodingKeys: String, CodingKey {
            case type, id, tags
            case latitude = "lat"
            case longitude = "lon"
        }
    }
}

struct OverpassClient: Sendable {
    static let defaultEndpoints = [
        URL(string: "https://overpass-api.de/api/interpreter")!,
        URL(string: "https://overpass.kumi.systems/api/interpreter")!,
        URL(string: "https://overpass.openstreetmap.fr/api/interpreter")!,
    ]

    private let apiClient: APIClient
    private let endpoints: [URL]
    private let userAgent: String

    init(
        apiClient: APIClient = APIClient(),
        endpoints: [URL] = Self.defaultEndpoints,
        userAgent: String = "MycoIOS/0.1 (github.naturewhisp.myco.ios)"
    ) {
        self.apiClient = apiClient
        self.endpoints = endpoints
        self.userAgent = userAgent
    }

    func query(_ query: String) async throws -> OverpassResponse {
        var failedEndpoints: [String] = []
        for endpoint in endpoints {
            do {
                return try await apiClient.decode(OverpassResponse.self, from: request(query: query, endpoint: endpoint))
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                failedEndpoints.append(endpoint.host ?? endpoint.absoluteString)
            }
        }
        throw OverpassClientError.allEndpointsFailed(failedEndpoints)
    }

    func elements(around coordinate: CLLocationCoordinate2D, radiusMeters: Int, filter: String) async throws -> OverpassResponse {
        let radius = min(max(radiusMeters, 1), 50_000)
        let query = "[out:json][timeout:25];(nwr(around:\(radius),\(coordinate.latitude),\(coordinate.longitude))[\(filter)];);out center tags;"
        return try await self.query(query)
    }

    private func request(query: String, endpoint: URL) -> URLRequest {
        var components = URLComponents()
        components.queryItems = [URLQueryItem(name: "data", value: query)]
        var request = URLRequest(url: endpoint.appending(queryItems: components.queryItems ?? []))
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 35
        return request
    }
}
