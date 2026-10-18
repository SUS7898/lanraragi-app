package com.sus7898.lrrviewer.ui.library

import com.sus7898.lrrviewer.data.api.TagStat

/**
 * Tag autocompletion for the library search box. LANraragi splits the filter on commas (spaces belong to the
 * term), a leading `-` excludes and a trailing `$` makes a tag exact, so only the last comma-separated term
 * is completed and replaced.
 */
object SearchSuggest {

    /** The term being typed: text after the last comma, without the exclusion sign or quotes. */
    fun currentTerm(text: String): String =
        text.substringAfterLast(',').trimStart().removePrefix("-").trim().trim('"').removeSuffix("$")

    fun suggest(stats: List<TagStat>, text: String, limit: Int = 8): List<TagStat> {
        val term = currentTerm(text).lowercase()
        if (term.length < 2) return emptyList()
        val ns = term.substringBefore(':', missingDelimiterValue = "").takeIf { ':' in term }
        val value = if (ns != null) term.substringAfter(':') else term
        return stats.asSequence()
            .filter { s ->
                val text0 = s.text.lowercase()
                if (ns != null) s.namespace.lowercase() == ns && text0.startsWith(value)
                else text0.contains(value) || s.namespace.lowercase().startsWith(value)
            }
            .filter { s -> s.raw.lowercase() != term }
            .sortedWith(compareByDescending<TagStat> { it.text.lowercase().startsWith(value) }.thenByDescending { it.weight })
            .take(limit)
            .toList()
    }

    /** Replaces the term being typed with the exact tag and leaves the cursor ready for the next term. */
    fun apply(text: String, tag: TagStat): String {
        val comma = text.lastIndexOf(',')
        val head = if (comma >= 0) text.substring(0, comma + 1) + " " else ""
        val current = text.substring(comma + 1).trimStart()
        val neg = if (current.startsWith("-")) "-" else ""
        return "$head$neg${tag.raw}$, "
    }
}
