package github.naturewhisp.myco.network

import github.naturewhisp.myco.model.ElevationResponse
import github.naturewhisp.myco.model.GeocodeResult
import github.naturewhisp.myco.model.OverpassResponse
import github.naturewhisp.myco.model.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfaccia Retrofit per il servizio di geocodifica diretta e inversa OpenStreetMap Nominatim.
 */
interface GeocodingService {
    /**
     * Esegue una ricerca geografica testuale per toponimo o indirizzo.
     *
     * @param query Testo di ricerca immesso dall'utente.
     * @param format Formato di risposta richiesto (default "json").
     * @param limit Numero massimo di risultati restituiti (default 1).
     * @param addressDetails Flag per includere i dettagli della gerarchia amministrativa (1 = sì).
     * @param lang Codice ISO della lingua preferita per i toponimi (default "it").
     * @return Lista dei risultati di geocodifica [GeocodeResult].
     */
    @GET("search")
    suspend fun searchLocation(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1,
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("accept-language") lang: String = "it"
    ): List<GeocodeResult>

    /**
     * Risolve le coordinate geografiche WGS84 nel toponimo corrispondente (reverse geocoding).
     *
     * @param lat Latitudine in gradi decimali.
     * @param lon Longitudine in gradi decimali.
     * @param format Formato di risposta (default "json").
     * @param zoom Livello di dettaglio toponomastico (default 14 = frazione/comune).
     * @param addressDetails Flag per includere i dettagli amministrativi.
     * @param lang Codice lingua per il toponimo (default "it").
     * @return [GeocodeResult] con toponimo e gerarchia amministrativa.
     */
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("zoom") zoom: Int = 14,
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("accept-language") lang: String = "it"
    ): GeocodeResult
}

/**
 * Interfaccia Retrofit per l'API meteorologica e altimetrica Open-Meteo.
 */
interface WeatherService {
    /**
     * Recupera le serie temporali meteorologiche (passato 14 giorni e previsione fino a 11 giorni).
     *
     * @param latitude Latitudine in gradi decimali.
     * @param longitude Longitudine in gradi decimali.
     * @param pastDays Giorni di archivio storico da recuperare (default 14).
     * @param forecastDays Giorni di previsione futura da recuperare (default 11).
     * @param hourly Variabili orarie richieste separate da virgola.
     * @param daily Parametri giornalieri aggregati richiesti (default "weathercode").
     * @param timezone Fuso orario ("auto" per rilevamento geografico automatico).
     * @return [WeatherResponse] contenente serie temporali orarie e giornaliere.
     */
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("past_days") pastDays: Int = 14,
        @Query("forecast_days") forecastDays: Int = 11,
        @Query("hourly") hourly: String = "temperature_2m,relativehumidity_2m,precipitation",
        @Query("daily") daily: String = "weathercode",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse

    /**
     * Recupera l'elevazione in metri s.l.m. per un elenco batch di coordinate geografiche.
     *
     * @param latitude Stringa di latitudini separate da virgola (es. per matrice DEM 5 punti).
     * @param longitude Stringa di longitudini separate da virgola.
     * @return [ElevationResponse] contenente le quote altimetriche corrispondenti.
     */
    @GET("v1/elevation")
    suspend fun getElevation(
        @Query("latitude") latitude: String,
        @Query("longitude") longitude: String
    ): ElevationResponse
}

/**
 * Interfaccia Retrofit per l'interprete di query QL di OpenStreetMap Overpass.
 */
interface OverpassService {
    /**
     * Esegue una query Overpass QL per estrarre elementi vettoriali (boschi, essenze arboree).
     *
     * @param query Stringa della query Overpass QL (formato `[out:json];...;out body;`).
     * @return [OverpassResponse] contenente gli elementi OSM trovati.
     */
    @GET("api/interpreter")
    suspend fun queryOverpass(
        @Query("data") query: String
    ): OverpassResponse
}
