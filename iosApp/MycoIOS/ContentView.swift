import MycoCore
import SwiftUI

struct MycoRootView: View {
    @Environment(\.colorScheme) private var systemColorScheme
    @AppStorage(PreferenceKey.theme) private var themePreference = ThemePreference.system.rawValue
    @AppStorage(PreferenceKey.safetyDisclaimer) private var hasAcknowledgedSafetyDisclaimer = false
    @StateObject private var viewModel: MycoViewModel
    @StateObject private var locationService = CoreLocationService()

    init(cacheStore: CacheStore? = nil, savedPlacesStore: SavedPlacesStore? = nil) {
        _viewModel = StateObject(wrappedValue: MycoViewModel(cacheStore: cacheStore, savedPlacesStore: savedPlacesStore))
    }

    private var preference: ThemePreference { ThemePreference(rawValue: themePreference) ?? .system }
    private var colors: HerbariumColors {
        switch preference {
        case .system:
            systemColorScheme == .dark ? .nocturne : .parchment
        case .parchment:
            .parchment
        case .nocturne:
            .nocturne
        }
    }

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
    @AppStorage(PreferenceKey.theme) private var themePreference = ThemePreference.system.rawValue
    @AppStorage(PreferenceKey.safetyDisclaimer) private var hasAcknowledgedSafetyDisclaimer = false
    @AppStorage(PreferenceKey.aiEnabled) private var aiEnabled = false
    @Environment(\.herbariumColors) private var colors
    @ObservedObject var viewModel: MycoViewModel
    @State private var cacheMessage: String?

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
                Section("Offline e cache") {
                    Button("Svuota cache", systemImage: "trash") {
                        Task {
                            do {
                                try await viewModel.clearCache()
                                cacheMessage = "Cache eliminata. Preferenze e dati salvati sono invariati."
                            } catch {
                                cacheMessage = "Impossibile eliminare la cache."
                            }
                        }
                    }
                    .frame(minHeight: 44)
                    if let cacheMessage {
                        Text(cacheMessage).font(.footnote).foregroundStyle(colors.inkSoft)
                    }
                }
                Section("AI locale") {
                    Toggle("Arricchisci nota dal campo", isOn: $aiEnabled)
                    Text("Se Foundation Models non è disponibile, Myco conserva automaticamente la nota deterministica del core.")
                        .font(.footnote).foregroundStyle(colors.inkSoft)
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
        GeometryReader { proxy in
            colors.background.ignoresSafeArea()
            ScrollView {
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
                .frame(maxWidth: 520, minHeight: proxy.size.height)
                .frame(maxWidth: .infinity)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
    }
}
