import CoreLocation
import SwiftUI

struct RegistryView: View {
    @Environment(\.herbariumColors) private var colors
    @ObservedObject var viewModel: MycoViewModel
    @ObservedObject var locationService: CoreLocationService
    @State private var query = ""
    @State private var showingSettings = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Cerca una località per consultare le condizioni ambientali. Le probabilità di crescita saranno disponibili quando il core condiviso sarà collegato.")
                        .font(.subheadline)
                        .foregroundStyle(colors.inkSoft)
                }

                if let location = locationService.location {
                    Section {
                        Button {
                            viewModel.useCurrentLocation(latitude: location.latitude, longitude: location.longitude)
                        } label: {
                            Label("Usa posizione attuale", systemImage: "location.fill")
                        }
                        .frame(minHeight: 44)
                    }
                } else {
                    Section {
                        Button {
                            locationService.start()
                        } label: {
                            Label("Attiva posizione", systemImage: "location")
                        }
                        .frame(minHeight: 44)
                    } footer: {
                        Text("La posizione viene usata solo per scegliere una località.")
                    }
                }

                if viewModel.isSearching {
                    Section {
                        HStack {
                            ProgressView()
                            Text("Ricerca in corso")
                        }
                    }
                } else if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                          viewModel.searchResults.isEmpty,
                          viewModel.errorMessage == nil {
                    ContentUnavailableView.search(text: query)
                } else if !viewModel.searchResults.isEmpty {
                    Section("Risultati") {
                        ForEach(viewModel.searchResults) { place in
                            Button {
                                query = place.displayName
                                viewModel.select(place: place)
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(place.displayName)
                                        .foregroundStyle(colors.ink)
                                    Text(place.address?.state ?? place.address?.country ?? "Località")
                                        .font(.caption)
                                        .foregroundStyle(colors.inkSoft)
                                }
                                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            }
                            .accessibilityHint("Seleziona questa località e carica i dati ambientali")
                        }
                    }
                }

                if let selectedLocation = viewModel.selectedLocation {
                    Section("Località selezionata") {
                        Label(selectedLocation.name, systemImage: "mappin.and.ellipse")
                        if viewModel.isLoadingEnvironment {
                            HStack {
                                ProgressView()
                                Text("Aggiornamento dati ambientali")
                            }
                        } else if let elevation = viewModel.elevation {
                            Label("Altitudine \(elevation.formatted(.number.precision(.fractionLength(0)))) m", systemImage: "mountain.2")
                        }
                    }
                }

                if let errorMessage = viewModel.errorMessage {
                    Section {
                        Label(errorMessage, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(colors.warning)
                    }
                }
            }
            .searchable(text: $query, prompt: "Cerca città, borgo o montagna")
            .onChange(of: query) { _, newValue in
                viewModel.search(query: newValue)
            }
            .scrollContentBackground(.hidden)
            .background(colors.background)
            .navigationTitle("Registro")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Impostazioni", systemImage: "gearshape") {
                        showingSettings = true
                    }
                    .accessibilityLabel("Impostazioni")
                }
            }
            .sheet(isPresented: $showingSettings) {
                SettingsView()
                    .presentationDetents([.medium])
            }
        }
    }
}
