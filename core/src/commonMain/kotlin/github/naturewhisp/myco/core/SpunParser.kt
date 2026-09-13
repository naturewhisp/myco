package github.naturewhisp.myco.core

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object SpunParser {
    const val headerSize: Int = 32

    /** Parses the platform-inflated payload; zlib decompression remains an adapter responsibility. */
    fun parseInflated(headerBytes: ByteArray, payloadBytes: ByteArray): SpunGrid? {
        if (headerBytes.size < headerSize || headerBytes.copyOfRange(0, 4).decodeToString() != "SPUN") return null
        val version = headerBytes.u16(4)
        val regionCode = headerBytes.copyOfRange(6, 10).decodeToString().trimEnd('\u0000')
        val minLat = Float.fromBits(headerBytes.i32(10)).toDouble()
        val maxLat = Float.fromBits(headerBytes.i32(14)).toDouble()
        val minLon = Float.fromBits(headerBytes.i32(18)).toDouble()
        val maxLon = Float.fromBits(headerBytes.i32(22)).toDouble()
        val width = headerBytes.u16(26)
        val height = headerBytes.u16(28)
        val step = headerBytes.u16(30)
        val gridSize = width * height
        if (width <= 0 || height <= 0 || payloadBytes.size != gridSize * 2) return null
        return SpunGrid(
            SpunRegionHeader(version, regionCode, minLat, maxLat, minLon, maxLon, width, height, step),
            payloadBytes.copyOfRange(0, gridSize),
            payloadBytes.copyOfRange(gridSize, gridSize * 2),
        )
    }

    fun sample(grid: SpunGrid, latitude: Double, longitude: Double, radiusMeters: Int = 1500): SpunSample? {
        val header = grid.header
        if (latitude !in header.minLat..header.maxLat || longitude !in header.minLon..header.maxLon) return null
        val stepLon = (header.maxLon - header.minLon) / header.width
        val stepLat = (header.maxLat - header.minLat) / header.height
        val centerColumn = ((longitude - header.minLon) / stepLon).toInt()
        val centerRow = ((header.maxLat - latitude) / stepLat).toInt()
        val metersPerDegreeLongitude = 111_320.0 * cos(degreesToRadians(latitude))
        val rowRadius = max(1, ((radiusMeters / 111_320.0) / stepLat).roundToInt())
        val columnRadius = max(1, ((radiusMeters / max(1.0, metersPerDegreeLongitude)) / stepLon).roundToInt())
        val ecm = mutableListOf<Double>()
        val hyphal = mutableListOf<Double>()
        for (row in max(0, centerRow - rowRadius)..min(header.height - 1, centerRow + rowRadius)) {
            val normalizedRow = (row - centerRow).toDouble() / rowRadius
            for (column in max(0, centerColumn - columnRadius)..min(header.width - 1, centerColumn + columnRadius)) {
                val normalizedColumn = (column - centerColumn).toDouble() / columnRadius
                if (normalizedRow * normalizedRow + normalizedColumn * normalizedColumn <= 1.0) {
                    val index = row * header.width + column
                    val ecmValue = grid.ecmData[index].toInt() and 0xFF
                    val hyphalValue = grid.hyphalData[index].toInt() and 0xFF
                    if (ecmValue > 0) ecm += ecmValue.toDouble()
                    if (hyphalValue > 0) hyphal += hyphalValue / 20.0
                }
            }
        }
        if (ecm.isEmpty() && hyphal.isEmpty()) return null
        val ecmValue = ecm.percentile80()
        val hyphalValue = hyphal.percentile80()
        return SpunSample(
            ecmValue,
            hyphalValue,
            when {
                ecmValue >= 55.0 -> 1.0
                ecmValue >= 40.0 -> 0.9
                ecmValue >= 25.0 -> 0.75
                ecmValue >= 12.0 -> 0.55
                else -> 0.35
            },
            when {
                hyphalValue >= 5.5 -> 1.0
                hyphalValue >= 4.0 -> 0.85
                hyphalValue >= 2.5 -> 0.65
                else -> 0.40
            },
            header.regionCode,
        )
    }

    private fun ByteArray.u16(offset: Int): Int = ((this[offset].toInt() and 0xFF) shl 8) or
        (this[offset + 1].toInt() and 0xFF)

    private fun ByteArray.i32(offset: Int): Int = ((this[offset].toInt() and 0xFF) shl 24) or
        ((this[offset + 1].toInt() and 0xFF) shl 16) or
        ((this[offset + 2].toInt() and 0xFF) shl 8) or
        (this[offset + 3].toInt() and 0xFF)

    private fun List<Double>.percentile80(): Double = if (isEmpty()) 0.0 else sorted()[((size - 1) * 0.8).toInt()]

    private fun degreesToRadians(value: Double): Double = value * kotlin.math.PI / 180.0
}
