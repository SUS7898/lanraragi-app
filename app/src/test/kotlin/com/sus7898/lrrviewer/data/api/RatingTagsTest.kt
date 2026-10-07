package com.sus7898.lrrviewer.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatingTagsTest {

    @Test
    fun `rating is read from the rating namespace and ignored when out of range`() {
        assertEquals(4, Archive.ratingFromTags("artist:foo, rating:4, language:korean"))
        assertEquals(5, Archive.ratingFromTags("rating: 5"))
        assertNull(Archive.ratingFromTags("artist:foo"))
        assertNull(Archive.ratingFromTags("rating:9"))
        assertNull(Archive.ratingFromTags("rating:"))
        assertNull(Archive.ratingFromTags(null))
    }

    @Test
    fun `tagsWithRating replaces or removes the rating tag and keeps everything else`() {
        assertEquals("artist:foo, language:korean, rating:3", Archive.tagsWithRating("artist:foo, rating:5, language:korean", 3))
        assertEquals("artist:foo, language:korean", Archive.tagsWithRating("artist:foo, rating:5, language:korean", null))
        assertEquals("rating:2", Archive.tagsWithRating("", 2))
        assertEquals("artist:foo", Archive.tagsWithRating("artist:foo, rating:1, rating:2", 0))
    }

    @Test
    fun `archive exposes rating and sort labels fall back to the namespace`() {
        val a = Archive(arcid = "x", tags = "rating:2, artist:bar")
        assertEquals(2, a.rating)
        assertEquals("평점", SearchQuery.sortLabel("rating"))
        assertEquals("character", SearchQuery.sortLabel("character"))
    }
}
