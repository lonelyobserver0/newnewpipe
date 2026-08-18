package org.newnewpipe.app.watchtogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests of the room code generator (plan 022-S16): codes must have the
 * requested length and only contain unambiguous characters.
 */
class WatchTogetherRoomCodeTest {

    @Test
    fun `generates a four character code by default`() {
        val code = WatchTogetherRoomCode.generate()
        assertEquals(4, code.length)
    }

    @Test
    fun `respects the requested length`() {
        assertEquals(6, WatchTogetherRoomCode.generate(6).length)
        assertEquals(2, WatchTogetherRoomCode.generate(2).length)
    }

    @Test
    fun `only uses unambiguous characters`() {
        // no 0/O, 1/I/L — easy to dictate over the phone
        val allowed = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        repeat(200) {
            val code = WatchTogetherRoomCode.generate(8)
            assertTrue("code $code contains an ambiguous char", code.all { it in allowed })
        }
    }

    @Test
    fun `is deterministic with a seeded random`() {
        val a = WatchTogetherRoomCode.generate(4, Random(42))
        val b = WatchTogetherRoomCode.generate(4, Random(42))
        assertEquals(a, b)
    }
}
