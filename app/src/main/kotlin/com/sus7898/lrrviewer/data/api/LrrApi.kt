package com.sus7898.lrrviewer.data.api

import com.sus7898.lrrviewer.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin client for the LANraragi HTTP API (https://sugoi.gitbook.io/lanraragi/api-documentation).
 * All numeric fields are parsed leniently; see [LenientSerializers.kt].
 */
class LrrApi(
    private val client: OkHttpClient,
    private val settings: StateFlow<AppSettings>,
    private val json: Json = LrrJson.instance,
) {

    // ------------------------------------------------------------------ URL helpers

    val baseUrl: String get() = LrrUrls.normalizeBaseUrl(settings.value.serverUrl)

    private fun base(): HttpUrl =
        LrrUrls.parseBase(settings.value.serverUrl) ?: throw LrrException.NotConfigured()

    private fun api(vararg segments: String): HttpUrl.Builder =
        base().newBuilder().addPathSegment("api").apply { segments.forEach { addPathSegment(it) } }

    private fun thumbSegment(id: String) = if (id.startsWith("TANK_")) "tankoubons" else "archives"

    fun thumbnailUrl(id: String): String =
        runCatching { api(thumbSegment(id), id, "thumbnail").build().toString() }.getOrDefault("")

    fun webReaderUrl(id: String): String =
        runCatching { base().newBuilder().addPathSegment("reader").addQueryParameter("id", id).build().toString() }
            .getOrDefault("")

    fun resolvePages(paths: List<String>): List<String> = paths.map { LrrUrls.resolvePageUrl(baseUrl, it) }

    // ------------------------------------------------------------------ transport

    private suspend fun fetchText(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw mapError(response.code, body)
            body
        }
    }

    private fun mapError(code: Int, body: String): LrrException {
        if (code == 401) return LrrException.Unauthorized()
        val detail = runCatching { json.decodeFromString<OperationResponse>(body).error }.getOrNull()
            ?: body.takeIf { it.isNotBlank() && it.length < 300 }
        return LrrException.HttpError(code, detail)
    }

    private suspend inline fun <reified T> getJson(url: HttpUrl): T =
        json.decodeFromString(fetchText(Request.Builder().url(url).get().build()))

    private suspend fun send(url: HttpUrl, method: String) {
        val body = if (method == "GET") null else ByteArray(0).toRequestBody(null)
        fetchText(Request.Builder().url(url).method(method, body).build())
    }

    private fun decodeArchives(elements: List<JsonElement>): Pair<List<Archive>, Int> {
        var skipped = 0
        val items = elements.mapNotNull { el ->
            runCatching { json.decodeFromJsonElement<Archive>(el) }.getOrElse { skipped++; null }
        }
        return items to skipped
    }

    // ------------------------------------------------------------------ endpoints

    suspend fun info(): ServerInfo = getJson(api("info").build())

    suspend fun search(query: SearchQuery, start: Int): SearchPage {
        val url = api("search").apply {
            if (query.filter.isNotBlank()) addQueryParameter("filter", query.filter)
            if (query.category.isNotBlank()) addQueryParameter("category", query.category)
            addQueryParameter("start", start.toString())
            addQueryParameter("sortby", query.sortBy)
            addQueryParameter("order", query.order)
            addQueryParameter("newonly", query.newOnly.toString())
            addQueryParameter("untaggedonly", query.untaggedOnly.toString())
            addQueryParameter("hidecompleted", query.hideCompleted.toString())
            addQueryParameter("groupby_tanks", query.groupTanks.toString())
        }.build()
        val raw: RawSearchResult = getJson(url)
        val (items, skipped) = decodeArchives(raw.data)
        return SearchPage(items, raw.recordsFiltered, raw.recordsTotal, skipped)
    }

    suspend fun random(query: SearchQuery, count: Int = 1): List<Archive> {
        val url = api("search", "random").apply {
            addQueryParameter("count", count.toString())
            if (query.filter.isNotBlank()) addQueryParameter("filter", query.filter)
            if (query.category.isNotBlank()) addQueryParameter("category", query.category)
            addQueryParameter("newonly", query.newOnly.toString())
            addQueryParameter("untaggedonly", query.untaggedOnly.toString())
            addQueryParameter("groupby_tanks", query.groupTanks.toString())
        }.build()
        val raw: RawSearchResult = getJson(url)
        return decodeArchives(raw.data).first
    }

    suspend fun metadata(id: String): Archive = getJson(api("archives", id, "metadata").build())

    /** Page list; also triggers server-side extraction when needed. */
    suspend fun files(id: String, force: Boolean = false): FilesResponse =
        getJson(api("archives", id, "files").apply { if (force) addQueryParameter("force", "true") }.build())

    suspend fun minionJob(jobId: Int): MinionJob = getJson(api("minion", jobId.toString()).build())

    suspend fun categories(): List<Category> = getJson(api("categories").build())

    /** [page] is 1-based, as LANraragi counts pages read. */
    suspend fun updateProgress(id: String, page: Int) =
        send(api("archives", id, "progress", page.coerceAtLeast(1).toString()).build(), "PUT")

    suspend fun clearNew(id: String) = send(api("archives", id, "isnew").build(), "DELETE")

    suspend fun setNew(id: String) = send(api("archives", id, "isnew").build(), "PUT")

    suspend fun tankoubon(id: String): Pair<TankoubonFull, List<Archive>> {
        val resp: TankoubonFullResponse = getJson(api("tankoubons", id, "full").build())
        val tank = resp.result ?: throw LrrException.HttpError(200, "탄코본 정보가 비어 있습니다.")
        var archives = decodeArchives(tank.full_data).first
        if (archives.isEmpty() && tank.archives.isNotEmpty()) {
            archives = tank.archives.mapNotNull { aid -> runCatching { metadata(aid) }.getOrNull() }
        }
        return tank to archives
    }

    companion object {
        /** One-off connection test with explicit credentials (used before settings are saved). */
        suspend fun probe(serverUrl: String, apiKey: String): ServerInfo = withContext(Dispatchers.IO) {
            val base = LrrUrls.parseBase(serverUrl)
                ?: throw IllegalArgumentException("서버 주소 형식이 올바르지 않습니다. 예: http://192.168.0.10:3000")
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url(base.newBuilder().addPathSegment("api").addPathSegment("info").build())
                .apply { if (apiKey.isNotBlank()) header("Authorization", LrrAuthInterceptor.bearerValue(apiKey)) }
                .build()
            client.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (r.code == 401) throw LrrException.Unauthorized()
                if (!r.isSuccessful) throw LrrException.HttpError(r.code, body.take(200))
                LrrJson.instance.decodeFromString<ServerInfo>(body)
            }
        }
    }
}
