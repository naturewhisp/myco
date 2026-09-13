import Foundation
import XCTest
@testable import MycoIOS

@MainActor
final class PreferencesStoreTests: XCTestCase {
    func testRemovingEphemeralValuesPreservesSafetyAndThemePreferences() {
        let suiteName = "MycoIOSTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = PreferencesStore(defaults: defaults)
        store.hasAcknowledgedSafetyDisclaimer = true
        store.themePreference = "dark"
        store.set("cached-response", forKey: "forecastCache")

        store.removeEphemeralValues(["forecastCache", "hasAcknowledgedSafetyDisclaimer", "themePreference"])

        XCTAssertTrue(store.hasAcknowledgedSafetyDisclaimer)
        XCTAssertEqual(store.themePreference, "dark")
        XCTAssertNil(store.value(forKey: "forecastCache") as String?)
    }
}
