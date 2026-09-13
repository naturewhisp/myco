import MycoCore
import SwiftUI

struct MycoRootView: View {
    @AppStorage("themePreference") private var themePreference = ThemePreference.system.rawValue
    @AppStorage("hasAcknowledgedSafetyDisclaimer") private var hasAcknowledgedSafetyDisclaimer = false
    @StateObject private var viewModel = MycoViewModel()
    @StateObject private var locationService = CoreLocationService()

    private var preference: ThemePreference { ThemePreference(rawValue: themePreference) ?? .system }
    private var colors: HerbariumColors { preference == .nocturne ? .nocturne : .parchment }

    var body: some View {
        ZStack {
            TabView {
                RegistryView(viewModel: viewModel, locationService: locationService)
                    .tabItem { Label("Registro", systemImage: "book.closed") }
                ForecastView(viewModel: viewModel)
                    .tabItem { Label("Previsioni", systemImage: "cloud.sun") }
                MapView(viewModel: viewModel, locationService: locationService)
                    .tabItem { Label("Mappa", systemImage: "map") }
            }
            .tint(colors.forest)
            .disabled(!hasAcknowledgedSafetyDisclaimer)
            .accessibilityHidden(!hasAcknowledgedSafetyDisclaimer)

            if !hasAcknowledgedSafetyDisclaimer {
                SafetyDisclaimerView { hasAcknowledgedSafetyDisclaimer = true }
                    .transition(.opacity)
            }
        }
        .environment(\.herbariumColors, colors)
        .preferredColorScheme(preference.colorScheme)
    }
}

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("themePreference") private var themePreference = ThemePreference.system.rawValue
    @AppStorage("hasAcknowledgedSafetyDisclaimer") private var hasAcknowledgedSafetyDisclaimer = false
    @Environment(\.herbariumColors) private var colors

    var body: some View {
        NavigationStack {
            Form {
                Section("Aspetto") {
                    Picker("Tema", selection: $themePreference) {
                        ForEach(ThemePreference.allCases) { preference in
                            Text(preference.title).tag(preference.rawValue)
                        }
                    }
                    .pickerStyle(.inline)
                }
                Section("Sicurezza") {
                    Button(role: .destructive) {
                        hasAcknowledgedSafetyDisclaimer = false
                        dismiss()
                    } label: {
                        Label("Mostra nuovamente l'avvertenza", systemImage: "exclamationmark.triangle")
                    }
                    .frame(minHeight: 44)
                }
                Section("Informazioni") {
                    LabeledContent("Core condiviso", value: "KMP \(MycoCoreInfo().version())")
                }
            }
            .navigationTitle("Impostazioni")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Fine") { dismiss() }.frame(minWidth: 44, minHeight: 44)
                }
            }
            .tint(colors.forest)
        }
    }
}

private struct SafetyDisclaimerView: View {
    @Environment(\.herbariumColors) private var colors
    let acknowledge: () -> Void

    var body: some View {
        ZStack {
            colors.background.ignoresSafeArea()
            VStack(spacing: 24) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(colors.warning)
                    .accessibilityHidden(true)
                Text("Avvertenza di sicurezza").font(.title.bold()).multilineTextAlignment(.center)
                Text("Myco offre indicazioni ambientali e non identifica funghi né conferma la loro commestibilità. Non consumare mai un fungo senza una verifica esperta indipendente.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(colors.inkSoft)
                Button(action: acknowledge) {
                    Text("Ho compreso").frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(colors.forest)
                .controlSize(.large)
                .accessibilityHint("Conferma di aver letto l'avvertenza e apre l'app")
            }
            .padding(28)
            .frame(maxWidth: 520)
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
    }
}
