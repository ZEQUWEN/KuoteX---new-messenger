package com.example.mtproto

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * Состояние сессии MTProto: msg_id, seq_no, server_salt, анти-replay.
 *
 * Именно этого слоя не хватало KuoteX: без него сообщения можно
 * переигрывать (replay), а сервер отвечает bad_msg_notification
 * на устройствах с неточными часами.
 *
 * Класс потокобезопасен: отправка может идти из нескольких корутин.
 */
class MTProtoSession(
    val authKey: ByteArray,
    val isClient: Boolean = true
) {
    init {
        if (authKey.size != 256)
            throw MTProtoCrypto.SecurityViolation("auth_key must be 256 bytes")
    }

    @Volatile
    var serverSalt: Long = 0L

    val sessionId: Long = SecureRandom().nextLong()

    private val seqCounter = AtomicInteger(0)
    private val lock = Any()
    private var lastMessageId = 0L

    /** Смещение часов устройства относительно сервера, мс. */
    @Volatile
    private var timeOffsetMs: Long = 0L

    /** Окно приёма: сообщения вне него отбрасываются (сек). */
    private val pastWindowSec = 300L
    private val futureWindowSec = 30L

    /** Множество увиденных msg_id для защиты от повторов. */
    private val seenMessageIds = LinkedHashSet<Long>()
    private val maxSeen = 4096

    // ------------------------------------------------------------ identifiers

    private fun nowMs(): Long = System.currentTimeMillis() + timeOffsetMs

    /**
     * Коррекция часов по msg_id сервера. Вызывать при получении
     * bad_msg_notification с кодами 16/17 или из new_session_created.
     */
    fun synchronizeClock(serverMessageId: Long) {
        val serverSec = serverMessageId ushr 32
        timeOffsetMs = serverSec * 1000L - System.currentTimeMillis()
    }

    /**
     * msg_id = unixtime << 32 | (доли секунды << 2) | флаг стороны.
     * Клиент использует чётные значения, сервер — нечётные.
     * Строго возрастает даже при переводе часов назад.
     */
    fun nextMessageId(): Long {
        synchronized(lock) {
            val ms = nowMs()
            val seconds = ms / 1000L
            val fraction = ((ms % 1000L) * ((1L shl 30) / 1000L))
            var id = (seconds shl 32) or ((fraction shl 2) and 0xFFFFFFFCL)
            id = if (isClient) (id and 3L.inv()) else (id and 3L.inv()) or 1L
            if (id <= lastMessageId) id = lastMessageId + 4
            lastMessageId = id
            return id
        }
    }

    /**
     * seq_no: нечётные для content-related сообщений (требующих ack),
     * чётные для служебных (ack, ping).
     */
    fun nextSeqNo(contentRelated: Boolean): Int =
        if (contentRelated) seqCounter.incrementAndGet() * 2 - 1
        else seqCounter.get() * 2

    // ------------------------------------------------------------ encrypt

    /** Упаковывает тело TL-запроса в зашифрованный конверт MTProto. */
    fun encrypt(body: ByteArray, contentRelated: Boolean = true): ByteArray =
        encryptWithIds(body, nextMessageId(), nextSeqNo(contentRelated))

    /**
     * Шифрует тело с заранее известными msg_id и seq_no.
     *
     * Нужно роутеру: он обязан запомнить msg_id ДО отправки, чтобы
     * сопоставить с ним будущий rpc_result. Если бы msg_id создавался
     * внутри, узнать его снаружи было бы нельзя.
     */
    fun encryptWithIds(body: ByteArray, messageId: Long, seqNo: Int): ByteArray {
        val payload = ByteArray(32 + body.size)
        MTProtoCrypto.writeLongLE(payload, 0, serverSalt)
        MTProtoCrypto.writeLongLE(payload, 8, sessionId)
        MTProtoCrypto.writeLongLE(payload, 16, messageId)
        MTProtoCrypto.writeIntLE(payload, 24, seqNo)
        MTProtoCrypto.writeIntLE(payload, 28, body.size)
        System.arraycopy(body, 0, payload, 32, body.size)

        return MTProtoCrypto.encrypt(authKey, payload, isClient)
    }

    // ------------------------------------------------------------ decrypt

    data class Incoming(
        val salt: Long,
        val sessionId: Long,
        val messageId: Long,
        val seqNo: Int,
        val body: ByteArray
    )

    /** Расшифровывает конверт и валидирует все инварианты MTProto. */
    fun decrypt(envelope: ByteArray): Incoming {
        // Входящие для клиента приходят от сервера -> fromClient = false.
        val decrypted = MTProtoCrypto.decrypt(authKey, envelope, !isClient)

        if (decrypted.size < 32)
            throw MTProtoCrypto.SecurityViolation("payload too short")

        val salt = MTProtoCrypto.readLongLE(decrypted, 0)
        val sid = MTProtoCrypto.readLongLE(decrypted, 8)
        val messageId = MTProtoCrypto.readLongLE(decrypted, 16)
        val seqNo = MTProtoCrypto.readIntLE(decrypted, 24)
        val length = MTProtoCrypto.readIntLE(decrypted, 28)

        if (length < 0 || length % 4 != 0 || 32 + length > decrypted.size)
            throw MTProtoCrypto.SecurityViolation("bad body length")
        if (decrypted.size - 32 - length < 12)
            throw MTProtoCrypto.SecurityViolation("padding shorter than 12 bytes")
        if (sid != sessionId)
            throw MTProtoCrypto.SecurityViolation("session_id mismatch")

        // Чётность: клиент ждёт нечётные msg_id от сервера и наоборот.
        val expectedOdd = if (isClient) 1L else 0L
        if ((messageId and 1L) != expectedOdd)
            throw MTProtoCrypto.SecurityViolation("bad msg_id parity")

        // Временное окно.
        val tsSec = messageId ushr 32
        val nowSec = nowMs() / 1000L
        if (tsSec < nowSec - pastWindowSec || tsSec > nowSec + futureWindowSec)
            throw MTProtoCrypto.SecurityViolation("msg_id outside acceptance window")

        // Анти-replay.
        synchronized(lock) {
            if (!seenMessageIds.add(messageId))
                throw MTProtoCrypto.SecurityViolation("replayed msg_id")
            if (seenMessageIds.size > maxSeen) {
                val iter = seenMessageIds.iterator()
                var toDrop = maxSeen / 4
                while (toDrop > 0 && iter.hasNext()) {
                    iter.next()
                    iter.remove()
                    toDrop--
                }
            }
        }

        return Incoming(salt, sid, messageId, seqNo, decrypted.copyOfRange(32, 32 + length))
    }
}

