import MycoCore
import XCTest
@testable import MycoIOS

final class OpenMeteoDomainMapperTests: XCTestCase {
    func testHourlyValuesAreAggregatedByDayForSharedCore() throws {
        let forecast = OpenMeteoForecast(
            latitude: 42,
            longitude: 12,
            elevation: 500,
            timezone: "Europe/Rome",
            current: nil,
            hourly: .init(
                time: ["2026-09-13T00:00", "2026-09-13T12:00", "2026-09-14T00:00"],
                temperature2m: [10, 14, 16],
                relativeHumidity2m: [80, 60, 70],
                precipitation: [1.5, 0.5, 2],
                soilMoisture0To7: [0.3, 0.5, 0.4],
                soilMoisture7To28: [0.4, 0.6, 0.5],
                evapotranspiration: [0.1, 0.2, 0.3]
            ),
            daily: .init(
                time: ["2026-09-13", "2026-09-14"],
                precipitationSum: [2, 2],
                temperature2mMax: [14, 16],
                temperature2mMin: [10, 16],
                weatherCode: [3, 61]
            )
        )

        let days = OpenMeteoDomainMapper.processedDays(from: forecast)

        XCTAssertEqual(days.count, 2)
        XCTAssertEqual(days[0].dateIso, "2026-09-13")
        XCTAssertEqual(days[0].avgTemp, 12, accuracy: 0.001)
        XCTAssertEqual(days[0].totalPrecipMm, 2, accuracy: 0.001)
        XCTAssertEqual(days[0].avgHumidityPercent, 70, accuracy: 0.001)
        XCTAssertEqual(try XCTUnwrap(days[0].soilMoisture0To7).doubleValue, 0.4, accuracy: 0.001)
        XCTAssertEqual(try XCTUnwrap(days[0].evapotranspiration).doubleValue, 0.3, accuracy: 0.001)
        XCTAssertEqual(days[0].weatherCode?.intValue, 3)
    }
}
