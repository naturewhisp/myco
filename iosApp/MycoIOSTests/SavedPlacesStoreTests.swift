import SwiftData
import XCTest
@testable import MycoIOS

@MainActor
final class SavedPlacesStoreTests: XCTestCase {
    func testFavoritesCanBeAddedRenamedAndRemoved() throws {
        let store = try makeStore()
        try store.toggleFavorite(name: "Monte Amiata", latitude: 42.89, longitude: 11.63)
        let added = try XCTUnwrap(store.favorites().first)
        XCTAssertEqual(added.name, "Monte Amiata")

        try store.renameFavorite(id: added.id, name: "Amiata")
        XCTAssertEqual(try store.favorites().first?.name, "Amiata")

        try store.removeFavorite(id: added.id)
        XCTAssertTrue(try store.favorites().isEmpty)
    }

    func testRecentsKeepOnlyEightNewestPlaces() throws {
        let store = try makeStore()
        for index in 0 ..< 10 {
            try store.recordRecent(name: "Luogo \(index)", latitude: Double(index), longitude: 12)
        }

        let recents = try store.recents()
        XCTAssertEqual(recents.count, 8)
        XCTAssertEqual(recents.first?.name, "Luogo 9")
        XCTAssertFalse(recents.contains(where: { $0.name == "Luogo 0" }))
    }

    func testRecentsUseThreeDecimalCoordinateIdentity() throws {
        let store = try makeStore()
        try store.recordRecent(name: "Original", latitude: 42.8900, longitude: 11.6300)
        try store.recordRecent(name: "Nearly same", latitude: 42.8904, longitude: 11.6304)
        XCTAssertEqual(try store.recents().count, 1)
        XCTAssertEqual(try store.recents().first?.name, "Nearly same")

        try store.recordRecent(name: "Distinct", latitude: 42.8911, longitude: 11.6311)
        XCTAssertEqual(try store.recents().count, 2)
    }

    func testFavoriteToggleUsesThreeDecimalCoordinateIdentity() throws {
        let store = try makeStore()
        try store.toggleFavorite(name: "Monte Amiata", latitude: 42.8900, longitude: 11.6300)
        try store.toggleFavorite(name: "Nearly same", latitude: 42.8904, longitude: 11.6304)
        XCTAssertTrue(try store.favorites().isEmpty)

        try store.toggleFavorite(name: "Monte Amiata", latitude: 42.8900, longitude: 11.6300)
        try store.toggleFavorite(name: "Distinct", latitude: 42.8911, longitude: 11.6311)
        XCTAssertEqual(try store.favorites().count, 2)
    }

    func testRecentsMigrationKeepsNewestDuplicateOnly() throws {
        let schema = Schema([SavedPlace.self])
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: configuration)
        let context = ModelContext(container)
        let old = SavedPlace(kind: "recent", name: "Old", latitude: 42.89, longitude: 11.63, updatedAt: Date(timeIntervalSince1970: 1))
        let newest = SavedPlace(kind: "recent", name: "Newest", latitude: 42.8904, longitude: 11.6304, updatedAt: Date(timeIntervalSince1970: 2))
        let favorite = SavedPlace(kind: "favorite", name: "Favorite", latitude: 42.89, longitude: 11.63)
        context.insert(old)
        context.insert(newest)
        context.insert(favorite)
        try context.save()

        let store = SavedPlacesStore(modelContainer: container)
        let recents = try store.recents()
        XCTAssertEqual(recents.count, 1)
        XCTAssertEqual(recents.first?.name, "Newest")
        XCTAssertEqual(try store.favorites().count, 1)
    }

    private func makeStore() throws -> SavedPlacesStore {
        let schema = Schema([SavedPlace.self])
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: configuration)
        return SavedPlacesStore(modelContainer: container)
    }
}
