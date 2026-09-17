import CoreLocation
import Foundation
import MycoCore

struct EnvironmentalDay: Identifiable {
    let date: Date
    let averageTemperature: Double
    let rainfall: Double
    let humidity: Double
    let probability: Int
    let tierLabel: String

    var id: Date { date }
}

struct SelectedLocation: Identifiable, Sendable {
    let name: String
    let coordinate: CLLocationCoordinate2D

    var id: String { "\(coordinate.latitude),\(coordinate.longitude)" }
}

@MainActor
final class MycoViewModel: ObservableObject {
    @Published private(set) var searchResults: [LocationSearchResult] = []
    @Published private(set) var selectedLocation: SelectedLocation?
    @Published private(set) var forecast: OpenMeteoForecast?
    @Published private(set) var elevation: Double?
    @Published private(set) var analysis: AnalysisResult?
    @Published private(set) var heatmap: SpunHeatmapRaster?
    @Published private(set) var fieldNote = ""
    @Published private(set) var isSearching = false
    @Published private(set) var isLoadingEnvironment = false
    @Published private(set) var isOfflineFallback = false
    @Published private(set) var errorMessage: String?
    @Published var selectedSpecies: MushroomSpecies
    @Published private(set) var favorites: [SavedPlaceValue] = []
    @Published private(set) var recentLocations: [SavedPlaceValue] = []

    let speciesCatalog: [MushroomSpecies]

    private let locationSearch: any LocationSearching
    private let openMeteo: OpenMeteoClient
    private let overpass: OverpassClient
    private let cacheStore: CacheStore?
    private let spun = SpunBundleService()
    private let fieldNoteGenerator: any FieldNoteGenerating
    private let savedPlacesStore: SavedPlacesStore?
    private let analysisEngine = MycoAnalysisEngine()
    private var searchTask: Task<Void, Never>?
    private var environmentTask: Task<Void, Never>?
    private var fieldNoteTask: Task<Void, Never>?
    private var heatmapTask: Task<Void, Never>?
    /// Monotonically identifies the environment selection currently displayed.
    /// Cancellation is cooperative, so this also rejects results from dependencies
    /// that complete after they have been cancelled.
    private var environmentGeneration = 0
    private var searchGeneration = 0

    init(
        locationSearch: any LocationSearching = MKLocalSearchService(),
        openMeteo: OpenMeteoClient = OpenMeteoClient(),
        overpass: OverpassClient = OverpassClient(),
        cacheStore: CacheStore? = nil,
        fieldNoteGenerator: any FieldNoteGenerating = FoundationModelService(),
        savedPlacesStore: SavedPlacesStore? = nil
    ) {
        self.locationSearch = locationSearch
        self.openMeteo = openMeteo
        self.overpass = overpass
        self.cacheStore = cacheStore
        self.fieldNoteGenerator = fieldNoteGenerator
        self.savedPlacesStore = savedPlacesStore
        speciesCatalog = SpeciesCatalog.shared.all
        selectedSpecies = SpeciesCatalog.shared.byId(id: "general")
        refreshSavedPlaces()
    }

    deinit {
        searchTask?.cancel()
        environmentTask?.cancel()
        fieldNoteTask?.cancel()
        heatmapTask?.cancel()
    }

    func submitSearch(query: String) {
        searchTask?.cancel()
        searchGeneration += 1
        let generation = searchGeneration
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedQuery.isEmpty else {
            isSearching = false
            searchResults = []
            errorMessage = nil
            return
        }
        isSearching = true
        errorMessage = nil
        searchTask = Task { [weak self, locationSearch] in
            do {
                let results = try await locationSearch.search(query: trimmedQuery)
                try Task.checkCancellation()
                guard self?.searchGeneration == generation else { return }
                self?.searchResults = results
                self?.isSearching = false
            } catch is CancellationError {
                return
            } catch {
                guard !Task.isCancelled, self?.searchGeneration == generation else { return }
                self?.isSearching = false
                self?.searchResults = []
                self?.errorMessage = "Impossibile cercare la località. Riprova."
            }
        }
    }

