package com.sus7898.lrrviewer.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderQueueTest {

    @Test
    fun `next archive is the following non-tankoubon entry of the queue`() {
        val queue = listOf("a", "b", "TANK_x", "c")
        assertEquals("b", ReaderViewModel.nextArchiveIn(queue, "a"))
        assertEquals("c", ReaderViewModel.nextArchiveIn(queue, "b"))
        assertNull(ReaderViewModel.nextArchiveIn(queue, "c"))
    }

    @Test
    fun `no next when the current archive is not in the queue or the queue is empty`() {
        assertNull(ReaderViewModel.nextArchiveIn(listOf("a", "b"), "zzz"))
        assertNull(ReaderViewModel.nextArchiveIn(emptyList(), "a"))
    }
}
