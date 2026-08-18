package org.newnewpipe.app.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class SyncCipherTest {

    private val payload = """
        {"subscriptions":[{"service_id":0,"url":"https://youtube.com/@a","name":"A","updated_at_ms":1724000000000}]}
    """.trimIndent().toByteArray(Charsets.UTF_8)

    @Test
    fun roundtrip_encryptThenDecrypt_returnsPlaintext() {
        val cipher = SyncCipher("correct horse battery staple")

        val blob = cipher.encrypt(payload)

        assertArrayEquals(payload, cipher.decrypt(blob))
    }

    @Test
    fun wrongPassphrase_failsDecryption() {
        val blob = SyncCipher("passphrase-a").encrypt(payload)

        assertThrows(AEADBadTagException::class.java) {
            SyncCipher("passphrase-b").decrypt(blob)
        }
    }

    @Test
    fun tamperedBlob_failsDecryption() {
        val cipher = SyncCipher("passphrase")
        val blob = cipher.encrypt(payload)
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()

        assertThrows(AEADBadTagException::class.java) {
            cipher.decrypt(blob)
        }
    }

    @Test
    fun nonBlobInput_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncCipher("passphrase").decrypt("plain text".toByteArray())
        }
    }

    @Test
    fun samePayloadTwice_producesDifferentBlobs() {
        val cipher = SyncCipher("passphrase")

        val first = cipher.encrypt(payload)
        val second = cipher.encrypt(payload)

        assertFalse(first.contentEquals(second))
        // But both decrypt to the same plaintext.
        assertTrue(cipher.decrypt(first).contentEquals(cipher.decrypt(second)))
    }

    @Test
    fun emptyPayload_roundtrips() {
        val cipher = SyncCipher("passphrase")

        assertArrayEquals(ByteArray(0), cipher.decrypt(cipher.encrypt(ByteArray(0))))
    }
}
