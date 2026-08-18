package org.newnewpipe.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun timestampToMs_centiseconds() {
        // [mm:ss.xx] — la frazione è in centesimi (convenzione LRC).
        assertEquals(27_930L, LrcParser.timestampToMs("[00:27.93]"))
        assertEquals(61_500L, LrcParser.timestampToMs("[01:01.50]"))
    }

    @Test
    fun timestampToMs_secondsOnly() {
        assertEquals(1_000L, LrcParser.timestampToMs("[00:01]"))
        assertEquals(90_000L, LrcParser.timestampToMs("[01:30]"))
    }

    @Test
    fun timestampToMs_singleDigitFraction() {
        assertEquals(1_500L, LrcParser.timestampToMs("[00:01.5]"))
        assertEquals(1_050L, LrcParser.timestampToMs("[00:01.05]"))
    }

    @Test
    fun timestampToMs_milliseconds() {
        assertEquals(1_234L, LrcParser.timestampToMs("[00:01.234]"))
    }

    @Test
    fun timestampToMs_invalidSeconds_returnsNull() {
        assertNull(LrcParser.timestampToMs("[00:75]"))
        assertNull(LrcParser.timestampToMs("not a tag"))
        assertNull(LrcParser.timestampToMs("[ti:Some Title]"))
    }

    @Test
    fun parse_ordersLinesByTimestamp() {
        val lrc = "[00:30.88] Watch the sun rise\n" +
            "[00:27.93] Listen to the wind blow\n" +
            "[00:34.62] Run in the shadows\n"
        val lines = LrcParser.parse(lrc)

        assertEquals(3, lines.size)
        assertEquals(27_930L, lines[0].timestampMs)
        assertEquals("Listen to the wind blow", lines[0].text)
        assertEquals(30_880L, lines[1].timestampMs)
        assertEquals(34_620L, lines[2].timestampMs)
    }

    @Test
    fun parse_multipleTagsOnSameLine_createsOneLinePerTag() {
        val lines = LrcParser.parse("[00:12.00][00:30.00]Chorus")

        assertEquals(2, lines.size)
        assertEquals(12_000L, lines[0].timestampMs)
        assertEquals(30_000L, lines[1].timestampMs)
        assertEquals("Chorus", lines[0].text)
        assertEquals("Chorus", lines[1].text)
    }

    @Test
    fun parse_skipsMetadataAndUnsyncedLines() {
        val lrc = "[ti:The Chain]\n" +
            "[ar:Fleetwood Mac]\n" +
            "[al:Rumours]\n" +
            "[00:27.93] Listen to the wind blow\n" +
            "Plain text without timestamp\n" +
            "[offset:+500]\n" +
            "[00:30.88] Watch the sun rise\n"
        val lines = LrcParser.parse(lrc)

        assertEquals(2, lines.size)
        assertEquals("Listen to the wind blow", lines[0].text)
        assertEquals("Watch the sun rise", lines[1].text)
    }

    @Test
    fun parse_emptyAndBlankInput_returnsEmptyList() {
        assertTrue(LrcParser.parse("").isEmpty())
        assertTrue(LrcParser.parse("\n\n   \n").isEmpty())
    }
}
