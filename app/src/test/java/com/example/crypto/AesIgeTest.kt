package com.example.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.security.SecureRandom

class AesIgeTest {

    @Test
    fun testEncryptionAndDecryption() {
        val key = ByteArray(32)
        val iv = ByteArray(32)
        val random = SecureRandom()
        random.nextBytes(key)
        random.nextBytes(iv)

        val plaintext = ByteArray(64) // Multiple of 16
        random.nextBytes(plaintext)

        val ciphertext = AesIge.encrypt(plaintext, key, iv)
        val decrypted = AesIge.decrypt(ciphertext, key, iv)

        assertArrayEquals(plaintext, decrypted)
    }
}
