package org.newnewpipe.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LyricsIndexTest {

    private fun indexOf(vararg timestamps: Long): LyricsIndex =
        LyricsIndex(timestamps.map { LrcLine(it, "riga $it") })

    @Test
    fun emptyLines_alwaysMinusOne() {
        val index = LyricsIndex(emptyList())
        assertEquals(-1, index.lineIndexAt(0L))
        assertEquals(-1, index.lineIndexAt(99_000L))
        assertEquals(0, index.size)
    }

    @Test
    fun singleLine_exactAndAfter() {
        val index = indexOf(5_000L)
        assertEquals(0, index.lineIndexAt(5_000L))
        assertEquals(0, index.lineIndexAt(5_001L))
        assertEquals(0, index.lineIndexAt(999_999L))
    }

    @Test
    fun singleLine_beforeStart() {
        val index = indexOf(5_000L)
        assertEquals(-1, index.lineIndexAt(0L))
        assertEquals(-1, index.lineIndexAt(4_999L))
    }

    @Test
    fun positionBetweenLines_returnsPrevious() {
        val index = indexOf(0L, 10_000L, 20_000L)
        assertEquals(0, index.lineIndexAt(0L))
        assertEquals(0, index.lineIndexAt(9_999L))
        assertEquals(1, index.lineIndexAt(10_000L))
        assertEquals(1, index.lineIndexAt(19_999L))
        assertEquals(2, index.lineIndexAt(20_000L))
    }

    @Test
    fun positionAfterLastLine_returnsLast() {
        val index = indexOf(1_000L, 2_000L, 3_000L)
        assertEquals(2, index.lineIndexAt(3_000L))
        assertEquals(2, index.lineIndexAt(300_000L))
        assertEquals(2, index.lineIndexAt(Long.MAX_VALUE))
    }

    @Test
    fun exactTimestamp_returnsThatLine() {
        val index = indexOf(100L, 200L, 300L)
        assertEquals(0, index.lineIndexAt(100L))
        assertEquals(1, index.lineIndexAt(200L))
        assertEquals(2, index.lineIndexAt(300L))
    }

    @Test
    fun duplicateTimestamps_lastWins() {
        val index = indexOf(1_000L, 1_000L, 2_000L)
        // La ricerca binaria restituisce l'ultima riga con lo stesso timestamp.
        assertEquals(1, index.lineIndexAt(1_000L))
        assertEquals(2, index.lineIndexAt(2_000L))
    }

    @Test
    fun unsortedLines_throw() {
        assertThrows(IllegalArgumentException::class.java) {
            LyricsIndex(listOf(LrcLine(2_000L, "a"), LrcLine(1_000L, "b")))
        }
    }

    @Test
    fun worksWithLrcParserOutput() {
        val lrc = """
            [00:05.00]Intro
            [00:10.00]Prima strofa
            [00:15.00]Seconda strofa
            [00:20.00]Ritornello
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        val index = LyricsIndex(lines)
        assertEquals(4, index.size)
        assertEquals(-1, index.lineIndexAt(4_999L))
        assertEquals(0, index.lineIndexAt(5_000L))
        assertEquals(2, index.lineIndexAt(15_000L))
        assertEquals(3, index.lineIndexAt(300_000L))
    }
}
