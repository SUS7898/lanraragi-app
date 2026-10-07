package com.sus7898.lrrviewer.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object LrrJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }
}

data class Tag(val namespace: String, val value: String) {
    val raw: String get() = if (namespace.isEmpty()) value else "$namespace:$value"
    override fun toString(): String = raw
}

data class TocEntry(val name: String, val page: Int)

@Serializable
data class Archive(
    val arcid: String,
    @Serializable(with = LenientStringSerializer::class) val title: String = "",
    @Serializable(with = LenientStringSerializer::class) val filename: String = "",
    val tags: String? = null,
    val summary: String? = null,
    @Serializable(with = LenientBooleanSerializer::class) val isnew: Boolean = false,
    val extension: String? = null,
    @Serializable(with = LenientIntSerializer::class) val progress: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val pagecount: Int = 0,
    @Serializable(with = LenientLongSerializer::class) val lastreadtime: Long = 0L,
    @Serializable(with = LenientLongSerializer::class) val size: Long = 0L,
    val toc: JsonElement? = null,
) {
    val isTankoubon: Boolean get() = arcid.startsWith("TANK_")

    val tagList: List<Tag>
        get() = parseTags(tags)

    /** Unix seconds from the `date_added` tag, or null when absent/garbled (never throws). */
    val dateAdded: Long?
        get() = tagList.firstOrNull { it.namespace == "date_added" }?.value?.trim()?.toLongOrNull()

    val isCompleted: Boolean get() = pagecount > 0 && progress >= pagecount

    val tocEntries: List<TocEntry>
        get() = (toc as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val page = o["page"]?.jsonPrimitive?.intOrNull
                ?: o["page"]?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull()
                ?: return@mapNotNull null
            TocEntry(name, page)
        }?.sortedBy { it.page } ?: emptyList()

    companion object {
        fun parseTags(tags: String?): List<Tag> =
            tags.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { raw ->
                val idx = raw.indexOf(':')
                if (idx > 0) Tag(raw.substring(0, idx).trim(), raw.substring(idx + 1).trim()) else Tag("", raw)
            }
    }
}

/** Raw search payload; items are decoded one-by-one so a single malformed entry cannot break the list. */
@Serializable
data class RawSearchResult(
    val data: List<JsonElement> = emptyList(),
    @Serializable(with = LenientIntSerializer::class) val draw: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val recordsFiltered: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val recordsTotal: Int = 0,
)

data class SearchPage(val items: List<Archive>, val filtered: Int, val total: Int, val skipped: Int)

@Serializable
data class ServerInfo(
    @Serializable(with = LenientStringSerializer::class) val name: String = "LANraragi",
    @Serializable(with = LenientStringSerializer::class) val motd: String = "",
    @Serializable(with = LenientStringSerializer::class) val version: String = "",
    @Serializable(with = LenientStringSerializer::class) val version_name: String = "",
    @Serializable(with = LenientBooleanSerializer::class) val has_password: Boolean = false,
    @Serializable(with = LenientBooleanSerializer::class) val nofun_mode: Boolean = false,
    @Serializable(with = LenientIntSerializer::class) val archives_per_page: Int = 100,
    @Serializable(with = LenientBooleanSerializer::class) val server_resizes_images: Boolean = false,
    @Serializable(with = LenientBooleanSerializer::class) val server_tracks_progress: Boolean = false,
    @Serializable(with = LenientBooleanSerializer::class) val authenticated_progress: Boolean = false,
    @Serializable(with = LenientIntSerializer::class) val total_archives: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val total_pages_read: Int = 0,
)

@Serializable
data class FilesResponse(
    val pages: List<String> = emptyList(),
    @Serializable(with = LenientIntSerializer::class) val job: Int = -1,
)

@Serializable
data class MinionJob(
    @Serializable(with = LenientStringSerializer::class) val state: String = "",
    @Serializable(with = LenientStringSerializer::class) val task: String = "",
    val error: String? = null,
)

@Serializable
data class Category(
    val id: String,
    @Serializable(with = LenientStringSerializer::class) val name: String = "",
    @Serializable(with = LenientBooleanSerializer::class) val pinned: Boolean = false,
    val search: String? = null,
    val archives: List<String> = emptyList(),
) {
    val isDynamic: Boolean get() = !search.isNullOrBlank()
}

@Serializable
data class TankoubonFullResponse(val result: TankoubonFull? = null)

@Serializable
data class TankoubonFull(
    @Serializable(with = LenientStringSerializer::class) val id: String = "",
    @Serializable(with = LenientStringSerializer::class) val name: String = "",
    val summary: String? = null,
    val tags: String? = null,
    val archives: List<String> = emptyList(),
    val full_data: List<JsonElement> = emptyList(),
)

@Serializable
data class OperationResponse(
    @Serializable(with = LenientStringSerializer::class) val operation: String = "",
    @Serializable(with = LenientBooleanSerializer::class) val success: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

/** Parameters of `GET /api/search`. */
data class SearchQuery(
    val filter: String = "",
    val category: String = "",
    val sortBy: String = "date_added",
    val order: String = "desc",
    val newOnly: Boolean = false,
    val untaggedOnly: Boolean = false,
    val hideCompleted: Boolean = false,
    val groupTanks: Boolean = true,
) {
    companion object {
        val SORT_OPTIONS: List<Pair<String, String>> = listOf(
            "date_added" to "추가일",
            "title" to "제목",
            "lastread" to "최근 읽음",
        )
    }
}
