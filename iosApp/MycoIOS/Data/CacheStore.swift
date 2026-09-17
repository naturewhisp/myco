import Foundation
import SwiftData

@Model
final class CacheEntry {
    @Attribute(.unique) var key: String
    var payload: Data
    var createdAt: Date
    var latitude: Double?
    var longitude: Double?
    var ttl: TimeInterval?

    init(
        key: String,
        payload: Data,
        createdAt: Date,
        latitude: Double?,
        longitude: Double?,
        ttl: TimeInterval?
    ) {
        self.key = key
        self.payload = payload
        self.createdAt = createdAt
        self.latitude = latitude
        self.longitude = longitude
        self.ttl = ttl
    }
}

struct CachedPayload: Sendable, Equatable {
    let key: String
    let payload: Data
    let createdAt: Date
    let latitude: Double?
    let longitude: Double?
    let ttl: TimeInterval?

    func isExpired(at date: Date = .now) -> Bool {
        guard let ttl else { return false }
        return date.timeIntervalSince(createdAt) >= ttl
    }
}

/// A persistent response cache. It owns only `CacheEntry` records and never accesses UserDefaults.
@MainActor
final class CacheStore {
    private let modelContext: ModelContext

    init(modelContainer: ModelContainer) {
        modelContext = ModelContext(modelContainer)
    }

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
    }

    func put(
        key: String,
        payload: Data,
        latitude: Double? = nil,
        longitude: Double? = nil,
        ttl: TimeInterval? = nil,
        createdAt: Date = .now
    ) async throws {
        var descriptor = FetchDescriptor<CacheEntry>(predicate: #Predicate { $0.key == key })
        descriptor.fetchLimit = 1

        if let existing = try modelContext.fetch(descriptor).first {
            existing.payload = payload
            existing.createdAt = createdAt
            existing.latitude = latitude
            existing.longitude = longitude
            existing.ttl = ttl
        } else {
            modelContext.insert(
                CacheEntry(
                    key: key,
                    payload: payload,
                    createdAt: createdAt,
                    latitude: latitude,
                    longitude: longitude,
                    ttl: ttl
                )
            )
        }
        try modelContext.save()
    }

    /// Returns a fresh value by default. Set `expiredFallback` to retain a stale cached value when offline.
    func get(
        key: String,
        expiredFallback: Bool = false,
        now: Date = .now
    ) async throws -> CachedPayload? {
        var descriptor = FetchDescriptor<CacheEntry>(predicate: #Predicate { $0.key == key })
        descriptor.fetchLimit = 1
        guard let entry = try modelContext.fetch(descriptor).first else { return nil }

        let value = CachedPayload(
            key: entry.key,
            payload: entry.payload,
            createdAt: entry.createdAt,
            latitude: entry.latitude,
            longitude: entry.longitude,
            ttl: entry.ttl
        )
        return expiredFallback || !value.isExpired(at: now) ? value : nil
    }

    /// Deletes only SwiftData cache records; user preferences remain owned by `PreferencesStore`.
    func clearCache() async throws {
        try modelContext.delete(model: CacheEntry.self)
        try modelContext.save()
    }
}
