import Foundation
import SwiftData
import XCTest
@testable import MycoIOS

@MainActor
final class CacheStoreTests: XCTestCase {
    func testPutAndGetPreservesPayloadAndLocationMetadata() async throws {
        let store = try makeStore()
        let payload = Data("forecast".utf8)

        try await store.put(
            key: "forecast:rome",
            payload: payload,
            latitude: 41.9028,
            longitude: 12.4964,
            ttl: 900
        )

        let cached = try await store.get(key: "forecast:rome")
        XCTAssertEqual(cached?.payload, payload)
        XCTAssertEqual(cached?.latitude, 41.9028)
        XCTAssertEqual(cached?.longitude, 12.4964)
        XCTAssertEqual(cached?.ttl, 900)
    }

    func testExpiredValueIsAvailableOnlyWhenFallbackIsRequested() async throws {
        let store = try makeStore()
        let now = Date(timeIntervalSince1970: 1_000)
        try await store.put(
            key: "weather:stale",
            payload: Data("stale".utf8),
            ttl: 60,
            createdAt: now.addingTimeInterval(-61)
        )

        let fresh = try await store.get(key: "weather:stale", now: now)
        let stale = try await store.get(key: "weather:stale", expiredFallback: true, now: now)
        XCTAssertNil(fresh)
        XCTAssertEqual(stale?.payload, Data("stale".utf8))
    }

    func testClearCacheRemovesAllEntries() async throws {
        let store = try makeStore()
        try await store.put(key: "first", payload: Data([1]))
        try await store.put(key: "second", payload: Data([2]))

        try await store.clearCache()

        let first = try await store.get(key: "first")
        let second = try await store.get(key: "second")
        XCTAssertNil(first)
        XCTAssertNil(second)
    }

    private func makeStore() throws -> CacheStore {
        let schema = Schema([CacheEntry.self])
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: configuration)
        return CacheStore(modelContainer: container)
    }
}
