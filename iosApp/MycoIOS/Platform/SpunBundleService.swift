import Compression
import Foundation
import MycoCore

enum SpunBundleError: Error {
    case missingAsset
    case invalidHeader
    case decompressionFailed
    case invalidGrid
}

@MainActor
final class SpunBundleService {
    private var cachedGrid: SpunGrid?

    func sample(latitude: Double, longitude: Double, radiusMeters: Int32 = 1_500) throws -> SpunSample? {
        let grid = try loadGrid()
        return SpunParser.shared.sample(grid: grid, latitude: latitude, longitude: longitude, radiusMeters: radiusMeters)
    }

    func heatmap(
        latitude: Double,
        longitude: Double,
        analysis: AnalysisResult,
        speciesID: String,
        isDark: Bool
    ) throws -> HeatmapRaster? {
        try HeatmapEngine().generate(
            centerLatitude: latitude,
            centerLongitude: longitude,
            grid: loadGrid(),
            baseWeatherScore: Double(analysis.weatherScore),
            seasonalityScore: analysis.seasonalityScore,
            altitudeScore: analysis.altitudeScore,
            speciesId: speciesID,
            isDark: isDark,
            gridSize: 96,
            radiusKm: 35
        )
    }

    private func loadGrid() throws -> SpunGrid {
        if let cachedGrid { return cachedGrid }
        guard let url = Bundle.main.url(forResource: "spun_italy", withExtension: "bin") else {
            throw SpunBundleError.missingAsset
        }
        let data = try Data(contentsOf: url, options: .mappedIfSafe)
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

    var kotlinByteArray: KotlinByteArray {
        let result = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            result.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return result
    }
}
