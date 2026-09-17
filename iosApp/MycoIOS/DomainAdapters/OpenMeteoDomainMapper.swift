import Foundation
import MycoCore

enum OpenMeteoDomainMapper {
    private struct Accumulator {
        var temperatures: [Double] = []
        var precipitation: [Double] = []
        var humidity: [Double] = []
        var shallowSoil: [Double] = []
        var deepSoil: [Double] = []
        var evapotranspiration: [Double] = []
    }

    static func processedDays(from forecast: OpenMeteoForecast) -> [ProcessedDay] {
        guard let hourly = forecast.hourly else { return [] }
        var accumulators: [String: Accumulator] = [:]
        for (index, timestamp) in hourly.time.enumerated() {
            let date = String(timestamp.prefix(10))
            var value = accumulators[date, default: Accumulator()]
            append(hourly.temperature2m?.safeValue(at: index), to: &value.temperatures)
            append(hourly.precipitation?.safeValue(at: index), to: &value.precipitation)
            append(hourly.relativeHumidity2m?.safeValue(at: index), to: &value.humidity)
            append(hourly.soilMoisture0To7?.safeValue(at: index), to: &value.shallowSoil)
            append(hourly.soilMoisture7To28?.safeValue(at: index), to: &value.deepSoil)
            append(hourly.evapotranspiration?.safeValue(at: index), to: &value.evapotranspiration)
            accumulators[date] = value
        }

        let weatherCodes = Dictionary(uniqueKeysWithValues: zip(forecast.daily?.time ?? [], forecast.daily?.weatherCode ?? []))
        return accumulators.keys.sorted().compactMap { date in
            guard let value = accumulators[date] else { return nil }
            return ProcessedDay(
                dateIso: date,
                avgTemp: value.temperatures.average ?? 0,
                totalPrecipMm: value.precipitation.reduce(0, +),
                avgHumidityPercent: value.humidity.average ?? 0,
                weatherCode: weatherCodes[date].flatMap { $0 }.map { KotlinInt(int: Int32($0)) },
                soilMoisture0To7: value.shallowSoil.average.map { KotlinDouble(double: $0) },
                soilMoisture7To28: value.deepSoil.average.map { KotlinDouble(double: $0) },
                evapotranspiration: value.evapotranspiration.isEmpty ? nil : KotlinDouble(double: value.evapotranspiration.reduce(0, +))
            )
        }
    }

    private static func append(_ value: Double?, to values: inout [Double]) {
        if let value { values.append(value) }
    }
}

private extension Array where Element == Double {
    var average: Double? { isEmpty ? nil : reduce(0, +) / Double(count) }
}

private extension Array where Element == Double? {
    func safeValue(at index: Int) -> Double? {
        indices.contains(index) ? self[index] : nil
    }
}
