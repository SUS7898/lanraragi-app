package com.sus7898.lrrviewer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecksumsTest {

    private val hash = "a".repeat(64)

    @Test
    fun `finds hash in sha256sum listing`() {
        val listing = """
            ${"b".repeat(64)}  other.txt
            $hash  lrr-viewer-v1.2.3.apk
        """.trimIndent()
        assertEquals(hash, Checksums.findSha256(listing, "lrr-viewer-v1.2.3.apk"))
        assertEquals(hash, Checksums.findSha256(listing, "dist/lrr-viewer-v1.2.3.apk"))
        assertNull(Checksums.findSha256(listing, "missing.apk"))
    }

    @Test
    fun `bare hash file`() {
        assertEquals(hash, Checksums.findSha256("$hash\n", "whatever.apk"))
        assertEquals(hash, Checksums.findSha256("${hash.uppercase()} *whatever.apk", "whatever.apk"))
    }

    @Test
    fun `github digest field`() {
        assertEquals(hash, Checksums.fromDigestField("sha256:$hash"))
        assertNull(Checksums.fromDigestField("md5:abc"))
        assertNull(Checksums.fromDigestField(null))
    }

    @Test
    fun `sha256 of bytes`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Checksums.sha256Hex(ByteArray(0)))
    }
}
