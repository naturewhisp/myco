import Foundation

@MainActor
protocol PreferencesStoring: AnyObject {
    var hasAcknowledgedSafetyDisclaimer: Bool { get set }
    var themePreference: String { get set }
    func removeEphemeralValues(_ keys: [String])
}

@MainActor
final class PreferencesStore: PreferencesStoring {
    private enum Key {
        static let safetyDisclaimer = "hasAcknowledgedSafetyDisclaimer"
        static let themePreference = "themePreference"
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var hasAcknowledgedSafetyDisclaimer: Bool {
        get { defaults.bool(forKey: Key.safetyDisclaimer) }
        set { defaults.set(newValue, forKey: Key.safetyDisclaimer) }
    }

    var themePreference: String {
        get { defaults.string(forKey: Key.themePreference) ?? "system" }
        set { defaults.set(newValue, forKey: Key.themePreference) }
    }

    func value<T>(forKey key: String) -> T? {
        defaults.object(forKey: key) as? T
    }

    func set<T>(_ value: T?, forKey key: String) {
        defaults.set(value, forKey: key)
    }

    /// Removes only values passed by the caller; user safety and appearance preferences are never cleared here.
    func removeEphemeralValues(_ keys: [String]) {
        for key in keys where key != Key.safetyDisclaimer && key != Key.themePreference {
            defaults.removeObject(forKey: key)
        }
    }
}
