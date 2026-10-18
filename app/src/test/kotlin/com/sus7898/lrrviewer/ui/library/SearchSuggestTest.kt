package com.sus7898.lrrviewer.ui.library

import com.sus7898.lrrviewer.data.api.TagStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestTest {

    private val stats = listOf(
        TagStat("artist", "foo bar", 30),
        TagStat("artist", "foobaz", 5),
        TagStat("character", "jeanne alter", 12),
        TagStat("", "full color", 40),
        TagStat("series", "food wars", 8),
    )

    @Test
    fun `current term is the last comma separated piece without exclusion or exact markers`() {
        assertEquals("art", SearchSuggest.currentTerm("language:korean, -art"))
        assertEquals("artist:foo", SearchSuggest.currentTerm("artist:foo$"))
        assertEquals("", SearchSuggest.currentTerm("a, "))
    }

    @Test
    fun `namespace prefix restricts and prefix matches rank before weight`() {
        assertEquals(listOf("artist:foo bar", "artist:foobaz"), SearchSuggest.suggest(stats, "artist:foo").map { it.raw })
        val plain = SearchSuggest.suggest(stats, "foo").map { it.raw }
        assertEquals(listOf("artist:foo bar", "series:food wars", "artist:foobaz"), plain)
        assertTrue(SearchSuggest.suggest(stats, "f").isEmpty())
    }

    @Test
    fun `apply replaces only the last term and keeps the exclusion sign`() {
        val tag = TagStat("artist", "foo bar", 1)
        assertEquals("artist:foo bar$, ", SearchSuggest.apply("foo", tag))
        assertEquals("language:korean, -artist:foo bar$, ", SearchSuggest.apply("language:korean, -fo", tag))
    }
}
