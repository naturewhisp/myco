import Foundation
import XCTest
@testable import MycoIOS

@MainActor
final class PreferencesStoreTests: XCTestCase {
    func testRemovingEphemeralValuesPreservesUserPreferences() {
        let suiteName = "MycoIOSTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = PreferencesStore(defaults: defaults)
        store.hasAcknowledgedSafetyDisclaimer = true
        store.themePreference = "dark"
        store.set(true, forKey: PreferenceKey.aiEnabled)
        store.set("cached-response", forKey: "forecastCache")

        store.removeEphemeralValues(["forecastCache", PreferenceKey.safetyDisclaimer, PreferenceKey.theme, PreferenceKey.aiEnabled])

        XCTAssertTrue(store.hasAcknowledgedSafetyDisclaimer)
        XCTAssertEqual(store.themePreference, "dark")
        XCTAssertEqual(store.value(forKey: PreferenceKey.aiEnabled) as Bool?, true)
        XCTAssertNil(store.value(forKey: "forecastCache") as String?)
    }
}
