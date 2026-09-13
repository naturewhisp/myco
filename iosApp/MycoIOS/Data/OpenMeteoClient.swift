import CoreLocation
import Foundation

struct OpenMeteoForecast: Codable, Sendable {
    let latitude: Double
    let longitude: Double
    let elevation: Double?
    let timezone: String
    let current: Current?
    let hourly: Hourly?
    let daily: Daily?

    struct Current: Codable, Sendable {
        let time: String
        let temperature2m: Double?
        let relativeHumidity2m: Double?
        let precipitation: Double?

        enum CodingKeys: String, CodingKey {
            case time, precipitation
            case temperature2m = "temperature_2m"
            case relativeHumidity2m = "relative_humidity_2m"
        }
    }

    struct Hourly: Codable, Sendable {
        let time: [String]
        let temperature2m: [Double?]?
        let relativeHumidity2m: [Double?]?
        let precipitation: [Double?]?
        let soilMoisture0To7: [Double?]?
        let soilMoisture7To28: [Double?]?
        let evapotranspiration: [Double?]?

        enum CodingKeys: String, CodingKey {
            case time, precipitation
            case temperature2m = "temperature_2m"
            case relativeHumidity2m = "relative_humidity_2m"
            case soilMoisture0To7 = "soil_moisture_0_to_7cm"
            case soilMoisture7To28 = "soil_moisture_7_to_28cm"
            case evapotranspiration = "et0_fao_evapotranspiration"
        }
    }

    struct Daily: Codable, Sendable {
        let time: [String]
        let precipitationSum: [Double?]?
        let temperature2mMax: [Double?]?
        let temperature2mMin: [Double?]?
        let weatherCode: [Int?]?

        enum CodingKeys: String, CodingKey {
            case time
            case precipitationSum = "precipitation_sum"
            case temperature2mMax = "temperature_2m_max"
            case temperature2mMin = "temperature_2m_min"
            case weatherCode = "weather_code"
        }
    }
}

struct OpenMeteoElevation: Codable, Sendable {
    let latitude: [Double]
    let longitude: [Double]
    let elevation: [Double]

    /// The API returns parallel arrays; this is the elevation for a one-coordinate request.
    var firstElevation: Double? { elevation.first }
}

struct OpenMeteoClient: Sendable {
    private let apiClient: APIClient
    private let forecastURL: URL
    private let elevationURL: URL

    init(
        apiClient: APIClient = APIClient(),
        forecastURL: URL = URL(string: "https://api.open-meteo.com/v1/forecast")!,
        elevationURL: URL = URL(string: "https://api.open-meteo.com/v1/elevation")!
    ) {
        self.apiClient = apiClient
        self.forecastURL = forecastURL
        self.elevationURL = elevationURL
    }

    func forecast(for coordinate: CLLocationCoordinate2D, timezone: String = "auto") async throws -> OpenMeteoForecast {
        var components = URLComponents(url: forecastURL, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "latitude", value: String(coordinate.latitude)),
            URLQueryItem(name: "longitude", value: String(coordinate.longitude)),
            URLQueryItem(name: "current", value: "temperature_2m,relative_humidity_2m,precipitation"),
            URLQueryItem(name: "hourly", value: "temperature_2m,relative_humidity_2m,precipitation,soil_moisture_0_to_7cm,soil_moisture_7_to_28cm,et0_fao_evapotranspiration"),
            URLQueryItem(name: "daily", value: "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum"),
            URLQueryItem(name: "past_days", value: "14"),
            URLQueryItem(name: "forecast_days", value: "11"),
            URLQueryItem(name: "timezone", value: timezone),
        ]
        return try await apiClient.decode(OpenMeteoForecast.self, from: URLRequest(url: components.url!))
    }

    func elevation(for coordinate: CLLocationCoordinate2D) async throws -> OpenMeteoElevation {
        try await elevations(around: coordinate)
    }

    /// Samples center, north, south, east and west at the same 75 m spacing used by Android.
    func elevations(around coordinate: CLLocationCoordinate2D, deltaMeters: Double = 75) async throws -> OpenMeteoElevation {
        let latitudeDelta = deltaMeters / 111_320
        let longitudeScale = max(1, 111_320 * cos(coordinate.latitude * .pi / 180))
        let longitudeDelta = deltaMeters / longitudeScale
        let coordinates = [
            coordinate,
            CLLocationCoordinate2D(latitude: coordinate.latitude + latitudeDelta, longitude: coordinate.longitude),
            CLLocationCoordinate2D(latitude: coordinate.latitude - latitudeDelta, longitude: coordinate.longitude),
            CLLocationCoordinate2D(latitude: coordinate.latitude, longitude: coordinate.longitude + longitudeDelta),
            CLLocationCoordinate2D(latitude: coordinate.latitude, longitude: coordinate.longitude - longitudeDelta),
        ]
        var components = URLComponents(url: elevationURL, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "latitude", value: coordinates.map { String($0.latitude) }.joined(separator: ",")),
            URLQueryItem(name: "longitude", value: coordinates.map { String($0.longitude) }.joined(separator: ",")),
        ]
        return try await apiClient.decode(OpenMeteoElevation.self, from: URLRequest(url: components.url!))
    }
}
