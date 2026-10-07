package com.sus7898.lrrviewer.update

import java.security.MessageDigest

object Checksums {

    private val hexRegex = Regex("^[0-9a-fA-F]{64}$")

    /**
     * Finds the SHA-256 for [fileName] in a `sha256sum`-style listing
     * ("<hex>  <name>" per line) or a bare single-hash `.sha256` file.
     */
    fun findSha256(listing: String, fileName: String): String? {
        val wanted = fileName.substringAfterLast('/').trim()
        val lines = listing.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            val parts = line.split(Regex("\\s+"), limit = 2)
            val hash = parts[0].removePrefix("sha256:")
            if (!hexRegex.matches(hash)) continue
            if (parts.size == 1) return hash.lowercase()
            val name = parts[1].trim().removePrefix("*").substringAfterLast('/')
            if (name.equals(wanted, ignoreCase = true)) return hash.lowercase()
        }
        return null
    }

    /** Parses the GitHub asset `digest` field (`sha256:<hex>`). */
    fun fromDigestField(digest: String?): String? {
        val d = digest?.trim() ?: return null
        val hex = d.substringAfter("sha256:", missingDelimiterValue = "")
        return hex.takeIf { hexRegex.matches(it) }?.lowercase()
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
