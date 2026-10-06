import CoreLocation
import Foundation
import MycoCore

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
    let canopyCover: Double
    let forestProximityIndex: Double

    init(score: Double, description: String, canopyTypes: [String], canopyCover: Double = 0.0, forestProximityIndex: Double = 0.0) {
        self.score = score
        self.description = description
        self.canopyTypes = canopyTypes
        self.canopyCover = canopyCover
        self.forestProximityIndex = forestProximityIndex
    }

    private enum CodingKeys: String, CodingKey {
        case score
        case description
        case canopyTypes
        case canopyCover
        case forestProximityIndex
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.score = try container.decode(Double.self, forKey: .score)
        self.description = try container.decode(String.self, forKey: .description)
        self.canopyTypes = try container.decode([String].self, forKey: .canopyTypes)
        self.canopyCover = try container.decodeIfPresent(Double.self, forKey: .canopyCover) ?? 0.0
        self.forestProximityIndex = try container.decodeIfPresent(Double.self, forKey: .forestProximityIndex) ?? 0.0
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(score, forKey: .score)
        try container.encode(description, forKey: .description)
        try container.encode(canopyTypes, forKey: .canopyTypes)
        try container.encode(canopyCover, forKey: .canopyCover)
        try container.encode(forestProximityIndex, forKey: .forestProximityIndex)
    }
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

    /// Extracts structured habitat evidence from Overpass elements in lockstep with Android and :core.
    func extractHabitatEvidence(
        from elements: [OverpassResponse.Element],
        around coordinate: CLLocationCoordinate2D,
        radiusMeters: Int = Self.defaultHabitatRadiusMeters
    ) -> HabitatEvidence {
        let osmElements: [OsmHabitatElement] = elements.map { el in
            let lat = el.latitude ?? el.center?.latitude
            let lon = el.longitude ?? el.center?.longitude
            let genus = el.tags?["genus"] ?? el.tags?["species"]?.split(separator: " ").first.map(String.init)
            let leafType = el.tags?["leaf_type"]
            let natural = el.tags?["natural"]
            let landuse = el.tags?["landuse"]
            let isWood = natural == "wood" || landuse == "forest"
            let isMeadow = ["meadow", "grass", "pasture"].contains(landuse ?? "") || ["grassland", "heath"].contains(natural ?? "")
            let isUrban = ["residential", "commercial", "industrial", "retail", "construction"].contains(landuse ?? "") || el.tags?["building"] != nil

            return OsmHabitatElement(
                lat: lat.map { KotlinDouble(double: $0) },
                lon: lon.map { KotlinDouble(double: $0) },
                isWoodOrForest: isWood,
                isMeadowOrGrass: isMeadow,
                isUrbanOrBuilt: isUrban,
                genus: genus,
                leafType: leafType
            )
        }

        return MycoAlgorithms.shared.extractHabitatEvidence(
            elements: osmElements,
            targetLat: coordinate.latitude,
            targetLon: coordinate.longitude,
            searchRadiusMeters: Int32(radiusMeters)
        )
    }

    /// Queries Overpass for forest and specific habitat elements, extracts shared evidence,
    /// and evaluates the final habitat factor via MycoAlgorithms.shared.evaluateHabitat.
    /// Supports 0.95 score branch for saprotrophic open habitats in lockstep with shared core.
    func habitat(
        around coordinate: CLLocationCoordinate2D,
        radiusMeters: Int = Self.defaultHabitatRadiusMeters,
        preferredCanopyTypes: [String],
        ecologicalCategory: HabitatEcologicalCategory = .treeAssociated
    ) async throws -> HabitatSnapshot {
        let radius = min(max(radiusMeters, 1), 50_000)
        let forest = try await query(Self.forestQuery(around: coordinate, radiusMeters: radius))
        let specificHabitat = try await query(
            Self.specificHabitatQuery(
                around: coordinate,
                radiusMeters: radius,
                preferredCanopyTypes: preferredCanopyTypes,
                ecologicalCategory: ecologicalCategory
            )
        )

        let combinedElements = forest.elements + specificHabitat.elements
        let evidence = extractHabitatEvidence(from: combinedElements, around: coordinate, radiusMeters: radius)

        let species: MushroomSpecies
        if ecologicalCategory == .saprotrophic {
            species = SpeciesCatalog.shared.all.first { $0.category == .saprotrophic }
                ?? SpeciesCatalog.shared.byId(id: "macrolepiota_procera")
        } else {
            species = SpeciesCatalog.shared.all.first { $0.category == .ectomycorrhizal }
                ?? SpeciesCatalog.shared.byId(id: "boletus_edulis")
        }

        let evaluation = MycoAlgorithms.shared.evaluateHabitat(
            evidence: evidence,
            species: species,
            spunEcmRichness: nil
        )

        let detected: Set<String>
        if ecologicalCategory == .saprotrophic {
            detected = specificHabitat.elements.isEmpty ? [] : ["saprotrophic_habitat"]
        } else {
            detected = Set(specificHabitat.elements.compactMap { $0.tags?["genus"]?.lowercased() })
        }

        // Shared core produces 0.95 for open habitats with meadowFraction >= 0.25 (saprotrophic scoring contract)
        let score = evaluation.baseScore
        let cleanText = evaluation.baseText.replacingOccurrences(of: "Habitat: ", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
        let bonus = evaluation.bonusText
        let description: String
        if !bonus.isEmpty && !bonus.hasPrefix("Nessuna essenza") {
            let cleanBonus = bonus.replacingOccurrences(of: "Bonus: ", with: "").replacingOccurrences(of: "Bonus SPUN: ", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
            description = "\(cleanText) • \(cleanBonus)"
        } else {
            description = cleanText
        }
        let canopyCover = evidence.forestCoverFraction
        let proximityIndex = evidence.forestCoverFraction

        return HabitatSnapshot(
            score: score,
            description: description,
            canopyTypes: detected.isEmpty ? Array(evidence.confirmedHostGenera).sorted() : detected.sorted(),
            canopyCover: canopyCover,
            forestProximityIndex: proximityIndex
        )
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
