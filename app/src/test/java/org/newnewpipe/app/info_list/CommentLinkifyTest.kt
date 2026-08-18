package org.newnewpipe.app.info_list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentLinkifyTest {

    @Test
    fun `url semplice viene rilevata`() {
        val links = findCommentLinks("Guarda https://example.com/video ora")
        assertEquals(1, links.size)
        val link = links[0]
        assertEquals("https://example.com/video", link.url)
        assertEquals("https://example.com/video", "Guarda https://example.com/video ora".substring(link.start, link.end))
    }

    @Test
    fun `punteggiatura finale dell url viene tagliata`() {
        val text = "Visita https://example.com."
        val links = findCommentLinks(text)
        assertEquals(1, links.size)
        assertEquals("https://example.com", links[0].url)
    }

    @Test
    fun `url www viene rilevata`() {
        val links = findCommentLinks("vedi www.example.com qui")
        assertEquals(1, links.size)
        assertEquals("www.example.com", links[0].url)
    }

    @Test
    fun `timestamp mm ss viene convertito in internal url`() {
        val text = "Il momento chiave è 12:34"
        val links = findCommentLinks(text)
        assertEquals(1, links.size)
        assertEquals("internal://timestamp/754", links[0].url) // 12*60 + 34
        assertEquals("12:34", text.substring(links[0].start, links[0].end))
    }

    @Test
    fun `timestamp con ore viene convertito in secondi`() {
        val text = "Guarda 1:02:03"
        val links = findCommentLinks(text)
        assertEquals(1, links.size)
        assertEquals("internal://timestamp/3723", links[0].url) // 1*3600 + 2*60 + 3
    }

    @Test
    fun `timestamp dentro un url non genera link separato`() {
        val text = "https://example.com/watch?v=123&t=12:34"
        val links = findCommentLinks(text)
        assertEquals(1, links.size)
        assertEquals("https://example.com/watch?v=123&t=12:34", links[0].url)
    }

    @Test
    fun `url e timestamp convivono ordinati per posizione`() {
        val text = "Prima https://example.com poi 12:34"
        val links = findCommentLinks(text)
        assertEquals(2, links.size)
        assertTrue(links[0].url.startsWith("https://"))
        assertEquals("internal://timestamp/754", links[1].url)
    }

    @Test
    fun `testo senza link produce lista vuota`() {
        assertTrue(findCommentLinks("nessun link qui, solo testo").isEmpty())
        assertTrue(findCommentLinks("").isEmpty())
    }
}
