import CoreLocation
import MycoCore
import SwiftUI
import UIKit

struct RegistryView: View {
    @Environment(\.herbariumColors) private var colors
    @Environment(\.dismissSearch) private var dismissSearch
    @ObservedObject var viewModel: MycoViewModel
    @ObservedObject var locationService: CoreLocationService
    @State private var query = ""
    @State private var showingSettings = false
    @State private var showingSpecies = false
    @State private var favoriteToRename: SavedPlaceValue?
    @State private var favoriteName = ""

    var body: some View {
        NavigationStack {
            List {
                locationSection
                speciesSection
                savedPlacesSection
                searchResultsSection
                analysisSection
                errorSection
            }
            .searchable(text: $query, prompt: "Cerca città, borgo o montagna")
            .onSubmit(of: .search) {
                dismissSearch()
                viewModel.submitSearch(query: query)
            }
            .scrollContentBackground(.hidden)
            .background(colors.background)
            .navigationTitle("Registro")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Impostazioni", systemImage: "gearshape") { showingSettings = true }
                        .accessibilityLabel("Impostazioni")
                }
            }
            .sheet(isPresented: $showingSettings) {
                SettingsView(viewModel: viewModel).presentationDetents([.medium, .large])
            }
            .sheet(isPresented: $showingSpecies) {
                SpeciesPickerView(viewModel: viewModel)
            }
            .alert("Rinomina preferito", isPresented: Binding(
                get: { favoriteToRename != nil },
                set: { if !$0 { favoriteToRename = nil } }
            )) {
                TextField("Nome", text: $favoriteName)
                Button("Annulla", role: .cancel) { favoriteToRename = nil }
                Button("Salva") {
                    if let favoriteToRename { viewModel.renameFavorite(id: favoriteToRename.id, name: favoriteName) }
                    favoriteToRename = nil
                }
            }
        }
    }

    @ViewBuilder private var locationSection: some View {
        Section {
            if let location = locationService.location {
                Button {
                    viewModel.useCurrentLocation(latitude: location.latitude, longitude: location.longitude)
                } label: {
                    Label("Usa posizione attuale", systemImage: "location.fill")
                }
                .frame(minHeight: 44)
            } else {
                Button {
                    locationService.requestCurrentLocation()
                } label: {
                    Label("Rileva posizione una volta", systemImage: "location")
                }
                .frame(minHeight: 44)
            }
            if let selected = viewModel.selectedLocation {
                LabeledContent {
                    Text(selected.name)
                        .multilineTextAlignment(.trailing)
                        .lineLimit(3)
                } label: {
                    Label("Selezionata", systemImage: "mappin.and.ellipse")
                }
                if let elevation = viewModel.elevation {
                    LabeledContent("Altitudine", value: "\(elevation.formatted(.number.precision(.fractionLength(0)))) m")
                }
                Button(viewModel.isFavorite(selected) ? "Rimuovi dai preferiti" : "Aggiungi ai preferiti", systemImage: viewModel.isFavorite(selected) ? "star.slash" : "star") {
                    viewModel.toggleFavorite()
                }
                .frame(minHeight: 44)
            }
            if locationService.locationServicesAvailable == false {
                locationPermissionNotice(
                    title: "Servizi di localizzazione disattivati",
                    message: "Attivali nelle Impostazioni per usare la posizione attuale."
                )
            } else {
                switch locationService.authorizationStatus {
                case .denied:
                    locationPermissionNotice(
                        title: "Accesso alla posizione negato",
                        message: "Consenti l'accesso alla posizione nelle Impostazioni per usare il GPS."
                    )
                case .restricted:
                    Label("Accesso alla posizione limitato dal dispositivo", systemImage: "lock.slash")
                        .font(.footnote)
                        .foregroundStyle(colors.warning)
                default:
                    if locationService.accuracyAuthorization == .reducedAccuracy {
                        Label("Posizione approssimativa attiva", systemImage: "location.slash")
                            .font(.footnote)
                            .foregroundStyle(colors.warning)
                    }
                }
            }
            if viewModel.isOfflineFallback {
                Label("Modalità offline: risultati da cache scaduta", systemImage: "wifi.slash")
                    .foregroundStyle(colors.warning)
            }
        } header: {
            Text("Località")
        } footer: {
            Text("La posizione del Registro usa una richiesta one-shot; il tracking continuo è limitato alla Mappa.")
        }
    }

    private func locationPermissionNotice(title: String, message: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(title, systemImage: "location.slash")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(colors.warning)
            Text(message)
                .font(.footnote)
                .foregroundStyle(colors.inkSoft)
            Button("Apri Impostazioni", systemImage: "gear") {
                guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                UIApplication.shared.open(url)
            }
            .frame(minHeight: 44)
        }
    }

    @ViewBuilder private var savedPlacesSection: some View {
        if !viewModel.favorites.isEmpty {
            Section("Preferiti") {
                ForEach(viewModel.favorites) { place in
                    Button(place.name, systemImage: "star.fill") { viewModel.selectSavedPlace(place) }
                        .frame(minHeight: 44)
                        .contextMenu {
                            Button("Rinomina", systemImage: "pencil") {
                                favoriteName = place.name
                                favoriteToRename = place
                            }
                            Button("Rimuovi", systemImage: "trash", role: .destructive) { viewModel.removeFavorite(id: place.id) }
                        }
                        .swipeActions { Button("Rimuovi", role: .destructive) { viewModel.removeFavorite(id: place.id) } }
                }
            }
        }
        if !viewModel.recentLocations.isEmpty {
            Section("Recenti") {
                ForEach(viewModel.recentLocations.prefix(5)) { place in
                    Button(place.name, systemImage: "clock") { viewModel.selectSavedPlace(place) }
                        .frame(minHeight: 44)
                }
            }
        }
    }

    private var speciesSection: some View {
        Section("Specie bersaglio") {
            Button { showingSpecies = true } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(viewModel.selectedSpecies.vernacularName)
                            .foregroundStyle(colors.ink)
                            .lineLimit(3)
                        Text(viewModel.selectedSpecies.binomialName).font(.caption).italic().foregroundStyle(colors.inkSoft)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").foregroundStyle(colors.inkSoft)
                }
                .frame(minHeight: 44)
            }
            .accessibilityHint("Apre il catalogo delle specie")
            if !viewModel.selectedSpecies.toxicLookAlikes.isEmpty {
                Label("Possibili sosia tossici: \(viewModel.selectedSpecies.toxicLookAlikes.joined(separator: ", "))", systemImage: "exclamationmark.triangle.fill")
                    .font(.footnote)
                    .foregroundStyle(colors.warning)
            }
        }
    }

    @ViewBuilder private var searchResultsSection: some View {
        if viewModel.isSearching {
            Section { HStack { ProgressView(); Text("Ricerca in corso") } }
        } else if !viewModel.searchResults.isEmpty {
            Section("Risultati") {
                ForEach(viewModel.searchResults) { place in
                    Button {
                        query = place.name
                        viewModel.select(place: place)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(place.name).foregroundStyle(colors.ink)
                            Text(place.detail ?? "Località").font(.caption).foregroundStyle(colors.inkSoft)
                        }
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    }
                    .accessibilityHint("Seleziona la località e avvia l'analisi")
                }
            }
        }
    }

    @ViewBuilder private var analysisSection: some View {
        if viewModel.isLoadingEnvironment {
            Section { HStack { ProgressView(); Text("Analisi ambientale e scientifica") } }
        } else if let analysis = viewModel.analysis {
            Section("Probabilità di fruttificazione") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("\(analysis.probability)%").font(.system(.largeTitle, design: .rounded, weight: .bold))
                    Text(analysis.tier.descriptiveLabel).font(.headline)
                    ProgressView(value: Double(analysis.probability), total: 100).tint(tierColor(index: Int(analysis.tier.tierIndex)))
                }
                .padding(.vertical, 8)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Probabilità \(analysis.probability) percento, livello \(analysis.tier.shortLabel)")
            }
            Section("Fattori ecologici") {
                ForEach(Array(analysis.factors.enumerated()), id: \.offset) { _, factor in
                    VStack(alignment: .leading, spacing: 5) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(factor.label).frame(maxWidth: .infinity, alignment: .leading)
                            Text(factor.formattedValue).fontWeight(.semibold).multilineTextAlignment(.trailing)
                        }
                        Text("\(factor.level.name.capitalized) · \(factor.detail)")
                            .font(.caption)
                            .foregroundStyle(colors.inkSoft)
                    }
                    .padding(.vertical, 4)
                    .accessibilityElement(children: .combine)
                }
            }
            if !analysis.missingSources.isEmpty {
                Section("Copertura dati") {
                    Label("Analisi parziale: \(analysis.missingSources.joined(separator: ", "))", systemImage: "exclamationmark.circle")
                        .foregroundStyle(colors.warning)
                }
            }
            Section("Nota dal campo") {
                Text(viewModel.fieldNote.isEmpty ? analysis.deterministicFieldNote : viewModel.fieldNote)
            }
        } else if viewModel.selectedLocation == nil {
            Section {
                ContentUnavailableView {
                    Label("Scegli una località", systemImage: "leaf")
                } description: {
                    Text("Cerca su invio, usa il GPS oppure tocca la Mappa per avviare l'analisi Myco.")
                }
            }
        }
    }

    @ViewBuilder private var errorSection: some View {
        if let error = viewModel.errorMessage {
            Section {
                Label(error, systemImage: "exclamationmark.triangle").foregroundStyle(colors.warning)
                Button("Riprova") { viewModel.retry() }.frame(minHeight: 44)
            }
        }
    }

    private func tierColor(index: Int) -> Color {
        switch index {
        case 0: colors.inkSoft
        case 1: colors.lichen
        case 2: .orange
        case 3: .red.opacity(0.75)
        default: colors.warning
        }
    }
}
