package com.sus7898.lrrviewer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingModesTest {

    @Test
    fun `direction and continuity flags`() {
        assertTrue(ReadingMode.CONTINUOUS_RTL.isRightToLeft)
        assertTrue(ReadingMode.RTL.isRightToLeft)
        assertFalse(ReadingMode.CONTINUOUS_LTR.isRightToLeft)
        assertTrue(ReadingMode.WEBTOON.isContinuous)
        assertTrue(ReadingMode.CONTINUOUS_LTR.isHorizontalContinuous)
        assertFalse(ReadingMode.WEBTOON.isHorizontalContinuous)
        assertFalse(ReadingMode.VERTICAL.isContinuous)
    }

    @Test
    fun `toggle goes to webtoon from paging and back to the previous choice`() {
        // default RTL, no per-archive override -> webtoon, then back to "follow default" (null)
        assertEquals(ReadingMode.WEBTOON, continuousToggle(current = null, default = ReadingMode.RTL, remembered = null))
        assertNull(continuousToggle(current = ReadingMode.WEBTOON, default = ReadingMode.RTL, remembered = null))
        // a per-archive LTR override is restored
        assertEquals(ReadingMode.LTR, continuousToggle(current = ReadingMode.WEBTOON, default = ReadingMode.RTL, remembered = ReadingMode.LTR))
        // horizontal continuous counts as continuous too
        assertNull(continuousToggle(current = ReadingMode.CONTINUOUS_RTL, default = ReadingMode.RTL, remembered = null))
    }

    @Test
    fun `when the default itself is continuous the way back is RTL paging`() {
        assertEquals(ReadingMode.RTL, continuousToggle(current = null, default = ReadingMode.WEBTOON, remembered = null))
        assertEquals(ReadingMode.RTL, continuousToggle(current = ReadingMode.WEBTOON, default = ReadingMode.WEBTOON, remembered = ReadingMode.CONTINUOUS_LTR))
    }
}
