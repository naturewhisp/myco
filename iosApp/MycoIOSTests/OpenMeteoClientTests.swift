import CoreLocation
import Foundation
import XCTest
@testable import MycoIOS

final class OpenMeteoClientTests: XCTestCase {
    func testForecastMapsCurrentHourlyAndDailyFields() async throws {
        let payload = Data("""
        {"latitude":41.9,"longitude":12.5,"elevation":35.0,"timezone":"Europe/Rome","current":{"time":"2026-09-13T12:00","temperature_2m":24.5,"relative_humidity_2m":61.0,"precipitation":0.2},"hourly":{"time":["2026-09-13T12:00"],"temperature_2m":[24.5],"relative_humidity_2m":[61.0],"precipitation":[0.2]},"daily":{"time":["2026-09-13"],"precipitation_sum":[2.1],"temperature_2m_max":[27.0],"temperature_2m_min":[16.0]}}
        """.utf8)
        let loader = TestHTTPDataLoader { request in
            (payload, httpResponse(for: request))
        }
        let client = OpenMeteoClient(
            apiClient: APIClient(loader: loader),
            forecastURL: URL(string: "https://meteo.test/forecast")!,
            elevationURL: URL(string: "https://meteo.test/elevation")!
        )

        let forecast = try await client.forecast(for: CLLocationCoordinate2D(latitude: 41.9, longitude: 12.5), timezone: "Europe/Rome")

        XCTAssertEqual(forecast.current?.temperature2m, 24.5)
        XCTAssertEqual(forecast.current?.relativeHumidity2m, 61.0)
        XCTAssertEqual(forecast.hourly?.precipitation?.first, 0.2)
        XCTAssertEqual(forecast.daily?.temperature2mMin?.first, 16.0)
        XCTAssertEqual(forecast.timezone, "Europe/Rome")
    }

    func testElevationExposesFirstParallelArrayValue() async throws {
        let payload = Data("""
        {"latitude":[41.9],"longitude":[12.5],"elevation":[35.0]}
        """.utf8)
        let loader = TestHTTPDataLoader { request in
            (payload, httpResponse(for: request))
        }
        let client = OpenMeteoClient(apiClient: APIClient(loader: loader), elevationURL: URL(string: "https://meteo.test/elevation")!)

        let elevation = try await client.elevation(for: CLLocationCoordinate2D(latitude: 41.9, longitude: 12.5))

        XCTAssertEqual(elevation.firstElevation, 35.0)
    }
}
