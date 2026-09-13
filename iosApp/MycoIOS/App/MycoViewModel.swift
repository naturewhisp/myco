import CoreLocation
import Foundation

struct EnvironmentalDay: Identifiable, Sendable {
    let date: Date
    let minimumTemperature: Double?
    let maximumTemperature: Double?
    let rainfall: Double?

    var id: Date { date }
}

struct SelectedLocation: Identifiable, Sendable {
    let name: String
    let coordinate: CLLocationCoordinate2D

    var id: String { "\(coordinate.latitude),\(coordinate.longitude)" }
}

@MainActor
final class MycoViewModel: ObservableObject {
    @Published private(set) var searchResults: [NominatimPlace] = []
    @Published private(set) var selectedLocation: SelectedLocation?
    @Published private(set) var forecast: OpenMeteoForecast?
    @Published private(set) var elevation: Double?
    @Published private(set) var isSearching = false
    @Published private(set) var isLoadingEnvironment = false
    @Published private(set) var errorMessage: String?

    private let nominatim: NominatimClient
    private let openMeteo: OpenMeteoClient
    private var searchTask: Task<Void, Never>?
    private var environmentTask: Task<Void, Never>?

    init(
        nominatim: NominatimClient = NominatimClient(),
        openMeteo: OpenMeteoClient = OpenMeteoClient()
    ) {
        self.nominatim = nominatim
        self.openMeteo = openMeteo
    }

    deinit {
        searchTask?.cancel()
        environmentTask?.cancel()
    }

    func search(query: String) {
        searchTask?.cancel()
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedQuery.isEmpty else {
            isSearching = false
            searchResults = []
            errorMessage = nil
            return
        }

        isSearching = true
        errorMessage = nil
        searchTask = Task { [weak self, nominatim] in
            do {
                try await Task.sleep(for: .milliseconds(250))
                try Task.checkCancellation()
                let results = try await nominatim.search(query: trimmedQuery)
                try Task.checkCancellation()
                self?.searchResults = results
                self?.isSearching = false
            } catch is CancellationError {
                // A newer query superseded this request.
            } catch {
                guard !Task.isCancelled else { return }
                self?.isSearching = false
                self?.searchResults = []
                self?.errorMessage = "Impossibile cercare la località. Riprova."
            }
        }
    }

    func select(place: NominatimPlace) {
        selectedLocation = SelectedLocation(name: place.displayName, coordinate: place.coordinate)
        searchResults = []
        loadEnvironment(for: place.coordinate)
    }

    func select(coordinate: CLLocationCoordinate2D, name: String = "Punto selezionato") {
        selectedLocation = SelectedLocation(name: name, coordinate: coordinate)
        searchResults = []
        loadEnvironment(for: coordinate)
    }

    func useCurrentLocation(latitude: Double, longitude: Double) {
        select(
            coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            name: "Posizione attuale"
        )
    }

    var environmentalDays: [EnvironmentalDay] {
        guard let daily = forecast?.daily else { return [] }
        return daily.time.enumerated().compactMap { (index, dateString) -> EnvironmentalDay? in
            guard let date = Self.dateFormatter.date(from: dateString) else { return nil }
            return EnvironmentalDay(
                date: date,
                minimumTemperature: daily.temperature2mMin?.value(at: index) ?? nil,
                maximumTemperature: daily.temperature2mMax?.value(at: index) ?? nil,
                rainfall: daily.precipitationSum?.value(at: index) ?? nil
            )
        }
    }

    private func loadEnvironment(for coordinate: CLLocationCoordinate2D) {
        environmentTask?.cancel()
        isLoadingEnvironment = true
        forecast = nil
        elevation = nil
        errorMessage = nil

        environmentTask = Task { [weak self, openMeteo] in
            do {
                async let loadedForecast = openMeteo.forecast(for: coordinate)
                async let loadedElevation = openMeteo.elevation(for: coordinate)
                let (forecast, elevation) = try await (loadedForecast, loadedElevation)
                try Task.checkCancellation()
                self?.forecast = forecast
                self?.elevation = elevation.firstElevation ?? forecast.elevation
                self?.isLoadingEnvironment = false
            } catch is CancellationError {
                // A newer location superseded this request.
            } catch {
                guard !Task.isCancelled else { return }
                self?.isLoadingEnvironment = false
                self?.errorMessage = "Impossibile caricare i dati ambientali. Riprova."
            }
        }
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

private extension Array {
    func value(at index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
