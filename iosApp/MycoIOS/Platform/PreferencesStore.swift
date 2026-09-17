import Foundation

enum PreferenceKey {
    static let safetyDisclaimer = "hasAcknowledgedSafetyDisclaimer"
    static let theme = "themePreference"
    static let aiEnabled = "foundationModelsEnabled"
}

@MainActor
protocol PreferencesStoring: AnyObject {
    var hasAcknowledgedSafetyDisclaimer: Bool { get set }
    var themePreference: String { get set }
    func removeEphemeralValues(_ keys: [String])
}

@MainActor
final class PreferencesStore: PreferencesStoring {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var hasAcknowledgedSafetyDisclaimer: Bool {
        get { defaults.bool(forKey: PreferenceKey.safetyDisclaimer) }
        set { defaults.set(newValue, forKey: PreferenceKey.safetyDisclaimer) }
    }

    var themePreference: String {
        get { defaults.string(forKey: PreferenceKey.theme) ?? "system" }
        set { defaults.set(newValue, forKey: PreferenceKey.theme) }
    }

    func value<T>(forKey key: String) -> T? {
        defaults.object(forKey: key) as? T
    }

    func set<T>(_ value: T?, forKey key: String) {
        defaults.set(value, forKey: key)
    }

    /// Removes only values passed by the caller; user safety and appearance preferences are never cleared here.
    func removeEphemeralValues(_ keys: [String]) {
        for key in keys where key != PreferenceKey.safetyDisclaimer && key != PreferenceKey.theme && key != PreferenceKey.aiEnabled {
            defaults.removeObject(forKey: key)
        }
    }
}
