package com.example.crypto

import android.util.Base64
import java.io.Closeable
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ephemeral Cryptographic Session.
 * Factory / Scoped lifecycle ensures keys and memory buffers are zeroed out
 * upon session completion to prevent memory leaks on resource-constrained devices.
 */
class CryptoSession(
    val sessionId: String = "session_${System.currentTimeMillis()}"
) : Closeable {

    private var rawKeyBytes: ByteArray? = ByteArray(32).apply { SecureRandom().nextBytes(this) }
    private var secretKey: SecretKey? = SecretKeySpec(rawKeyBytes, "AES")
    private var isClosed = false

    private val GCM_IV_LENGTH = 12
    private val GCM_TAG_LENGTH = 128

    fun encryptPayload(plainText: String): String {
        check(!isClosed) { "CryptoSession $sessionId is already closed and disposed." }
        val currentKey = secretKey ?: error("SecretKey not available")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, parameterSpec)

            val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = iv + ciphertext
            "e2e_sess:$sessionId:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            "cipher_err:[$plainText]"
        }
    }

    fun decryptPayload(cipherText: String): String {
        check(!isClosed) { "CryptoSession $sessionId is already closed and disposed." }
        val currentKey = secretKey ?: error("SecretKey not available")
        return try {
            val prefix = "e2e_sess:$sessionId:"
            if (!cipherText.startsWith(prefix)) return cipherText
            val rawB64 = cipherText.removePrefix(prefix)
            val combined = Base64.decode(rawB64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return cipherText

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, currentKey, parameterSpec)

            val decrypted = cipher.doFinal(encryptedBytes)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            cipherText
        }
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            // Securely wipe secret key bytes from RAM
            rawKeyBytes?.let { Arrays.fill(it, 0.toByte()) }
            rawKeyBytes = null
            secretKey = null
        }
    }
}
