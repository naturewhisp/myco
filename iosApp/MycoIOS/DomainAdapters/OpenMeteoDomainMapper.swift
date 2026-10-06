import Foundation
import MycoCore

enum OpenMeteoDomainMapper {
    static func processedDays(from forecast: OpenMeteoForecast) -> [ProcessedDay] {
        guard let hourly = forecast.hourly else { return [] }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: forecast.timezone) ?? TimeZone(secondsFromGMT: 0)!
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = calendar.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.isLenient = false
        let expected = Set(hourly.time.map { String($0.prefix(10)) }).sorted().compactMap { date -> ExpectedDayHours? in
            guard let start = formatter.date(from: date),
                  let end = calendar.date(byAdding: .day, value: 1, to: start) else { return nil }
            return ExpectedDayHours(dateIso: date, hours: Int32(end.timeIntervalSince(start) / 3600))
        }
        func number(_ values: [Double?]?, _ index: Int) -> KotlinDouble? {
            guard let values, values.indices.contains(index), let value = values[index] else { return nil }
            return KotlinDouble(double: value)
        }
        let observations = hourly.time.enumerated().map { index, timestamp in
            WeatherHour(
                timestamp: timestamp,
                temperature: number(hourly.temperature2m, index), humidity: number(hourly.relativeHumidity2m, index),
                precipitation: number(hourly.precipitation, index), shallowSoil: number(hourly.soilMoisture0To7, index),
                deepSoil: number(hourly.soilMoisture7To28, index), et0: number(hourly.evapotranspiration, index)
            )
        }
        let codes = (forecast.daily?.time ?? []).enumerated().map { index, date in
            let values = forecast.daily?.weatherCode ?? []
            let code = values.indices.contains(index) ? values[index] : nil
            return DailyWeatherCode(dateIso: date, code: code.map { KotlinInt(int: Int32($0)) })
        }
        return WeatherAggregation.shared.aggregate(
            observations: observations, expectedDays: expected, codes: codes, provenance: "OPEN_METEO_MODEL_FORECAST"
        )
    }
}
