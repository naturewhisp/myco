import Foundation
import MycoCore
import XCTest
@testable import MycoIOS

final class OpenMeteoDomainMapperTests: XCTestCase {
    func testSharedCoverageFixtureAggregatesCompleteAndIncompleteDays() throws {
        let forecast = try JSONDecoder().decode(OpenMeteoForecast.self, from: fixture("weather/coverage.json"))
        let days = OpenMeteoDomainMapper.processedDays(from: forecast)

        XCTAssertEqual(days.count, 2)
        let complete = try XCTUnwrap(days.first { $0.dateIso == "2026-10-06" })
        XCTAssertEqual(complete.avgTemp, 21.5, accuracy: 0.001)
        XCTAssertEqual(complete.minTemp, 10, accuracy: 0.001)
        XCTAssertEqual(complete.maxTemp, 33, accuracy: 0.001)
        XCTAssertEqual(complete.avgHumidityPercent, 80, accuracy: 0.001)
        XCTAssertEqual(complete.totalPrecipMm, 24, accuracy: 0.001)
        XCTAssertEqual(try XCTUnwrap(complete.soilMoisture0To7).doubleValue, 0.25, accuracy: 0.001)
        XCTAssertEqual(try XCTUnwrap(complete.soilMoisture7To28).doubleValue, 0.3, accuracy: 0.001)
        XCTAssertEqual(try XCTUnwrap(complete.evapotranspiration).doubleValue, 2.4, accuracy: 0.001)
        XCTAssertTrue(try XCTUnwrap(complete.coverage).weatherUsable)

        let incomplete = try XCTUnwrap(days.first { $0.dateIso == "2026-10-07" })
        XCTAssertFalse(try XCTUnwrap(incomplete.coverage).weatherUsable)
        XCTAssertTrue(incomplete.totalPrecipMm.isNaN)
        XCTAssertNil(incomplete.soilMoisture0To7)
        XCTAssertNil(incomplete.soilMoisture7To28)
        XCTAssertNil(incomplete.evapotranspiration)
    }

    func testDailyWeatherMeansRequireAtLeastSeventyFivePercentCoverage() throws {
        let forecast = forecast(
            times: (0..<24).map { String(format: "2026-10-06T%02d:00", locale: Locale(identifier: "en_US_POSIX"), $0) },
            temperatures: Array(repeating: 20, count: 18) + Array(repeating: nil, count: 6),
            humidities: Array(repeating: 80, count: 18) + Array(repeating: nil, count: 6),
            precipitation: Array(repeating: 0, count: 24),
            shallowSoil: Array(repeating: 0.25, count: 24),
            deepSoil: Array(repeating: 0.3, count: 24),
            et0: Array(repeating: 0.1, count: 24)
        )

        let day = try XCTUnwrap(OpenMeteoDomainMapper.processedDays(from: forecast).first)
        XCTAssertTrue(try XCTUnwrap(day.coverage).weatherUsable)
        XCTAssertEqual(day.avgTemp, 20, accuracy: 0.001)
        XCTAssertEqual(day.totalPrecipMm, 0, accuracy: 0.001)
    }

    func testRainAndEvapotranspirationRequireCompleteHourlyCoverage() throws {
        let forecast = forecast(
            times: (0..<24).map { String(format: "2026-10-06T%02d:00", locale: Locale(identifier: "en_US_POSIX"), $0) },
            temperatures: Array(repeating: 20, count: 24),
            humidities: Array(repeating: 80, count: 24),
            precipitation: [nil] + Array(repeating: 1, count: 23),
            shallowSoil: Array(repeating: 0.25, count: 24),
            deepSoil: Array(repeating: 0.3, count: 24),
            et0: [0.1, nil] + Array(repeating: 0.1, count: 22)
        )

        let day = try XCTUnwrap(OpenMeteoDomainMapper.processedDays(from: forecast).first)
        let coverage = try XCTUnwrap(day.coverage)
        XCTAssertTrue(coverage.temperatureHours >= 18)
        XCTAssertTrue(coverage.humidityHours >= 18)
        XCTAssertEqual(coverage.precipitationHours, 23)
        XCTAssertEqual(coverage.et0Hours, 23)
        XCTAssertFalse(coverage.weatherUsable)
        XCTAssertTrue(day.totalPrecipMm.isNaN)
        XCTAssertNil(day.evapotranspiration)
    }

