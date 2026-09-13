import SwiftUI
import SwiftData

@main
struct MycoIOSApp: App {
    private static let persistence = MycoPersistenceBootstrap.makeContainer()

    var body: some Scene {
        WindowGroup {
            if let container = Self.persistence.container {
                MycoRootView(
                    cacheStore: CacheStore(modelContainer: container),
                    savedPlacesStore: SavedPlacesStore(modelContainer: container)
                )
                .modelContainer(container)
            } else {
                PersistenceUnavailableView()
            }
        }
    }
}

private struct PersistenceUnavailableView: View {
    var body: some View {
        ContentUnavailableView(
            "Archivio locale non disponibile",
            systemImage: "externaldrive.badge.xmark",
            description: Text("Myco non ha potuto inizializzare l'archivio locale. Riavvia l'app per riprovare.")
        )
    }
}
