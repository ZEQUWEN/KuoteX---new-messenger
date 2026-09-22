package com.example.data

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.nio.charset.StandardCharsets
import android.util.Base64

object CryptoManager {
    @Volatile
    private var aead: Aead? = null
    private const val KEYSET_NAME = "messenger_keyset"
    private const val PREF_FILE_NAME = "messenger_crypto_prefs"
    private const val MASTER_KEY_URI = "android-keystore://messenger_master_key"
    private const val PREF_DB_PASSPHRASE = "db_passphrase_v2"
    private const val PREF_RAW_FALLBACK = "db_passphrase_raw"

    @Synchronized
    fun init(context: Context) {
        if (aead != null) return
        try {
            AeadConfig.register()
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context.applicationContext, KEYSET_NAME, PREF_FILE_NAME)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            
            aead = keysetHandle.getPrimitive(Aead::class.java)
        } catch (t: Throwable) {
            try {
                val keysetHandle = AndroidKeysetManager.Builder()
                    .withSharedPref(context.applicationContext, KEYSET_NAME, PREF_FILE_NAME)
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .build()
                    .keysetHandle
                aead = keysetHandle.getPrimitive(Aead::class.java)
            } catch (t2: Throwable) {
                // Keystore fallback if unavailable on device/container
            }
        }
    }

    @Synchronized
    fun getDatabasePassphrase(context: Context): CharArray {
        init(context)
        val prefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
        val encryptedPassphrase = prefs.getString(PREF_DB_PASSPHRASE, null)
        if (!encryptedPassphrase.isNullOrEmpty()) {
            val decrypted = decrypt(encryptedPassphrase)
            if (decrypted.isNotEmpty() && decrypted != encryptedPassphrase) {
                return decrypted.toCharArray()
            }
        }

        var rawPassphrase = prefs.getString(PREF_RAW_FALLBACK, null)
        if (rawPassphrase.isNullOrEmpty()) {
            val randomBytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(randomBytes)
            rawPassphrase = java.util.Base64.getEncoder().encodeToString(randomBytes)
            prefs.edit().putString(PREF_RAW_FALLBACK, rawPassphrase).apply()
        }

        val encrypted = encrypt(rawPassphrase)
        if (encrypted != rawPassphrase) {
            prefs.edit().putString(PREF_DB_PASSPHRASE, encrypted).apply()
        }
        return rawPassphrase.toCharArray()
    }

    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return plaintext
        return try {
            val aeadPrimitive = aead ?: return plaintext
            val ciphertext = aeadPrimitive.encrypt(plaintext.toByteArray(StandardCharsets.UTF_8), null)
            java.util.Base64.getEncoder().encodeToString(ciphertext)
        } catch (t: Throwable) {
            plaintext
        }
    }

    fun decrypt(ciphertextBase64: String): String {
        if (ciphertextBase64.isEmpty()) return ciphertextBase64
        return try {
            val aeadPrimitive = aead ?: return ciphertextBase64
            val ciphertext = try {
                java.util.Base64.getDecoder().decode(ciphertextBase64)
            } catch (e: Exception) {
                return ciphertextBase64
            }
            val plaintext = aeadPrimitive.decrypt(ciphertext, null)
            String(plaintext, StandardCharsets.UTF_8)
        } catch (t: Throwable) {
            ciphertextBase64
        }
    }
}
