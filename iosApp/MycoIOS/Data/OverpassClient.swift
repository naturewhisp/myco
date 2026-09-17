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
        let center: Center?

        enum CodingKeys: String, CodingKey {
            case type, id, tags, center
            case latitude = "lat"
            case longitude = "lon"
        }
    }

    struct Center: Codable, Sendable {
        let latitude: Double
        let longitude: Double

        enum CodingKeys: String, CodingKey {
            case latitude = "lat"
            case longitude = "lon"
        }
    }
}

struct HabitatSnapshot: Codable, Sendable {
    let score: Double
    let description: String
    let canopyTypes: [String]
}

/// The two habitat acquisition strategies used by the shared ecological model.
/// Parasites use the tree-host strategy because OSM has no sufficiently reliable
/// tag for decaying wood at the required spatial resolution.
enum HabitatEcologicalCategory: Sendable {
    case saprotrophic
    case treeAssociated
}

struct OverpassClient: Sendable {
    static let defaultHabitatRadiusMeters = 1_500

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

    /// Mirrors the Android forest-count thresholds and preferred-canopy bonus query.
    func habitat(
        around coordinate: CLLocationCoordinate2D,
        radiusMeters: Int = Self.defaultHabitatRadiusMeters,
        preferredCanopyTypes: [String],
        ecologicalCategory: HabitatEcologicalCategory = .treeAssociated
    ) async throws -> HabitatSnapshot {
        let radius = min(max(radiusMeters, 1), 50_000)
        let forest = try await query(Self.forestQuery(around: coordinate, radiusMeters: radius))
        let forestCount = forest.elements.count
        let score: Double
        let description: String
        switch forestCount {
        case 16...:
            score = 1
            description = "Habitat ideale: punto immerso in area boschiva."
        case 5...:
            score = 0.95
            description = "Habitat promettente: vicinanza a boschi e foreste."
        case 1...:
            score = 0.6
            description = "Habitat misto: presenza di aree verdi sparse."
        default:
            score = 0.1
            description = "Habitat non ideale: nessun bosco rilevato nelle vicinanze."
        }

        let specificHabitat = try await query(
            Self.specificHabitatQuery(
                around: coordinate,
                radiusMeters: radius,
                preferredCanopyTypes: preferredCanopyTypes,
                ecologicalCategory: ecologicalCategory
            )
        )
        let detected: Set<String>
        if ecologicalCategory == .treeAssociated {
            detected = Set(specificHabitat.elements.compactMap { $0.tags?["genus"]?.lowercased() })
        } else {
            detected = specificHabitat.elements.isEmpty ? [] : ["saprotrophic_habitat"]
        }
        return HabitatSnapshot(score: score, description: description, canopyTypes: detected.sorted())
    }

    static func forestQuery(around coordinate: CLLocationCoordinate2D, radiusMeters: Int) -> String {
        let radius = min(max(radiusMeters, 1), 50_000)
        return "[out:json][timeout:25];(nwr[\"natural\"=\"wood\"](around:\(radius),\(coordinate.latitude),\(coordinate.longitude));nwr[\"landuse\"=\"forest\"](around:\(radius),\(coordinate.latitude),\(coordinate.longitude)););out center tags;"
    }

    /// Matches Android's category-specific Overpass acquisition: open habitats for
    /// saprotrophs, and forest canopy plus preferred host genera for tree-associated species.
    static func specificHabitatQuery(
        around coordinate: CLLocationCoordinate2D,
        radiusMeters: Int,
        preferredCanopyTypes: [String],
        ecologicalCategory: HabitatEcologicalCategory
    ) -> String {
        let radius = min(max(radiusMeters, 1), 50_000)
        let location = "(around:\(radius),\(coordinate.latitude),\(coordinate.longitude))"
        switch ecologicalCategory {
        case .saprotrophic:
            return "[out:json][timeout:25];(nwr[\"landuse\"~\"meadow|grass|pasture\"]\(location);nwr[\"natural\"~\"grassland|heath\"]\(location);nwr[\"leaf_type\"~\"broadleaved|needleleaved\"]\(location););out center tags;"
        case .treeAssociated:
            let genusRegex = preferredGenusRegex(from: preferredCanopyTypes)
            return "[out:json][timeout:25];(nwr[\"natural\"=\"wood\"]\(location);nwr[\"landuse\"=\"forest\"]\(location);nwr[\"leaf_type\"~\"broadleaved|needleleaved\"]\(location);nwr[\"genus\"~\"\(genusRegex)\"]\(location););out center tags;"
        }
    }

    private static func preferredGenusRegex(from canopyTypes: [String]) -> String {
        let knownGenera = [
            "fagus": "Fagus", "quercus": "Quercus", "castanea": "Castanea", "pinus": "Pinus",
            "picea": "Picea", "abies": "Abies", "betula": "Betula", "larix": "Larix",
            "populus": "Populus", "salix": "Salix", "ostrya": "Ostrya", "carpinus": "Carpinus",
            "corylus": "Corylus",
        ]
        let genera = canopyTypes.compactMap { knownGenera[$0.lowercased()] }
        return (genera.isEmpty ? ["Fagus", "Quercus", "Castanea", "Pinus", "Picea", "Abies"] : genera)
            .joined(separator: "|")
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
