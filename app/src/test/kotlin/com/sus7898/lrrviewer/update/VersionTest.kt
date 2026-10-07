package com.sus7898.lrrviewer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun `parses tags and version names`() {
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(1, 2, 0), Version.parse("1.2"))
        assertEquals(Version(0, 1, 0), Version.parse("0.1.0-debug"))
        assertEquals(Version(2, 0, 0, "rc.1"), Version.parse("v2.0.0-rc.1"))
        assertNull(Version.parse("latest"))
        assertNull(Version.parse(null))
    }

    @Test
    fun `ordering`() {
        assertTrue(Version.parse("1.0.1")!! > Version.parse("1.0.0")!!)
        assertTrue(Version.parse("1.1.0")!! > Version.parse("1.0.9")!!)
        assertTrue(Version.parse("2.0.0")!! > Version.parse("1.99.99")!!)
        assertTrue("release is newer than its pre-release", Version.parse("1.0.0")!! > Version.parse("1.0.0-rc1")!!)
        assertEquals(0, Version.parse("v1.2.3")!!.compareTo(Version.parse("1.2.3")!!))
    }
}
