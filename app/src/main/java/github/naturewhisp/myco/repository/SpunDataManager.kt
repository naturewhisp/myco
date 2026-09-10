package github.naturewhisp.myco.repository

import github.naturewhisp.myco.model.ClosestCoverageResult
import github.naturewhisp.myco.model.SpunData
import github.naturewhisp.myco.model.SpunRegionDescriptor
import github.naturewhisp.myco.model.SpunRegionHeader
import github.naturewhisp.myco.platform.AssetProvider
import github.naturewhisp.myco.utils.MushroomAlgorithms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.InflaterInputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Gestore dei dati di biodiversità fungina e densità ifale sotterranea del consorzio SPUN.
 *
 * Totalmente agnostico dalla piattaforma e privo di dipendenze da Android, legge i file binari
 * compressi zlib tramite l'astrazione [AssetProvider] e fornisce interrogazioni geospaziali ad alta efficienza.
 *
 * @param assetProvider Astrazione per l'accesso agli asset dell'applicazione [AssetProvider].
 */
class SpunDataManager(private val assetProvider: AssetProvider) {

    // Regioni registrate disponibili
    val availableRegions = listOf(
        SpunRegionDescriptor(
            regionCode = "ALP",
            displayName = "Italia e Arco Alpino",
            assetFileName = "spun/spun_italy.bin",
            minLat = 35.0f,
            maxLat = 48.5f,
            minLon = 5.0f,
            maxLon = 19.0f
        )
    )

    private val sentinelCoveragePoints = listOf(
        ClosestCoverageResult("Val Veny / Courmayeur (AO)", 45.7969, 6.9697, 0),
        ClosestCoverageResult("Passo del Gran San Bernardo (AO)", 45.8690, 7.1706, 0),
        ClosestCoverageResult("Passo del Brennero (BZ)", 47.0061, 11.5058, 0),
        ClosestCoverageResult("Foresta di Tarvisio (UD)", 46.5050, 13.5786, 0),
        ClosestCoverageResult("Parco Alpi Marittime (CN)", 44.2000, 7.2667, 0),
        ClosestCoverageResult("Foreste Casentinesi (FC)", 43.8500, 11.7500, 0),
        ClosestCoverageResult("Parco Gran Sasso (AQ)", 42.4500, 13.5500, 0),
        ClosestCoverageResult("Parco Nazionale del Pollino (PZ/CS)", 39.9167, 16.1833, 0),
        ClosestCoverageResult("Parco Nazionale dell'Aspromonte (RC)", 38.1667, 15.9167, 0),
        ClosestCoverageResult("Parco delle Madonie (PA)", 37.8800, 14.0200, 0),
        ClosestCoverageResult("Parco del Gennargentu (NU)", 39.9833, 9.3167, 0)
    )

    class LoadedRegion(
        val descriptor: SpunRegionDescriptor,
        val header: SpunRegionHeader,
        val ecmData: ByteArray,
        val hyphalData: ByteArray
    )

    private var currentLoadedRegion: LoadedRegion? = null
    private val loadMutex = Mutex()

    /**
     * Calcola dinamicamente la stazione di confine SPUN più vicina alle coordinate fornite,
     * determinando il toponimo e la distanza ortodromica in km tramite la formula di Haversine.
     */
    fun findClosestCoveragePoint(lat: Double, lon: Double): ClosestCoverageResult {
        var closest = sentinelCoveragePoints[0]
        var minDistance = Double.MAX_VALUE

        for (point in sentinelCoveragePoints) {
            val dist = MushroomAlgorithms.haversineDistanceKm(lat, lon, point.lat, point.lon)
            if (dist < minDistance) {
                minDistance = dist
                closest = point
            }
        }
        return closest.copy(distanceKm = max(1, minDistance.roundToInt()))
    }

    /**
     * Restituisce i dati della regione attualmente caricata in memoria se contiene le coordinate.
     */
    fun getCurrentRegionData(lat: Double, lon: Double): LoadedRegion? {
        val loaded = currentLoadedRegion ?: return null
        return if (loaded.descriptor.contains(lat, lon)) loaded else null
    }

    /**
     * Identifica la regione geografica contenente le coordinate specificate.
     */
    fun findRegionFor(lat: Double, lon: Double): SpunRegionDescriptor? {
        return availableRegions.firstOrNull { it.contains(lat, lon) }
    }