    func testMissingHourlyValuesRemainVisibleAndAreNotReplacedWithDryWeather() throws {
        let forecast = forecast(
            times: ["2026-10-06T00:00", "2026-10-06T01:00"],
            temperatures: [nil, 15],
            humidities: [80, nil],
            precipitation: [1, nil],
            shallowSoil: [0.3, 0.3],
            deepSoil: [0.4, 0.4],
            et0: [0.1, 0.1]
        )

        let days = OpenMeteoDomainMapper.processedDays(from: forecast)
        XCTAssertEqual(days.count, 1)
        let day = days[0]
        XCTAssertFalse(try XCTUnwrap(day.coverage).weatherUsable)
        XCTAssertTrue(day.totalPrecipMm.isNaN)
        XCTAssertNil(day.soilMoisture0To7)
        XCTAssertNil(day.evapotranspiration)
    }

    func testExpectedHoursFollowEuropeRomeDaylightSavingTransitions() throws {
        for (date, expectedHours) in [("2026-03-29", 23), ("2026-10-25", 25)] {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "Europe/Rome"))
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = calendar.timeZone
            formatter.dateFormat = "yyyy-MM-dd HH:mm"
            let dayStart = try XCTUnwrap(formatter.date(from: "\(date) 00:00"))
            let dayEnd = try XCTUnwrap(calendar.date(byAdding: .day, value: 1, to: dayStart))
            formatter.dateFormat = "yyyy-MM-dd'T'HH:mm"
            let times = stride(from: dayStart, to: dayEnd, by: 60 * 60).map { formatter.string(from: $0) }
            let forecast = forecast(
                times: times,
                temperatures: Array(repeating: 20, count: expectedHours),
                humidities: Array(repeating: 80, count: expectedHours),
                precipitation: Array(repeating: 1, count: expectedHours),
                shallowSoil: Array(repeating: 0.25, count: expectedHours),
                deepSoil: Array(repeating: 0.3, count: expectedHours),
                et0: Array(repeating: 0.1, count: expectedHours)
            )

