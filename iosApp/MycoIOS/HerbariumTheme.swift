import SwiftUI

enum ThemePreference: String, CaseIterable, Identifiable {
    case system
    case parchment
    case nocturne

    var id: String { rawValue }

    var title: String {
        switch self {
        case .system: "Sistema"
        case .parchment: "Pergamena"
        case .nocturne: "Notturno"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .parchment: .light
        case .nocturne: .dark
        }
    }
}

struct HerbariumColors {
    let background: Color
    let surface: Color
    let surfaceRaised: Color
    let ink: Color
    let inkSoft: Color
    let forest: Color
    let lichen: Color
    let rule: Color
    let warning: Color

    static let parchment = Self(
        background: Color(red: 0.957, green: 0.929, blue: 0.878),
        surface: Color(red: 1.0, green: 0.973, blue: 0.961),
        surfaceRaised: Color(red: 0.910, green: 0.871, blue: 0.796),
        ink: Color(red: 0.231, green: 0.184, blue: 0.149),
        inkSoft: Color(red: 0.431, green: 0.357, blue: 0.286),
        forest: Color(red: 0.247, green: 0.318, blue: 0.212),
        lichen: Color(red: 0.435, green: 0.514, blue: 0.333),
        rule: Color(red: 0.702, green: 0.631, blue: 0.533),
        warning: Color(red: 0.620, green: 0.149, blue: 0.173)
    )

    static let nocturne = Self(
        background: Color(red: 0.133, green: 0.118, blue: 0.094),
        surface: Color(red: 0.180, green: 0.157, blue: 0.125),
        surfaceRaised: Color(red: 0.220, green: 0.192, blue: 0.153),
        ink: Color(red: 0.929, green: 0.890, blue: 0.824),
        inkSoft: Color(red: 0.714, green: 0.659, blue: 0.569),
        forest: Color(red: 0.576, green: 0.659, blue: 0.455),
        lichen: Color(red: 0.576, green: 0.659, blue: 0.455),
        rule: Color(red: 0.361, green: 0.322, blue: 0.271),
        warning: Color(red: 0.973, green: 0.698, blue: 0.165)
    )
}

private struct HerbariumColorsKey: EnvironmentKey {
    static let defaultValue = HerbariumColors.parchment
}

extension EnvironmentValues {
    var herbariumColors: HerbariumColors {
        get { self[HerbariumColorsKey.self] }
        set { self[HerbariumColorsKey.self] = newValue }
    }
}
