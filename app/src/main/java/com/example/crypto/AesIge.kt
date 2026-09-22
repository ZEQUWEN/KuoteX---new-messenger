package com.example.crypto

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Реализация режима Infinite Garble Extension (IGE) для AES-256.
 * Используется в MTProto Telegram.
 */
object AesIge {

    fun encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(data.size % 16 == 0) { "Data size must be a multiple of 16 for AES-IGE" }
        require(key.size == 32) { "Key must be 32 bytes for AES-256" }
        require(iv.size == 32) { "IV must be 32 bytes for IGE (IV1 + IV2)" }

        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))

        val result = ByteArray(data.size)
        
        // IGE uses two 16-byte halves of the 32-byte IV
        var prevC = iv.copyOfRange(0, 16)
        var prevP = iv.copyOfRange(16, 32)

        for (i in data.indices step 16) {
            val block = data.copyOfRange(i, i + 16)
            
            // Xor plaintext block with previous ciphertext block
            val xored = xor(block, prevC)
            
            // Encrypt using AES
            val encrypted = cipher.doFinal(xored)
            
            // Xor result with previous plaintext block
            val c = xor(encrypted, prevP)
            
            System.arraycopy(c, 0, result, i, 16)
            
            // Update previous blocks for next iteration
            prevP = block
            prevC = c
        }
        
        return result
    }

    fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(data.size % 16 == 0) { "Data size must be a multiple of 16 for AES-IGE" }
        require(key.size == 32) { "Key must be 32 bytes for AES-256" }
        require(iv.size == 32) { "IV must be 32 bytes for IGE (IV1 + IV2)" }

        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))

        val result = ByteArray(data.size)
        
        // IGE uses two 16-byte halves of the 32-byte IV
        var prevC = iv.copyOfRange(0, 16)
        var prevP = iv.copyOfRange(16, 32)

        for (i in data.indices step 16) {
            val block = data.copyOfRange(i, i + 16)
            
            // Xor ciphertext block with previous plaintext block
            val xored = xor(block, prevP)
            
            // Decrypt using AES
            val decrypted = cipher.doFinal(xored)
            
            // Xor result with previous ciphertext block
            val p = xor(decrypted, prevC)
            
            System.arraycopy(p, 0, result, i, 16)
            
            // Update previous blocks for next iteration
            prevC = block
            prevP = p
        }
        
        return result
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray {
        val result = ByteArray(a.size)
        for (i in a.indices) {
            result[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
        return result
    }
}