    /**
     * Assicura che la regione associata alle coordinate sia caricata in memoria.
     */
    suspend fun ensureRegionLoadedFor(lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        val targetDescriptor = findRegionFor(lat, lon) ?: return@withContext
        if (currentLoadedRegion?.descriptor?.regionCode == targetDescriptor.regionCode) {
            return@withContext
        }

        loadMutex.withLock {
            if (currentLoadedRegion?.descriptor?.regionCode == targetDescriptor.regionCode) {
                return@withLock
            }

            try {
                assetProvider.open(targetDescriptor.assetFileName).use { inputStream ->
                    // 1. Legge i 32 byte dell'header
                    val headerBytes = ByteArray(32)
                    var readTotal = 0
                    while (readTotal < 32) {
                        val count = inputStream.read(headerBytes, readTotal, 32 - readTotal)
                        if (count == -1) break
                        readTotal += count
                    }
                    if (readTotal < 32) return@withLock

                    val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
                    val magicBytes = ByteArray(4)
                    buffer.get(magicBytes)
                    val magic = String(magicBytes, Charsets.US_ASCII)
                    if (magic != "SPUN") return@withLock

                    val version = buffer.short.toInt() and 0xFFFF
                    val regionBytes = ByteArray(4)
                    buffer.get(regionBytes)
                    val regionCode = String(regionBytes, Charsets.US_ASCII).trim('\u0000')
                    val minLat = buffer.float
                    val maxLat = buffer.float
                    val minLon = buffer.float
                    val maxLon = buffer.float
                    val width = buffer.short.toInt() and 0xFFFF
                    val height = buffer.short.toInt() and 0xFFFF
                    val step = buffer.short.toInt() and 0xFFFF

                    val header = SpunRegionHeader(
                        magic = magic,
                        version = version,
                        regionCode = regionCode,
                        minLat = minLat,
                        maxLat = maxLat,
                        minLon = minLon,
                        maxLon = maxLon,
                        width = width,
                        height = height,
                        stepArcSec = step
                    )

                    // 2. Decompressione zlib del payload (EcM + Hyphal)
                    val expectedGridSize = width * height
                    val totalExpectedBytes = expectedGridSize * 2

                    val compressedBytes = inputStream.readBytes()
                    val inflaterStream = InflaterInputStream(ByteArrayInputStream(compressedBytes))
                    val fullPayload = ByteArray(totalExpectedBytes)
                    val dataIn = DataInputStream(inflaterStream)
                    dataIn.readFully(fullPayload)

                    val ecmBytes = ByteArray(expectedGridSize)
                    val hyphalBytes = ByteArray(expectedGridSize)
                    System.arraycopy(fullPayload, 0, ecmBytes, 0, expectedGridSize)
                    System.arraycopy(fullPayload, expectedGridSize, hyphalBytes, 0, expectedGridSize)

                    currentLoadedRegion = LoadedRegion(
                        descriptor = targetDescriptor,
                        header = header,
                        ecmData = ecmBytes,
                        hyphalData = hyphalBytes
                    )
                }
            } catch (_: Exception) {
                // Fallback trasparente in caso di errore di lettura
            }
        }
    }

