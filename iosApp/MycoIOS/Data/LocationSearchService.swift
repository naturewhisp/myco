import CoreLocation
import Foundation
@preconcurrency import MapKit

struct LocationSearchResult: Identifiable, Sendable {
    let id: String
    let name: String
    let detail: String?
    let coordinate: CLLocationCoordinate2D
}

@MainActor
protocol LocationSearching {
    func search(query: String) async throws -> [LocationSearchResult]
}

/// Native interactive search for iOS. Nominatim remains available only for explicit reverse geocoding.
@MainActor
struct MKLocalSearchService: LocationSearching {
    func search(query: String) async throws -> [LocationSearchResult] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }

        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = trimmed
        request.resultTypes = [.address, .pointOfInterest]
        let response = try await MKLocalSearch(request: request).start()

        return response.mapItems.prefix(12).map { item in
            let coordinate = item.placemark.coordinate
            let name = item.name ?? trimmed
            return LocationSearchResult(
                id: "\(coordinate.latitude),\(coordinate.longitude),\(name)",
                name: name,
                detail: nil,
                coordinate: coordinate
            )
        }
    }
}