    func select(place: LocationSearchResult) {
        select(coordinate: place.coordinate, name: place.name)
    }

    func select(coordinate: CLLocationCoordinate2D, name: String = "Punto selezionato") {
        searchTask?.cancel()
        isSearching = false
        selectedLocation = SelectedLocation(name: name, coordinate: coordinate)
        try? savedPlacesStore?.recordRecent(name: name, latitude: coordinate.latitude, longitude: coordinate.longitude)
        refreshSavedPlaces()
        searchResults = []
        loadEnvironment(for: coordinate)
    }

    func useCurrentLocation(latitude: Double, longitude: Double) {
        select(coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude), name: "Posizione attuale")
    }

    func chooseSpecies(_ species: MushroomSpecies) {
        selectedSpecies = species
        guard let coordinate = selectedLocation?.coordinate else { return }
        loadEnvironment(for: coordinate)
    }

    func retry() {
        guard let coordinate = selectedLocation?.coordinate else { return }
        loadEnvironment(for: coordinate)
    }

    func refreshHeatmapPalette(isDark: Bool) {
        guard let coordinate = selectedLocation?.coordinate, let analysis else { return }
        heatmapTask?.cancel()
        let generation = environmentGeneration
        let spun = spun
        let speciesID = selectedSpecies.id
        heatmapTask = Task { [weak self] in
            let raster = try? await spun.heatmap(
                latitude: coordinate.latitude,
                longitude: coordinate.longitude,
                weatherScore: Double(analysis.weatherScore),
                seasonalityScore: analysis.seasonalityScore,
                altitudeScore: analysis.altitudeScore,
                speciesID: speciesID,
                isDark: isDark
            )
            guard !Task.isCancelled, self?.isCurrentEnvironment(generation) == true else { return }
            self?.heatmap = raster
        }
    }

    func clearCache() async throws {
        try await cacheStore?.clearCache()
    }

    func selectSavedPlace(_ place: SavedPlaceValue) {
        select(coordinate: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude), name: place.name)
    }

    func toggleFavorite() {
        guard let selectedLocation else { return }
        try? savedPlacesStore?.toggleFavorite(name: selectedLocation.name, latitude: selectedLocation.coordinate.latitude, longitude: selectedLocation.coordinate.longitude)
        refreshSavedPlaces()
    }

    func removeFavorite(id: UUID) {
        try? savedPlacesStore?.removeFavorite(id: id)
        refreshSavedPlaces()
    }

    func renameFavorite(id: UUID, name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        try? savedPlacesStore?.renameFavorite(id: id, name: trimmed)
        refreshSavedPlaces()
    }

    func isFavorite(_ location: SelectedLocation) -> Bool {
        let key = PlaceCoordinateKey(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude)
        return favorites.contains { PlaceCoordinateKey(latitude: $0.latitude, longitude: $0.longitude) == key }
    }

    var environmentalDays: [EnvironmentalDay] {
        guard let analysis else { return [] }
        return analysis.dailyOutlooks.compactMap { outlook in
            guard let date = Self.dateFormatter.date(from: outlook.dateIso) else { return nil }
            return EnvironmentalDay(
                date: date,
                averageTemperature: outlook.avgTemp,
                rainfall: outlook.totalPrecipMm,
                humidity: outlook.avgHumidityPercent,
                probability: Int(outlook.probability),
                tierLabel: outlook.tier.shortLabel
            )
        }
    }

    private func loadEnvironment(for coordinate: CLLocationCoordinate2D) {
        environmentTask?.cancel()
        heatmapTask?.cancel()
        environmentGeneration += 1
        let generation = environmentGeneration
        isLoadingEnvironment = true
        isOfflineFallback = false
        analysis = nil
        heatmap = nil
        fieldNoteTask?.cancel()
        fieldNote = ""
        errorMessage = nil
        let species = selectedSpecies
        let speciesID = species.id
        let preferredCanopyTypes = species.preferredCanopyTypes.map { $0 }
        let habitatEcologicalCategory: HabitatEcologicalCategory = species.category == .saprotrophic
            ? .saprotrophic
            : .treeAssociated

        environmentTask = Task { [weak self, openMeteo, overpass, cacheStore] in
            guard let self else { return }
            let keys = CacheKeys(coordinate: coordinate, speciesID: speciesID)
            async let loadedForecast = try? openMeteo.forecast(for: coordinate)
            async let loadedElevation = try? openMeteo.elevations(around: coordinate)
            async let loadedHabitat = try? overpass.habitat(
                    around: coordinate,
                    preferredCanopyTypes: preferredCanopyTypes,
                    ecologicalCategory: habitatEcologicalCategory
                )
            let (freshForecast, freshElevation, freshHabitat) = await (loadedForecast, loadedElevation, loadedHabitat)
            guard !Task.isCancelled, self.isCurrentEnvironment(generation) else { return }

            let cachedForecast: OpenMeteoForecast? = freshForecast == nil ? await self.cached(OpenMeteoForecast.self, key: keys.forecast, cache: cacheStore) : nil
            guard let forecast = freshForecast ?? cachedForecast else {
                guard self.isCurrentEnvironment(generation) else { return }
                self.isLoadingEnvironment = false
                self.errorMessage = "Meteo non disponibile e nessuna cache utilizzabile: l'analisi non può essere calcolata."
                return
            }
            let cachedElevation: OpenMeteoElevation? = freshElevation == nil ? await self.cached(OpenMeteoElevation.self, key: keys.elevation, cache: cacheStore) : nil
            let cachedHabitat: HabitatSnapshot? = freshHabitat == nil ? await self.cached(HabitatSnapshot.self, key: keys.habitat, cache: cacheStore) : nil
            guard !Task.isCancelled, self.isCurrentEnvironment(generation) else { return }

            if let freshForecast { await self.save(freshForecast, key: keys.forecast, ttl: 60 * 60, cache: cacheStore) }
            if let freshElevation { await self.save(freshElevation, key: keys.elevation, ttl: 30 * 24 * 60 * 60, cache: cacheStore) }
            if let freshHabitat { await self.save(freshHabitat, key: keys.habitat, ttl: 24 * 60 * 60, cache: cacheStore) }

            var missingSources: [String] = []
            if freshForecast == nil { missingSources.append("meteo live (cache)") }
            if freshElevation == nil { missingSources.append(cachedElevation == nil ? "quota DEM" : "quota DEM live (cache)") }
            if freshHabitat == nil { missingSources.append(cachedHabitat == nil ? "habitat OSM" : "habitat OSM live (cache)") }
            guard self.isCurrentEnvironment(generation) else { return }
            self.isOfflineFallback = cachedForecast != nil || cachedElevation != nil || cachedHabitat != nil

            let elevations = (freshElevation ?? cachedElevation)?.elevation ?? forecast.elevation.map { [$0] } ?? []
            let habitat = freshHabitat ?? cachedHabitat ?? HabitatSnapshot(
                score: 0.1,
                description: "Habitat non disponibile: stima conservativa e risultato parziale.",
                canopyTypes: []
            )
            let sample = try? await self.spun.sample(latitude: coordinate.latitude, longitude: coordinate.longitude)
            guard !Task.isCancelled, self.isCurrentEnvironment(generation) else { return }
            await self.finish(
                forecast: forecast,
                elevations: elevations,
                habitat: habitat,
                spunSample: sample,
                missingSources: missingSources,
                coordinate: coordinate,
                species: species,
                generation: generation
            )
        }
    }

    private func refreshSavedPlaces() {
        favorites = (try? savedPlacesStore?.favorites()) ?? []
        recentLocations = (try? savedPlacesStore?.recents()) ?? []
    }

    private func finish(
        forecast: OpenMeteoForecast,
        elevations: [Double],
        habitat: HabitatSnapshot,
        spunSample: SpunSampleValue?,
        missingSources: [String],
        coordinate: CLLocationCoordinate2D,
        species: MushroomSpecies,
        generation: Int
    ) async {
        guard isCurrentEnvironment(generation) else { return }
        let days = OpenMeteoDomainMapper.processedDays(from: forecast)
        guard !days.isEmpty else {
            guard isCurrentEnvironment(generation) else { return }
            isLoadingEnvironment = false
            errorMessage = "La risposta meteo non contiene una serie oraria utilizzabile."
            return
        }
        let todayIndex = min(14, days.count - 1)
        let month = Calendar.current.component(.month, from: .now) - 1
        let elevationSamples = (elevations.isEmpty ? [forecast.elevation ?? 0] : elevations).map { KotlinDouble(double: $0) }
        let input = AnalysisInputs(
            days: days,
            todayIndex: Int32(todayIndex),
            speciesId: species.id,
            habitatScore: habitat.score,
            habitatDescription: habitat.description,
            canopyTypes: habitat.canopyTypes,
            elevationSamples: elevationSamples,
            monthIndex: Int32(month),
            spunEcmRichness: spunSample.map { KotlinDouble(double: $0.ecmRichness) },
            spunHyphalDensity: spunSample.map { KotlinDouble(double: $0.hyphalDensity) },
            missingSources: spunSample == nil ? missingSources + ["SPUN"] : missingSources
        )
        guard isCurrentEnvironment(generation) else { return }
        self.forecast = forecast
        elevation = elevations.first ?? forecast.elevation
        let result = analysisEngine.analyze(input: input)
        analysis = result
        fieldNote = result.deterministicFieldNote
        fieldNoteTask = Task { [weak self, fieldNoteGenerator] in
            let enriched = await fieldNoteGenerator.enrich(deterministicNote: result.deterministicFieldNote)
            guard !Task.isCancelled, self?.isCurrentEnvironment(generation) == true else { return }
            self?.fieldNote = enriched
        }
        let raster = try? await spun.heatmap(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            weatherScore: Double(result.weatherScore),
            seasonalityScore: result.seasonalityScore,
            altitudeScore: result.altitudeScore,
            speciesID: species.id,
            isDark: false
        )
        guard !Task.isCancelled, isCurrentEnvironment(generation) else { return }
        heatmap = raster
        isLoadingEnvironment = false
    }

    private func isCurrentEnvironment(_ generation: Int) -> Bool {
        environmentGeneration == generation
    }

    private func save<Value: Encodable & Sendable>(
        _ value: Value,
        key: String,
        ttl: TimeInterval,
        cache: CacheStore?
    ) async {
        guard let cache else { return }
        let encoder = JSONEncoder()
        guard let payload = try? encoder.encode(value) else { return }
        try? await cache.put(key: key, payload: payload, ttl: ttl)
    }

    private func cached<Value: Decodable & Sendable>(_ type: Value.Type, key: String, cache: CacheStore?) async -> Value? {
        guard let payload = try? await cache?.get(key: key, expiredFallback: true)?.payload else { return nil }
        return try? JSONDecoder().decode(type, from: payload)
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}

private struct CacheKeys {
    let forecast: String
    let elevation: String
    let habitat: String

    init(coordinate: CLLocationCoordinate2D, speciesID: String) {
        let locale = Locale(identifier: "en_US_POSIX")
        let location = String(format: "%.4f_%.4f", locale: locale, coordinate.latitude, coordinate.longitude)
        forecast = "weather_\(location)"
        elevation = "terrain_\(location)"
        habitat = "habitat_\(speciesID)_\(location)"
    }
}
