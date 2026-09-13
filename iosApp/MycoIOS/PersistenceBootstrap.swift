import OSLog
import SwiftData

/// The result of opening Myco's non-critical local persistence.
struct MycoPersistenceBootstrapResult {
    let container: ModelContainer?
    let usedInMemoryFallback: Bool
    let errorDescription: String?
}

enum MycoPersistenceBootstrap {
    private static let logger = Logger(subsystem: "github.naturewhisp.myco.ios", category: "persistence")

    static func makeContainer() -> MycoPersistenceBootstrapResult {
        let schema = Schema([CacheEntry.self, SavedPlace.self])
        return makeContainer(
            openPersistent: { try ModelContainer(for: schema) },
            openInMemory: {
                let memoryConfiguration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
                return try ModelContainer(for: schema, configurations: memoryConfiguration)
            }
        )
    }

    static func makeContainer(
        openPersistent: () throws -> ModelContainer,
        openInMemory: () throws -> ModelContainer
    ) -> MycoPersistenceBootstrapResult {
        do {
            return MycoPersistenceBootstrapResult(
                container: try openPersistent(),
                usedInMemoryFallback: false,
                errorDescription: nil
            )
        } catch {
            logPersistentOpenFailure(error)

            do {
                let memoryContainer = try openInMemory()
                logger.warning("Using an in-memory persistence fallback; cached responses, favorites, and recents will not survive this launch.")
                return MycoPersistenceBootstrapResult(
                    container: memoryContainer,
                    usedInMemoryFallback: true,
                    errorDescription: String(describing: error)
                )
            } catch {
                logger.critical("Unable to create either persistent or in-memory Myco persistence: \(String(describing: error), privacy: .public)")
                return MycoPersistenceBootstrapResult(
                    container: nil,
                    usedInMemoryFallback: true,
                    errorDescription: String(describing: error)
                )
            }
        }
    }

    static func isConfirmedStoreOrMigrationFailure(_ error: Error) -> Bool {
        guard let swiftDataError = error as? SwiftDataError else { return false }
        return swiftDataError == .loadIssueModelContainer
            || swiftDataError == .backwardMigration
            || swiftDataError == .unknownSchema
            || swiftDataError == .modelValidationFailure
    }

    private static func logPersistentOpenFailure(_ error: Error) {
        let description = String(describing: error)
        if isConfirmedStoreOrMigrationFailure(error) {
            #if DEBUG
            logger.warning("[debug] SwiftData reported a confirmed store/schema/migration failure: \(description, privacy: .public). Persistent data is retained; no automatic deletion or rebuild is attempted.")
            #else
            logger.error("[production] SwiftData reported a confirmed store/schema/migration failure: \(description, privacy: .public). Persistent data is retained; no automatic deletion or rebuild is attempted.")
            #endif
        } else {
            #if DEBUG
            logger.warning("[debug] SwiftData persistence open failed with a non-confirmed error: \(description, privacy: .public). Retaining persistent data and using an in-memory fallback.")
            #else
            logger.error("[production] SwiftData persistence open failed with a non-confirmed error: \(description, privacy: .public). Retaining persistent data and using an in-memory fallback.")
            #endif
        }
    }
}
