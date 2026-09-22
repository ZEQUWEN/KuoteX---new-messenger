package com.example.mtproto

import java.math.BigInteger
import java.security.SecureRandom
import java.util.Arrays

/**
 * Менеджер MTProto 2.0 для KuoteX.
 *
 * Публичный API совместим с предыдущей версией (initializeSession,
 * completeDhExchange, encryptMessage, decryptMessage, authKey, sessionId,
 * serverSalt), поэтому com.example.api.TDLib продолжает работать без правок.
 *
 * Что изменилось внутри:
 *  - конверт теперь полный (auth_key_id + msg_key + данные), а не msg_key + данные;
 *  - msg_key проверяется при расшифровке (раньше подделка проходила молча);
 *  - добавлены msg_id / seq_no / session_id — без них работает replay;
 *  - DH проверяет группу и присланные значения (раньше сервер мог навязать слабую группу);
 *  - паддинг 12..1024 байта по спецификации 2.0 (раньше 16, как в 1.0).
 *
 * Референс: https://core.telegram.org/mtproto/description
 */
class MTProtoManager(val isClient: Boolean = true) {

    var authKey: ByteArray? = null
        private set

    /** Сессия создаётся сразу после получения auth_key. */
    var session: MTProtoSession? = null
        private set

    val sessionId: Long
        get() = session?.sessionId ?: 0L

    var serverSalt: Long
        get() = session?.serverSalt ?: 0L
        set(value) { session?.serverSalt = value }

    private var secretB: BigInteger? = null

    // ------------------------------------------------------------ handshake

    /**
     * Шаг 1: генерация локального секрета DH.
     * @return g_b в виде 256 байт — отправляется серверу.
     */
    fun initializeSession(): ByteArray {
        val b = MTProtoDh.generateSecret()
        secretB = b
        val gB = MTProtoDh.G.modPow(b, MTProtoDh.P)
        return MTProtoDh.toFixed256(gB)
    }

    /**
     * Шаг 2: завершение DH. Принимает g_a сервера (256 байт big-endian).
     *
     * Внимание: формат изменился. Раньше сюда передавался X509-encoded
     * ключ JCA; теперь — сырое значение g_a, как в спецификации MTProto.
     */
    fun completeDhExchange(serverGaBytes: ByteArray) {
        val b = secretB ?: throw IllegalStateException("Call initializeSession() first")
        val gA = BigInteger(1, serverGaBytes)

        val result = MTProtoDh.computeAuthKey(gA, b)

        authKey = result.authKey
        session = MTProtoSession(result.authKey, isClient)

        // Секретную экспоненту после вывода ключа держать в памяти незачем.
        secretB = null
    }

    /**
     * Прямая установка auth_key — для восстановления сессии из
     * защищённого хранилища без повторного handshake.
     */
    fun restoreAuthKey(key: ByteArray, salt: Long = 0L) {
        if (key.size != 256)
            throw MTProtoCrypto.SecurityViolation("auth_key must be 256 bytes")
        authKey = key
        session = MTProtoSession(key, isClient).apply { serverSalt = salt }
    }

    /** Идентификатор ключа, по которому сервер выбирает auth_key. */
    fun authKeyId(): Long {
        val key = authKey ?: throw IllegalStateException("Auth key is not generated")
        return MTProtoCrypto.authKeyId(key)
    }

    // ------------------------------------------------------------ messages

    /**
     * Шифрует TL-сериализованное тело в полный конверт MTProto 2.0.
     * @return auth_key_id(8) + msg_key(16) + AES-256-IGE(данные)
     */
    fun encryptMessage(messageData: ByteArray, contentRelated: Boolean = true): ByteArray {
        val s = session
            ?: throw IllegalStateException("Auth key is not generated. Complete DH exchange first.")
        return s.encrypt(messageData, contentRelated)
    }

    /**
     * Расшифровывает конверт с полной проверкой инвариантов.
     * @return только тело сообщения, без служебного заголовка и паддинга.
     * @throws MTProtoCrypto.SecurityViolation при подделке, повторе или сдвиге часов.
     */
    fun decryptMessage(encryptedPayload: ByteArray): ByteArray {
        val s = session
            ?: throw IllegalStateException("Auth key is not generated")
        return s.decrypt(encryptedPayload).body
    }

    /** Полная расшифровка вместе с метаданными (msg_id нужен для msgs_ack). */
    fun decryptFull(encryptedPayload: ByteArray): MTProtoSession.Incoming {
        val s = session ?: throw IllegalStateException("Auth key is not generated")
        return s.decrypt(encryptedPayload)
    }

    /** Коррекция часов по ответу сервера (bad_msg_notification 16/17). */
    fun synchronizeClock(serverMessageId: Long) {
        session?.synchronizeClock(serverMessageId)
    }

    /** Обнуляет ключ в памяти при выходе из аккаунта. */
    fun wipe() {
        authKey?.let { Arrays.fill(it, 0.toByte()) }
        authKey = null
        session = null
        secretB = null
    }
}

