package com.example.mtproto

import com.example.crypto.AesIge
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays

/**
 * Криптографическое ядро MTProto 2.0 для KuoteX.
 *
 * Вынесено из MTProtoManager отдельно, чтобы крипточасть можно было
 * тестировать и аудировать без сети, DH и Android-зависимостей.
 *
 * Референс: https://core.telegram.org/mtproto/description
 */
object MTProtoCrypto {

    private val secureRandom = SecureRandom()

    /** Нарушение криптографического инварианта. Соединение обязано быть разорвано. */
    class SecurityViolation(message: String) : Exception(message)

    // ---------------------------------------------------------------- helpers

    private fun sha256(vararg parts: ByteArray): ByteArray {
        val d = MessageDigest.getInstance("SHA-256")
        for (p in parts) d.update(p)
        return d.digest()
    }

    private fun sha1(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-1").digest(data)

    /**
     * Сравнение за постоянное время. Обычный Arrays.equals выходит на первом
     * различии и даёт таймингову утечку при проверке msg_key.
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    /** auth_key_id = младшие 8 байт SHA1(auth_key), little-endian Long. */
    fun authKeyId(authKey: ByteArray): Long {
        val tail = sha1(authKey).copyOfRange(12, 20)
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (tail[i].toLong() and 0xFF)
        return v
    }

    // ---------------------------------------------------------------- KDF

    /**
     * msg_key = средние 128 бит SHA256(substr(auth_key, 88+x, 32) + plaintext).
     * x = 0 для сообщений от клиента, 8 — от сервера.
     */
    fun messageKey(authKey: ByteArray, plaintext: ByteArray, fromClient: Boolean): ByteArray {
        requireAuthKey(authKey)
        val x = if (fromClient) 0 else 8
        return sha256(authKey.copyOfRange(88 + x, 88 + x + 32), plaintext)
            .copyOfRange(8, 24)
    }

    /** MTProto 2.0 KDF. Возвращает (aes_key 32 байта, aes_iv 32 байта). */
    fun deriveKeyIv(authKey: ByteArray, msgKey: ByteArray, fromClient: Boolean): Pair<ByteArray, ByteArray> {
        requireAuthKey(authKey)
        if (msgKey.size != 16) throw SecurityViolation("msg_key must be 16 bytes")
        val x = if (fromClient) 0 else 8

        val a = sha256(msgKey, authKey.copyOfRange(x, x + 36))
        val b = sha256(authKey.copyOfRange(40 + x, 40 + x + 36), msgKey)

        val aesKey = a.copyOfRange(0, 8) + b.copyOfRange(8, 24) + a.copyOfRange(24, 32)
        val aesIv = b.copyOfRange(0, 8) + a.copyOfRange(8, 24) + b.copyOfRange(24, 32)
        return Pair(aesKey, aesIv)
    }

    private fun requireAuthKey(authKey: ByteArray) {
        if (authKey.size != 256) throw SecurityViolation("auth_key must be 256 bytes (2048 bit)")
    }

    // ---------------------------------------------------------------- envelope

    /**
     * Шифрует уже собранный payload (salt|session_id|msg_id|seq_no|len|body).
     * Возвращает полный конверт: auth_key_id(8) + msg_key(16) + ciphertext.
     */
    fun encrypt(authKey: ByteArray, payload: ByteArray, fromClient: Boolean): ByteArray {
        requireAuthKey(authKey)

        // Паддинг 12..1024 байт так, чтобы длина стала кратна 16.
        // Случайная длина маскирует реальный размер сообщения.
        var padLen = 12 + ((-(payload.size + 12)) % 16 + 16) % 16
        val extraBlocks = secureRandom.nextInt(8)          // 0..7 доп. блоков
        padLen += extraBlocks * 16
        val padding = ByteArray(padLen).also { secureRandom.nextBytes(it) }
        val padded = payload + padding

        val msgKey = messageKey(authKey, padded, fromClient)
        val (aesKey, aesIv) = deriveKeyIv(authKey, msgKey, fromClient)
        val encrypted = AesIge.encrypt(padded, aesKey, aesIv)

        Arrays.fill(aesKey, 0)
        Arrays.fill(aesIv, 0)

        val out = ByteArray(8 + 16 + encrypted.size)
        writeLongLE(out, 0, authKeyId(authKey))
        System.arraycopy(msgKey, 0, out, 8, 16)
        System.arraycopy(encrypted, 0, out, 24, encrypted.size)
        return out
    }

    /**
     * Расшифровывает конверт и ОБЯЗАТЕЛЬНО проверяет msg_key до разбора тела.
     * Возвращает расшифрованный padded payload.
     */
    fun decrypt(authKey: ByteArray, envelope: ByteArray, fromClient: Boolean): ByteArray {
        requireAuthKey(authKey)
        if (envelope.size < 24 + 16 || (envelope.size - 24) % 16 != 0)
            throw SecurityViolation("bad envelope length")

        val expectedId = authKeyId(authKey)
        if (readLongLE(envelope, 0) != expectedId)
            throw SecurityViolation("unknown auth_key_id")

        val msgKey = envelope.copyOfRange(8, 24)
        val (aesKey, aesIv) = deriveKeyIv(authKey, msgKey, fromClient)
        val decrypted = AesIge.decrypt(envelope.copyOfRange(24, envelope.size), aesKey, aesIv)

        Arrays.fill(aesKey, 0)
        Arrays.fill(aesIv, 0)

        // Проверка целостности ДО любого парсинга — иначе получаем padding oracle.
        val actual = messageKey(authKey, decrypted, fromClient)
        if (!constantTimeEquals(msgKey, actual))
            throw SecurityViolation("msg_key mismatch: message forged or corrupted")

        return decrypted
    }

    // ---------------------------------------------------------------- LE utils

    fun writeLongLE(buf: ByteArray, off: Int, value: Long) {
        for (i in 0 until 8) buf[off + i] = ((value ushr (8 * i)) and 0xFF).toByte()
    }

    fun readLongLE(buf: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (buf[off + i].toLong() and 0xFF)
        return v
    }

    fun writeIntLE(buf: ByteArray, off: Int, value: Int) {
        for (i in 0 until 4) buf[off + i] = ((value ushr (8 * i)) and 0xFF).toByte()
    }

    fun readIntLE(buf: ByteArray, off: Int): Int {
        var v = 0
        for (i in 3 downTo 0) v = (v shl 8) or (buf[off + i].toInt() and 0xFF)
        return v
    }
}
