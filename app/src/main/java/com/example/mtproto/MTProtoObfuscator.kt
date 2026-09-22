package com.example.mtproto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Транспортная обфускация obfuscated2.
 *
 * Зачем это KuoteX: без неё поток мессенджера имеет узнаваемую сигнатуру,
 * и DPI операторов блокирует его по первым байтам. Здесь весь поток —
 * AES-CTR, статистически неотличимый от случайных данных: нет заголовков,
 * нет постоянных полей, первые 64 байта тоже случайны.
 *
 * Ключи для двух направлений выводятся из одного случайного 64-байтного
 * блока: обратное направление использует те же байты, развёрнутые задом наперёд.
 */
class MTProtoObfuscator private constructor(
    val initialPayload: ByteArray,   // 64 байта, отправляются первыми, в открытом виде
    private val encryptor: Cipher,
    private val decryptor: Cipher
) {

    fun encrypt(data: ByteArray): ByteArray = encryptor.update(data)
    fun decrypt(data: ByteArray): ByteArray = decryptor.update(data)

    companion object {
        private val random = SecureRandom()

        /** Сигнатуры, которые нельзя выдать в первых байтах — иначе поток опознаётся. */
        private val FORBIDDEN_PREFIXES = listOf(
            byteArrayOf(0x50, 0x4F, 0x53, 0x54),          // POST
            byteArrayOf(0x47, 0x45, 0x54, 0x20),          // "GET "
            byteArrayOf(0x48, 0x45, 0x41, 0x44),          // HEAD
            byteArrayOf(0x4F, 0x50, 0x54, 0x49),          // OPTI
            byteArrayOf(0xEE.toByte(), 0xEE.toByte(), 0xEE.toByte(), 0xEE.toByte()),
            byteArrayOf(0xDD.toByte(), 0xDD.toByte(), 0xDD.toByte(), 0xDD.toByte()),
            byteArrayOf(0x16, 0x03, 0x01, 0x02)           // TLS ClientHello
        )

        /** Протокол abridged: 4 байта 0xEF. */
        val PROTOCOL_ABRIDGED = byteArrayOf(0xEF.toByte(), 0xEF.toByte(), 0xEF.toByte(), 0xEF.toByte())

        fun create(dcId: Int, protocol: ByteArray = PROTOCOL_ABRIDGED): MTProtoObfuscator {
            val buf = ByteArray(64)
            while (true) {
                random.nextBytes(buf)
                if (buf[0] == 0xEF.toByte()) continue
                if (FORBIDDEN_PREFIXES.any { p -> (0..3).all { buf[it] == p[it] } }) continue
                if (buf[4] == 0.toByte() && buf[5] == 0.toByte() &&
                    buf[6] == 0.toByte() && buf[7] == 0.toByte()) continue
                break
            }
            System.arraycopy(protocol, 0, buf, 56, 4)
            buf[60] = (dcId and 0xFF).toByte()
            buf[61] = ((dcId shr 8) and 0xFF).toByte()

            val encKey = buf.copyOfRange(8, 40)
            val encIv = buf.copyOfRange(40, 56)

            val reversed = buf.copyOfRange(8, 56).reversedArray()
            val decKey = reversed.copyOfRange(0, 32)
            val decIv = reversed.copyOfRange(32, 48)

            val encryptor = ctr(encKey, encIv)
            val decryptor = ctr(decKey, decIv)

            // Байты 56..63 отправляются в зашифрованном виде — сервер по ним
            // проверяет, что расшифровал поток верно.
            val encryptedAll = encryptor.update(buf)
            val payload = buf.copyOfRange(0, 56) + encryptedAll.copyOfRange(56, 64)

            return MTProtoObfuscator(payload, encryptor, decryptor)
        }

        private fun ctr(key: ByteArray, iv: ByteArray): Cipher =
            Cipher.getInstance("AES/CTR/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }

        /** abridged framing: длина пакета в 4-байтных словах. */
        fun frame(payload: ByteArray): ByteArray {
            require(payload.size % 4 == 0) { "payload must be 4-byte aligned" }
            val words = payload.size / 4
            return if (words < 0x7F) {
                byteArrayOf(words.toByte()) + payload
            } else {
                byteArrayOf(0x7F) +
                    byteArrayOf(
                        (words and 0xFF).toByte(),
                        ((words shr 8) and 0xFF).toByte(),
                        ((words shr 16) and 0xFF).toByte()
                    ) + payload
            }
        }
    }
}
