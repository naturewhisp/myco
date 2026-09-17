import Compression
import Foundation
@preconcurrency import MycoCore

enum SpunBundleError: Error {
    case missingAsset
    case invalidHeader
    case decompressionFailed
    case invalidGrid
}

/// The values that cross the SPUN actor boundary. KMP reference types remain actor-confined.
struct SpunSampleValue: Sendable, Equatable {
    let ecmRichness: Double
    let hyphalDensity: Double
    let ecmScore: Double
    let hyphalScore: Double
    let regionCode: String
}

struct SpunHeatmapRaster: Sendable, Equatable {
    let argbPixels: [Int32]
    let width: Int
    let height: Int
    let north: Double
    let south: Double
    let west: Double
    let east: Double
}

/// Serializes SPUN parsing and keeps the non-Sendable KMP grid on a background actor.
actor SpunBundleService {
    private let assetURL: URL?
    private var cachedGrid: SpunGrid?
    private var gridLoadCount = 0

    init(assetURL: URL? = Bundle.main.url(forResource: "spun_italy", withExtension: "bin")) {
        self.assetURL = assetURL
    }

    func sample(latitude: Double, longitude: Double, radiusMeters: Int32 = 1_500) throws -> SpunSampleValue? {
        guard let sample = SpunParser.shared.sample(
            grid: try loadGrid(),
            latitude: latitude,
            longitude: longitude,
            radiusMeters: radiusMeters
        ) else {
            return nil
        }
        return SpunSampleValue(
            ecmRichness: sample.ecmRichness,
            hyphalDensity: sample.hyphalDensity,
            ecmScore: sample.ecmScore,
            hyphalScore: sample.hyphalScore,
            regionCode: sample.regionCode
        )
    }

    func heatmap(
        latitude: Double,
        longitude: Double,
        weatherScore: Double,
        seasonalityScore: Double,
        altitudeScore: Double,
        speciesID: String,
        isDark: Bool
    ) throws -> SpunHeatmapRaster? {
        guard let raster = HeatmapEngine().generate(
            centerLatitude: latitude,
            centerLongitude: longitude,
            grid: try loadGrid(),
            baseWeatherScore: weatherScore,
            seasonalityScore: seasonalityScore,
            altitudeScore: altitudeScore,
            speciesId: speciesID,
            isDark: isDark,
            gridSize: 96,
            radiusKm: 35
        ) else {
            return nil
        }
        let count = Int(raster.argbPixels.size)
        return SpunHeatmapRaster(
            argbPixels: (0..<count).map { raster.argbPixels.get(index: Int32($0)) },
            width: Int(raster.width),
            height: Int(raster.height),
            north: raster.north,
            south: raster.south,
            west: raster.west,
            east: raster.east
        )
    }

    func cachedGridLoadCount() -> Int { gridLoadCount }

    private func loadGrid() throws -> SpunGrid {
        if let cachedGrid { return cachedGrid }
        guard let assetURL else { throw SpunBundleError.missingAsset }
        let data = try Data(contentsOf: assetURL, options: .mappedIfSafe)
        guard data.count >= 32 else { throw SpunBundleError.invalidHeader }
        let header = Data(data.prefix(32))
        let width = Int(header.bigEndianUInt16(at: 26))
        let height = Int(header.bigEndianUInt16(at: 28))
        guard width > 0, height > 0 else { throw SpunBundleError.invalidHeader }
        let payload = try decompress(Data(data.dropFirst(32)), expectedSize: width * height * 2)
        guard let grid = SpunParser.shared.parseInflated(
            headerBytes: header.kotlinByteArray,
            payloadBytes: payload.kotlinByteArray
        ) else { throw SpunBundleError.invalidGrid }
        cachedGrid = grid
        gridLoadCount += 1
        return grid
    }

    private func decompress(_ source: Data, expectedSize: Int) throws -> Data {
        if let decoded = decode(source, expectedSize: expectedSize) { return decoded }

        // Apple's Compression framework consumes the RFC 1951 deflate payload on some OS releases,
        // while the Android asset is wrapped as RFC 1950 zlib (2-byte header + 4-byte Adler-32).
        guard source.count > 6 else { throw SpunBundleError.decompressionFailed }
        let deflatePayload = source.dropFirst(2).dropLast(4)
        guard let decoded = decode(Data(deflatePayload), expectedSize: expectedSize) else {
            throw SpunBundleError.decompressionFailed
        }
        return decoded
    }

    private func decode(_ source: Data, expectedSize: Int) -> Data? {
        var destination = [UInt8](repeating: 0, count: expectedSize)
        let decodedCount = destination.withUnsafeMutableBytes { destinationBuffer in
            source.withUnsafeBytes { sourceBuffer in
                compression_decode_buffer(
                    destinationBuffer.bindMemory(to: UInt8.self).baseAddress!,
                    expectedSize,
                    sourceBuffer.bindMemory(to: UInt8.self).baseAddress!,
                    source.count,
                    nil,
                    COMPRESSION_ZLIB
                )
            }
        }
        return decodedCount == expectedSize ? Data(destination) : nil
    }
}

private extension Data {
    func bigEndianUInt16(at offset: Int) -> UInt16 {
        (UInt16(self[offset]) << 8) | UInt16(self[offset + 1])
    }

    /// Kotlin/Native exposes indexed writes only. This tight index loop avoids intermediate arrays.
    var kotlinByteArray: KotlinByteArray {
        let result = KotlinByteArray(size: Int32(count))
        for index in indices {
            result.set(index: Int32(index), value: Int8(bitPattern: self[index]))
        }
        return result
    }
}
