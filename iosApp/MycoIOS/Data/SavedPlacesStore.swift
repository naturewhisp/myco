import Foundation
import SwiftData

@Model
final class SavedPlace {
    @Attribute(.unique) var id: UUID
    var kind: String
    var name: String
    var latitude: Double
    var longitude: Double
    var updatedAt: Date

    init(id: UUID = UUID(), kind: String, name: String, latitude: Double, longitude: Double, updatedAt: Date = .now) {
        self.id = id
        self.kind = kind
        self.name = name
        self.latitude = latitude
        self.longitude = longitude
        self.updatedAt = updatedAt
    }
}

struct SavedPlaceValue: Identifiable, Sendable {
    let id: UUID
    let name: String
    let latitude: Double
    let longitude: Double
}

@MainActor
final class SavedPlacesStore {
    private let context: ModelContext

    init(modelContainer: ModelContainer) { context = ModelContext(modelContainer) }

    func favorites() throws -> [SavedPlaceValue] { try values(kind: "favorite", limit: nil) }
    func recents() throws -> [SavedPlaceValue] { try values(kind: "recent", limit: 10) }

    func recordRecent(name: String, latitude: Double, longitude: Double) throws {
        let entry = SavedPlace(kind: "recent", name: name, latitude: latitude, longitude: longitude)
        context.insert(entry)
        let all = try entities(kind: "recent")
        for stale in all.dropFirst(10) { context.delete(stale) }
        try context.save()
    }

    func toggleFavorite(name: String, latitude: Double, longitude: Double) throws {
        if let existing = try entities(kind: "favorite").first(where: { abs($0.latitude - latitude) < 0.000_001 && abs($0.longitude - longitude) < 0.000_001 }) {
            context.delete(existing)
        } else {
            context.insert(SavedPlace(kind: "favorite", name: name, latitude: latitude, longitude: longitude))
        }
        try context.save()
    }

    func renameFavorite(id: UUID, name: String) throws {
        guard let value = try entities(kind: "favorite").first(where: { $0.id == id }) else { return }
        value.name = name
        value.updatedAt = .now
        try context.save()
    }

    func removeFavorite(id: UUID) throws {
        guard let value = try entities(kind: "favorite").first(where: { $0.id == id }) else { return }
        context.delete(value)
        try context.save()
    }

    private func values(kind: String, limit: Int?) throws -> [SavedPlaceValue] {
        Array(try entities(kind: kind).prefix(limit ?? .max)).map { SavedPlaceValue(id: $0.id, name: $0.name, latitude: $0.latitude, longitude: $0.longitude) }
    }

    private func entities(kind: String) throws -> [SavedPlace] {
        let descriptor = FetchDescriptor<SavedPlace>(
            predicate: #Predicate { $0.kind == kind },
            sortBy: [SortDescriptor(\SavedPlace.updatedAt, order: .reverse)]
        )
        return try context.fetch(descriptor)
    }
}