            let day = try XCTUnwrap(OpenMeteoDomainMapper.processedDays(from: forecast).first)
            let coverage = try XCTUnwrap(day.coverage)
            XCTAssertEqual(coverage.expectedHours, Int32(expectedHours))
            if expectedHours == 23 {
                XCTAssertTrue(coverage.weatherUsable)
                XCTAssertEqual(day.totalPrecipMm, Double(expectedHours), accuracy: 0.001)
                XCTAssertEqual(try XCTUnwrap(day.evapotranspiration).doubleValue, Double(expectedHours) * 0.1, accuracy: 0.001)
            } else {
                XCTAssertFalse(coverage.weatherUsable, "The repeated local hour is ambiguous without a UTC offset")
                XCTAssertTrue(day.totalPrecipMm.isNaN)
            }
        }
    }

    func testFallBackDayAcceptsBothRepeatedHoursWhenOffsetsArePresent() throws {
        let localHours = ["00", "01", "02", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23"]
        let times = localHours.enumerated().map { index, hour in
            let offset = index == 2 ? "+02:00" : (index == 3 ? "+01:00" : "")
            return "2026-10-25T\(hour):00\(offset)"
        }
        let forecast = forecast(
            times: times,
            temperatures: Array(repeating: 20, count: 25),
            humidities: Array(repeating: 80, count: 25),
            precipitation: Array(repeating: 1, count: 25),
            shallowSoil: Array(repeating: 0.25, count: 25),
            deepSoil: Array(repeating: 0.3, count: 25),
            et0: Array(repeating: 0.1, count: 25)
        )

        let day = try XCTUnwrap(OpenMeteoDomainMapper.processedDays(from: forecast).first)
        let coverage = try XCTUnwrap(day.coverage)
        XCTAssertEqual(coverage.expectedHours, 25)
        XCTAssertTrue(coverage.weatherUsable)
        XCTAssertEqual(day.totalPrecipMm, 25, accuracy: 0.001)
        XCTAssertEqual(try XCTUnwrap(day.evapotranspiration).doubleValue, 2.5, accuracy: 0.001)
    }

    func testMindinoRawFixtureMapsTheRecoveredSeptemberTwentyNinthWeather() throws {
        let data = try fixture("mindino/weather-original.json")
        let forecast = try JSONDecoder().decode(OpenMeteoForecast.self, from: data)
        let raw = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let hourly = try XCTUnwrap(raw["hourly"] as? [String: Any])
        let times = try XCTUnwrap(hourly["time"] as? [String])
        let temperatureValues = numericArray(hourly["temperature_2m"])
        let humidityValues = numericArray(hourly["relativehumidity_2m"] ?? hourly["relative_humidity_2m"])
        let precipitationValues = numericArray(hourly["precipitation"])
        let indexes = times.indices.filter { times[$0].hasPrefix("2026-09-29") }
        XCTAssertFalse(indexes.isEmpty, "The recovered payload must contain September 29 hourly values")

        let day = try XCTUnwrap(OpenMeteoDomainMapper.processedDays(from: forecast).first { $0.dateIso == "2026-09-29" })
        XCTAssertEqual(day.weatherCode?.intValue, 3)
        let temperatures = indexes.compactMap { index -> Double? in
            guard temperatureValues.indices.contains(index) else { return nil }
            return temperatureValues[index]
        }
        let humidities = indexes.compactMap { index -> Double? in
            guard humidityValues.indices.contains(index) else { return nil }
            return humidityValues[index]
        }
        XCTAssertFalse(temperatures.isEmpty)
        XCTAssertFalse(humidities.isEmpty)
        XCTAssertEqual(day.minTemp, try XCTUnwrap(temperatures.min()), accuracy: 0.001)
        XCTAssertEqual(day.maxTemp, try XCTUnwrap(temperatures.max()), accuracy: 0.001)
        XCTAssertEqual(day.avgTemp, temperatures.reduce(0, +) / Double(temperatures.count), accuracy: 0.001)
        XCTAssertEqual(day.avgHumidityPercent, humidities.reduce(0, +) / Double(humidities.count), accuracy: 0.001)
        if indexes.count == 24, indexes.allSatisfy({ precipitationValues.indices.contains($0) && precipitationValues[$0] != nil }) {
            XCTAssertEqual(day.totalPrecipMm, indexes.reduce(0.0) { $0 + (precipitationValues[$1] ?? 0) }, accuracy: 0.001)
        } else {
            XCTAssertTrue(day.totalPrecipMm.isNaN)
        }
    }

    private func fixture(_ path: String) throws -> Data {
        let components = path.split(separator: "/").map(String.init)
        let file = try XCTUnwrap(components.last)
        let folder = components.dropLast().joined(separator: "/")
        let name = URL(fileURLWithPath: file).deletingPathExtension().lastPathComponent
        let ext = URL(fileURLWithPath: file).pathExtension
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: ext, subdirectory: "testFixtures/\(folder)"))
        return try Data(contentsOf: url)
    }

    private func numericArray(_ value: Any?) -> [Double?] {
        (value as? [Any])?.map { ($0 as? NSNumber)?.doubleValue } ?? []
    }

    private func forecast(
        times: [String],
        temperatures: [Double?],
        humidities: [Double?],
        precipitation: [Double?],
        shallowSoil: [Double?],
        deepSoil: [Double?],
        et0: [Double?]
    ) -> OpenMeteoForecast {
        OpenMeteoForecast(
            latitude: 42,
            longitude: 12,
            elevation: 500,
            timezone: "Europe/Rome",
            current: nil,
            hourly: .init(
                time: times,
                temperature2m: temperatures,
                relativeHumidity2m: humidities,
                precipitation: precipitation,
                soilMoisture0To7: shallowSoil,
                soilMoisture7To28: deepSoil,
                evapotranspiration: et0
            ),
            daily: nil
        )
    }
}
