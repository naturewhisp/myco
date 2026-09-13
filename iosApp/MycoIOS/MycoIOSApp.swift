import SwiftUI
import SwiftData

@main
struct MycoIOSApp: App {
    private static let modelContainer: ModelContainer = {
        do {
            return try ModelContainer(for: CacheEntry.self, SavedPlace.self)
        } catch {
            fatalError("Unable to create the Myco data container: \(error)")
        }
    }()

    var body: some Scene {
        WindowGroup {
            MycoRootView(
                cacheStore: CacheStore(modelContainer: Self.modelContainer),
                savedPlacesStore: SavedPlacesStore(modelContainer: Self.modelContainer)
            )
        }
        .modelContainer(Self.modelContainer)
    }
}
