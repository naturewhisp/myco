import CoreLocation
import Foundation

struct NominatimPlace: Codable, Identifiable, Sendable {
    let placeID: Int
    let displayName: String
    let latitude: Double
    let longitude: Double
    let address: NominatimAddress?

    var id: Int { placeID }
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: latitude, longitude: longitude) }

    enum CodingKeys: String, CodingKey {
        case placeID = "place_id"
        case displayName = "display_name"
        case latitude = "lat"
        case longitude = "lon"
        case address
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        placeID = try container.decode(Int.self, forKey: .placeID)
        displayName = try container.decode(String.self, forKey: .displayName)
        let latitudeString = try container.decode(String.self, forKey: .latitude)
        let longitudeString = try container.decode(String.self, forKey: .longitude)
        guard let decodedLatitude = Double(latitudeString), let decodedLongitude = Double(longitudeString) else {
            throw DecodingError.dataCorruptedError(
                forKey: .latitude,
                in: container,
                debugDescription: "Nominatim returned invalid coordinates."
            )
        }
        latitude = decodedLatitude
        longitude = decodedLongitude
        address = try container.decodeIfPresent(NominatimAddress.self, forKey: .address)
    }
}

struct NominatimAddress: Codable, Sendable {
    let city: String?
    let town: String?
    let village: String?
    let municipality: String?
    let state: String?
    let country: String?
    let countryCode: String?

    enum CodingKeys: String, CodingKey {
        case city, town, village, municipality, state, country
        case countryCode = "country_code"
    }
}

struct NominatimClient: Sendable {
    private let apiClient: APIClient
    private let baseURL: URL
    private let userAgent: String

    init(
        apiClient: APIClient = APIClient(),
        baseURL: URL = URL(string: "https://nominatim.openstreetmap.org")!,
        userAgent: String = "MycoIOS/0.1 (github.naturewhisp.myco.ios)"
    ) {
        self.apiClient = apiClient
        self.baseURL = baseURL
        self.userAgent = userAgent
    }

    func search(query: String, limit: Int = 8, locale: Locale = .current) async throws -> [NominatimPlace] {
        guard !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [] }
        var components = URLComponents(url: baseURL.appending(path: "search"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "format", value: "jsonv2"),
            URLQueryItem(name: "addressdetails", value: "1"),
            URLQueryItem(name: "limit", value: String(min(max(limit, 1), 50))),
        ]
        return try await apiClient.decode([NominatimPlace].self, from: request(for: components.url!, locale: locale))
    }

    func reverse(coordinate: CLLocationCoordinate2D, locale: Locale = .current) async throws -> NominatimPlace {
        var components = URLComponents(url: baseURL.appending(path: "reverse"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "lat", value: String(coordinate.latitude)),
            URLQueryItem(name: "lon", value: String(coordinate.longitude)),
            URLQueryItem(name: "format", value: "jsonv2"),
            URLQueryItem(name: "addressdetails", value: "1"),
        ]
        return try await apiClient.decode(NominatimPlace.self, from: request(for: components.url!, locale: locale))
    }

    private func request(for url: URL, locale: Locale) -> URLRequest {
        var request = URLRequest(url: url)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue(locale.identifier, forHTTPHeaderField: "Accept-Language")
        request.timeoutInterval = 20
        return request
    }
}
