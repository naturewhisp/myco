import SwiftData
import XCTest
@testable import MycoIOS

final class PersistenceBootstrapTests: XCTestCase {
    func testOnlyConfirmedSwiftDataFailuresAreClassifiedForRecovery() {
        XCTAssertTrue(MycoPersistenceBootstrap.isConfirmedStoreOrMigrationFailure(SwiftDataError.loadIssueModelContainer))
        XCTAssertTrue(MycoPersistenceBootstrap.isConfirmedStoreOrMigrationFailure(SwiftDataError.backwardMigration))
        XCTAssertTrue(MycoPersistenceBootstrap.isConfirmedStoreOrMigrationFailure(SwiftDataError.unknownSchema))
        XCTAssertTrue(MycoPersistenceBootstrap.isConfirmedStoreOrMigrationFailure(SwiftDataError.modelValidationFailure))
        XCTAssertFalse(MycoPersistenceBootstrap.isConfirmedStoreOrMigrationFailure(TestPersistenceError.unrelated))
    }

    func testArbitraryOpenFailureUsesInMemoryFallbackWithoutDeletingData() throws {
        let schema = Schema([CacheEntry.self, SavedPlace.self])
        let result = MycoPersistenceBootstrap.makeContainer(
            openPersistent: { throw TestPersistenceError.unrelated },
            openInMemory: {
                let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
                return try ModelContainer(for: schema, configurations: configuration)
            }
        )

        XCTAssertNotNil(result.container)
        XCTAssertTrue(result.usedInMemoryFallback)
        XCTAssertNotNil(result.errorDescription)
    }

    func testFailureOfBothStoresReturnsRecoverableUnavailableState() {
        let result = MycoPersistenceBootstrap.makeContainer(
            openPersistent: { throw TestPersistenceError.unrelated },
            openInMemory: { throw TestPersistenceError.unrelated }
        )

        XCTAssertNil(result.container)
        XCTAssertTrue(result.usedInMemoryFallback)
        XCTAssertNotNil(result.errorDescription)
    }

    private enum TestPersistenceError: Error {
        case unrelated
    }
}
