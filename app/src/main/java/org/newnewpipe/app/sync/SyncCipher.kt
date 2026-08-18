package org.newnewpipe.app.sync

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional payload encryption for the sync files (plan 022 S7, decision D-4:
 * "cifratura blob opzionale con passphrase").
 *
 * When the user sets a passphrase in the sync settings, the payloads stored
 * on the WebDAV server are encrypted with AES-256-GCM; the key is derived
 * from the passphrase with PBKDF2-HMAC-SHA256 (100k iterations). The blob
 * layout is:
 *
 * ```
 * [magic "NNS1" (4)] [salt (16)] [iv (12)] [AES-GCM ciphertext]
 * ```
 *
 * The salt is random per encryption, so two encryptions of the same payload
 * differ and a wrong passphrase fails decryption (AEADBadTagException).
 * Pure JVM: no Android framework, fully covered by unit tests.
 */
class SyncCipher(private val passphrase: String) {

    /** Encrypts [plaintext] with a fresh random salt and IV. */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(salt), GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC + salt + iv + ciphertext
    }

    /**
     * Decrypts a blob produced by [encrypt]. Throws
     * [javax.crypto.AEADBadTagException] when the passphrase is wrong.
     */
    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size >= HEADER_BYTES && blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "not a NewNewPipe sync blob (missing header)"
        }
        var offset = MAGIC.size
        val salt = blob.copyOfRange(offset, offset + SALT_BYTES)
        offset += SALT_BYTES
        val iv = blob.copyOfRange(offset, offset + IV_BYTES)
        offset += IV_BYTES
        val ciphertext = blob.copyOfRange(offset, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(salt), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val generated = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        // The factory returns a key with the PBE algorithm name; AES/GCM
        // requires a key declared as AES.
        return SecretKeySpec(generated.encoded, "AES")
    }

    companion object {
        private val MAGIC = "NNS1".toByteArray(Charsets.US_ASCII)
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_BITS = 256
        private const val ITERATIONS = 100_000
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val HEADER_BYTES = MAGIC.size + SALT_BYTES + IV_BYTES
    }
}
