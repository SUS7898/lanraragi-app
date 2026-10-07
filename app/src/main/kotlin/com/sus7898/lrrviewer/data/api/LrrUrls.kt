package com.sus7898.lrrviewer.data.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object LrrUrls {

    /**
     * Cleans user input into a base URL: trims whitespace, adds `http://` when no scheme is
     * given, removes a trailing slash and anything after `#`/`?`. Returns "" for blank input.
     */
    fun normalizeBaseUrl(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return ""
        s = s.substringBefore('#').substringBefore('?')
        if (!s.contains("://")) s = "http://$s"
        // Lower-case the scheme only.
        val schemeEnd = s.indexOf("://")
        s = s.substring(0, schemeEnd).lowercase() + s.substring(schemeEnd)
        return s.trimEnd('/')
    }

    fun parseBase(input: String): HttpUrl? = normalizeBaseUrl(input).takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()

    /**
     * Converts a page path returned by `/api/archives/:id/files` into an absolute URL.
     *
     * The server returns paths like `./api/archives/<id>/page?path=00.jpg`. When LANraragi
     * runs behind a reverse proxy sub-path (e.g. `https://nas/lrr`), the returned path already
     * contains that sub-path (`/lrr/api/...`), so it is stripped before being appended again.
     */
    fun resolvePageUrl(baseUrl: String, pagePath: String): String {
        val raw = pagePath.trim()
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) return raw
        val base = normalizeBaseUrl(baseUrl)
        var p = raw.removePrefix(".")
        if (!p.startsWith("/")) p = "/$p"
        val basePath = base.toHttpUrlOrNull()?.encodedPath?.trimEnd('/').orEmpty()
        if (basePath.isNotEmpty() && (p == basePath || p.startsWith("$basePath/"))) {
            p = p.removePrefix(basePath)
        }
        return base + p
    }

    fun isSameServer(a: HttpUrl, b: HttpUrl): Boolean =
        a.scheme.equals(b.scheme, ignoreCase = true) && a.host.equals(b.host, ignoreCase = true) && a.port == b.port
}
