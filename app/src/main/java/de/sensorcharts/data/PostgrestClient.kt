package de.sensorcharts.data

import de.sensorcharts.domain.Resolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class ApiException(message: String) : IOException(message)

/** Minimal read-only client for the PostgREST endpoints created by db/01_sensor_charts.sql. */
class PostgrestClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Checks URL and API key by reading one row of the series catalog. */
    suspend fun testConnection(settings: ApiSettings) {
        get(settings, url(settings, "event_series") { addQueryParameter("limit", "1") })
    }

    /** All series (source + name) that have data. */
    suspend fun availableSeries(settings: ApiSettings): List<AvailableSeries> {
        val rows = get(
            settings,
            url(settings, "event_series") {
                addQueryParameter("select", "source,name")
                addQueryParameter("order", "source,name")
            },
        )
        return rows.mapNotNull { row ->
            val source = row.string("source") ?: return@mapNotNull null
            val name = row.string("name") ?: return@mapNotNull null
            AvailableSeries(source, name)
        }
    }

    /** Values of one series in [from, to), ordered by time. Pages through the result set. */
    suspend fun fetchSeries(
        settings: ApiSettings,
        resolution: Resolution,
        source: String,
        name: String,
        from: Instant,
        to: Instant,
    ): List<DataPoint> {
        val result = ArrayList<DataPoint>()
        var offset = 0
        // PostgREST may cap the page size (db-max-rows), so stop only on an empty page.
        while (true) {
            val rows = get(
                settings,
                url(settings, resolution.resource) {
                    addQueryParameter("select", "t:${resolution.timeColumn},v:${resolution.valueColumn}")
                    addQueryParameter("source", "eq.$source")
                    addQueryParameter("name", "eq.$name")
                    addQueryParameter(resolution.timeColumn, "gte.$from")
                    addQueryParameter(resolution.timeColumn, "lt.$to")
                    addQueryParameter("order", "${resolution.timeColumn}.asc")
                    addQueryParameter("limit", PAGE_SIZE.toString())
                    addQueryParameter("offset", offset.toString())
                },
            )
            if (rows.isEmpty()) break
            rows.mapNotNullTo(result) { row ->
                val time = row.string("t") ?: return@mapNotNullTo null
                val value = (row["v"]?.jsonPrimitive?.doubleOrNull) ?: return@mapNotNullTo null
                DataPoint(OffsetDateTime.parse(time).toInstant().toEpochMilli(), value)
            }
            offset += rows.size
            if (offset >= MAX_ROWS) break
        }
        return result
    }

    private fun url(settings: ApiSettings, resource: String, block: HttpUrl.Builder.() -> Unit): HttpUrl =
        settings.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegment(resource)
            .apply(block)
            .build()

    private suspend fun get(settings: ApiSettings, url: HttpUrl): List<JsonObject> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    (json.parseToJsonElement(body) as JsonObject).string("message")
                }.getOrNull()
                throw ApiException("HTTP ${response.code}${detail?.let { ": $it" } ?: ""}")
            }
            val array = json.parseToJsonElement(body) as? JsonArray
                ?: throw ApiException("Unerwartete Antwort von ${url.encodedPath}")
            array.mapNotNull { it as? JsonObject }
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        const val PAGE_SIZE = 5000
        const val MAX_ROWS = 200_000
    }
}
