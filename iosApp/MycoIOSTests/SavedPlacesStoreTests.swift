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

    func testRecentsKeepOnlyTenNewestPlaces() throws {
        let store = try makeStore()
        for index in 0 ..< 12 {
            try store.recordRecent(name: "Luogo \(index)", latitude: Double(index), longitude: 12)
        }

        let recents = try store.recents()
        XCTAssertEqual(recents.count, 10)
        XCTAssertEqual(recents.first?.name, "Luogo 11")
        XCTAssertFalse(recents.contains(where: { $0.name == "Luogo 0" }))
    }

    private func makeStore() throws -> SavedPlacesStore {
        let schema = Schema([SavedPlace.self])
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: configuration)
        return SavedPlacesStore(modelContainer: container)
    }
}
