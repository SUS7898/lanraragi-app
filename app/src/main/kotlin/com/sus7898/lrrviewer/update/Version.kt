package com.sus7898.lrrviewer.update

/** Minimal semantic version: `v1.2.3`, `1.2.3-beta.1`, `1.2` are all accepted. */
data class Version(val major: Int, val minor: Int, val patch: Int, val preRelease: String? = null) : Comparable<Version> {

    override fun compareTo(other: Version): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        // A version without pre-release label is newer than one with (1.0.0 > 1.0.0-rc1).
        return when {
            preRelease == null && other.preRelease == null -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun toString(): String = "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        private val regex = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.\-]+))?(?:\+[0-9A-Za-z.\-]+)?$""")

        fun parse(input: String?): Version? {
            val s = input?.trim()?.removeSuffix("-debug") ?: return null
            val m = regex.matchEntire(s) ?: return null
            return Version(
                major = m.groupValues[1].toIntOrNull() ?: return null,
                minor = m.groupValues[2].toIntOrNull() ?: 0,
                patch = m.groupValues[3].toIntOrNull() ?: 0,
                preRelease = m.groupValues[4].takeIf { it.isNotEmpty() },
            )
        }
    }
}