    /**
     * Interroga le metriche SPUN aggregando l'area circolare specificata da [radiusMeters].
     */
    suspend fun getSpunData(lat: Double, lon: Double, radiusMeters: Int = 1500): SpunData? = withContext(Dispatchers.Default) {
        ensureRegionLoadedFor(lat, lon)
        val region = currentLoadedRegion ?: return@withContext null
        val header = region.header

        if (lat < header.minLat || lat > header.maxLat || lon < header.minLon || lon > header.maxLon) {
            return@withContext null
        }

        val stepLon = (header.maxLon - header.minLon) / header.width
        val stepLat = (header.maxLat - header.minLat) / header.height

        val centerCol = ((lon - header.minLon) / stepLon).toInt()
        val centerRow = ((header.maxLat - lat) / stepLat).toInt()

        // Calcolo raggio in celle per aggregazione ad area
        val latRad = Math.toRadians(lat)
        val metersPerDegLat = 111320.0
        val metersPerDegLon = 111320.0 * cos(latRad)

        val deltaLatDeg = radiusMeters / metersPerDegLat
        val deltaLonDeg = radiusMeters / max(1.0, metersPerDegLon)

        val rowRadius = max(1, (deltaLatDeg / stepLat).roundToInt())
        val colRadius = max(1, (deltaLonDeg / stepLon).roundToInt())

        val validEcmList = mutableListOf<Float>()
        val validHyphalList = mutableListOf<Float>()

        val minR = max(0, centerRow - rowRadius)
        val maxR = min(header.height - 1, centerRow + rowRadius)
        val minC = max(0, centerCol - colRadius)
        val maxC = min(header.width - 1, centerCol + colRadius)

        for (r in minR..maxR) {
            val normR = (r - centerRow).toDouble() / rowRadius
            val normR2 = normR * normR
            val rowOffset = r * header.width

            for (c in minC..maxC) {
                val normC = (c - centerCol).toDouble() / colRadius
                if (normR2 + (normC * normC) <= 1.0) {
                    val idx = rowOffset + c
                    val ecmU8 = region.ecmData[idx].toInt() and 0xFF
                    val hypU8 = region.hyphalData[idx].toInt() and 0xFF

                    if (ecmU8 > 0) {
                        validEcmList.add(ecmU8.toFloat())
                    }
                    if (hypU8 > 0) {
                        validHyphalList.add(hypU8.toFloat() / 20.0f)
                    }
                }
            }
        }

        // Se nessun dato valido nell'area (es. mare aperto)
        if (validEcmList.isEmpty() && validHyphalList.isEmpty()) {
            return@withContext null
        }

        // Usiamo l'80° percentile o il valore massimo locale per catturare la macchia boschiva reale circostante
        val ecmVal = if (validEcmList.isNotEmpty()) {
            validEcmList.sorted()[((validEcmList.size - 1) * 0.8).toInt()]
        } else 0.0f

        val hyphalVal = if (validHyphalList.isNotEmpty()) {
            validHyphalList.sorted()[((validHyphalList.size - 1) * 0.8).toInt()]
        } else 0.0f

        // Normalizzazione punteggi
        val ecmScore = when {
            ecmVal >= 55.0f -> 1.0
            ecmVal >= 40.0f -> 0.9
            ecmVal >= 25.0f -> 0.75
            ecmVal >= 12.0f -> 0.55
            else -> 0.35
        }

        val hyphalScore = when {
            hyphalVal >= 5.5f -> 1.0
            hyphalVal >= 4.0f -> 0.85
            hyphalVal >= 2.5f -> 0.65
            else -> 0.40
        }

        val ecmQualityLabel = when {
            ecmVal >= 55.0f -> "Ideale"
            ecmVal >= 40.0f -> "Promettente"
            ecmVal >= 25.0f -> "Favorevole"
            ecmVal >= 12.0f -> "Moderata"
            else -> "Scarsa"
        }

        val hyphalVitalityLabel = when {
            hyphalVal >= 5.5f -> "Molto attiva"
            hyphalVal >= 4.0f -> "Attiva"
            hyphalVal >= 2.5f -> "Moderata"
            else -> "Scarsa"
        }

        val ecmText = String.format(
            Locale.ITALIAN,
            "Simbiosi EcM: %d specie (%s)",
            ecmVal.toInt(),
            ecmQualityLabel
        )

        val hyphalText = String.format(
            Locale.ITALIAN,
            "Rete Ifale: %.1f m/cm³ (%s)",
            hyphalVal,
            hyphalVitalityLabel
        )

        SpunData(
            ecmRichness = ecmVal,
            hyphalDensity = hyphalVal,
            ecmScore = ecmScore,
            hyphalScore = hyphalScore,
            ecmText = ecmText,
            hyphalText = hyphalText,
            regionCode = region.header.regionCode,
            regionName = region.descriptor.displayName,
            ecmQualityLabel = ecmQualityLabel,
            hyphalVitalityLabel = hyphalVitalityLabel
        )
    }

    /**
     * Restituisce la descrizione della regione correntemente in memoria.
     */
    fun getActiveRegionName(): String? {
        return currentLoadedRegion?.descriptor?.displayName
    }
}
