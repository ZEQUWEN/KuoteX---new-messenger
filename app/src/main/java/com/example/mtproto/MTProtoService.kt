package com.example.mtproto

import com.example.tlschema.TLStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Служебные сообщения MTProto: контейнеры, подтверждения, ошибки, ping.
 *
 * Это «служебный язык» протокола поверх шифрования. Без него:
 *  - сервер не знает, дошло ли сообщение, и шлёт его снова и снова;
 *  - каждое мелкое сообщение уходит отдельным пакетом (дорого в мобильной сети);
 *  - при расхождении часов или устаревшей соли соединение молча ломается.
 *
 * Референс: https://core.telegram.org/mtproto/service_messages
 */
object MTProtoService {

    // Идентификаторы конструкторов из схемы Telegram.
    const val MSG_CONTAINER = 0x73f1f8dc
    const val MSGS_ACK = 0x62d6b459
    const val BAD_MSG_NOTIFICATION = 0xa7eff811.toInt()
    const val BAD_SERVER_SALT = 0xedab447b.toInt()
    const val NEW_SESSION_CREATED = 0x9ec20908.toInt()
    const val PING = 0x7abe77ec
    const val PONG = 0x347773c5
    const val PING_DELAY_DISCONNECT = 0xf3427b8c.toInt()
    const val RPC_RESULT = 0xf35c6d01.toInt()
    const val RPC_ERROR = 0x2144ca19
    const val GZIP_PACKED = 0x3072cfa1
    const val MSG_DETAILED_INFO = 0x276d3ec6
    const val MSG_NEW_DETAILED_INFO = 0x809db6df.toInt()

    /** Разобранное служебное или прикладное сообщение. */
    sealed class Incoming {
        /** Ответ на запрос: msg_id запроса + тело результата. */
        data class RpcResult(val requestMsgId: Long, val result: ByteArray) : Incoming()

        /** Ошибка выполнения запроса. */
        data class RpcError(
            val requestMsgId: Long,
            val errorCode: Int,
            val errorMessage: String
        ) : Incoming()

        /** Сервер подтвердил приём наших сообщений. */
        data class Ack(val msgIds: List<Long>) : Incoming()

        /**
         * Наше сообщение отвергнуто: часы разошлись или seq_no неверный.
         * Коды 16/17 означают проблему со временем и лечатся синхронизацией.
         */
        data class BadMessage(
            val badMsgId: Long,
            val badMsgSeqNo: Int,
            val errorCode: Int
        ) : Incoming() {
            /** 16 — msg_id слишком старый, 17 — слишком новый. */
            val isClockProblem: Boolean get() = errorCode == 16 || errorCode == 17
        }

        /** Соль устарела; сервер прислал новую и ждёт повтора запроса. */
        data class BadSalt(
            val badMsgId: Long,
            val errorCode: Int,
            val newServerSalt: Long
        ) : Incoming()

        /** Сервер начал новую сессию: часть сообщений могла потеряться. */
        data class NewSession(
            val firstMsgId: Long,
            val uniqueId: Long,
            val serverSalt: Long
        ) : Incoming()

        data class Pong(val msgId: Long, val pingId: Long) : Incoming()

        /** Неизвестный или прикладной объект — отдаётся уровню выше. */
        data class Unknown(val constructorId: Int, val body: ByteArray) : Incoming()
    }

    /** Сообщение с метаданными, как оно лежит внутри контейнера. */
    data class Message(
        val msgId: Long,
        val seqNo: Int,
        val body: ByteArray
    )

    // ---------------------------------------------------------------- parse

    /**
     * Разбирает тело сообщения, разворачивая контейнеры.
     *
     * @return список пар (метаданные, разобранное содержимое).
     *   Контейнер раскрывается: наверх приходят уже отдельные сообщения.
     */
    fun parse(msgId: Long, seqNo: Int, body: ByteArray): List<Pair<Message, Incoming>> {
        if (body.size < 4) return emptyList()
        val ctor = MTProtoCrypto.readIntLE(body, 0)

        if (ctor == MSG_CONTAINER) {
            return parseContainer(body).flatMap { inner ->
                // Вложенные контейнеры спецификацией запрещены,
                // но рекурсия здесь безопасна: parse развернёт и их.
                parse(inner.msgId, inner.seqNo, inner.body)
            }
        }

        return listOf(Message(msgId, seqNo, body) to parseSingle(body))
    }

    /** msg_container#73f1f8dc messages:vector<%Message> = MessageContainer */
    fun parseContainer(body: ByteArray): List<Message> {
        val input = ByteArrayInputStream(body)
        val ctor = TLStream.readInt(input)
        if (ctor != MSG_CONTAINER)
            throw MTProtoCrypto.SecurityViolation("not a msg_container")

        val count = TLStream.readInt(input)
        if (count < 0 || count > MAX_CONTAINER_MESSAGES)
            throw MTProtoCrypto.SecurityViolation("bad container size: $count")

        val result = ArrayList<Message>(count)
        var offset = 8
        repeat(count) {
            if (offset + 16 > body.size)
                throw MTProtoCrypto.SecurityViolation("container truncated")
            val msgId = MTProtoCrypto.readLongLE(body, offset)
            val seqNo = MTProtoCrypto.readIntLE(body, offset + 8)
            val length = MTProtoCrypto.readIntLE(body, offset + 12)

            if (length < 0 || length % 4 != 0)
                throw MTProtoCrypto.SecurityViolation("bad inner length: $length")
            if (offset + 16 + length > body.size)
                throw MTProtoCrypto.SecurityViolation("inner message exceeds container")

            result.add(
                Message(msgId, seqNo, body.copyOfRange(offset + 16, offset + 16 + length))
            )
            offset += 16 + length
        }
        return result
    }

    private fun parseSingle(body: ByteArray): Incoming {
        val ctor = MTProtoCrypto.readIntLE(body, 0)
        val input = ByteArrayInputStream(body)
        TLStream.readInt(input)

        return when (ctor) {
            RPC_RESULT -> {
                val requestMsgId = TLStream.readLong(input)
                Incoming.RpcResult(requestMsgId, body.copyOfRange(12, body.size))
            }

            RPC_ERROR -> {
                // rpc_error приходит внутри rpc_result, но обрабатываем и отдельно.
                val code = TLStream.readInt(input)
                val message = TLStream.readString(input)
                Incoming.RpcError(0L, code, message)
            }

            MSGS_ACK -> {
                val vectorCtor = TLStream.readInt(input)
                if (vectorCtor != VECTOR)
                    throw MTProtoCrypto.SecurityViolation("msgs_ack without vector")
                val count = TLStream.readInt(input)
                if (count < 0 || count > MAX_ACK_IDS)
                    throw MTProtoCrypto.SecurityViolation("bad ack count: $count")
                Incoming.Ack((0 until count).map { TLStream.readLong(input) })
            }

            BAD_MSG_NOTIFICATION -> Incoming.BadMessage(
                badMsgId = TLStream.readLong(input),
                badMsgSeqNo = TLStream.readInt(input),
                errorCode = TLStream.readInt(input)
            )

            BAD_SERVER_SALT -> {
                // bad_msg_id, bad_msg_seqno, error_code, new_server_salt
                val badMsgId = TLStream.readLong(input)
                TLStream.readInt(input)                  // bad_msg_seqno не нужен
                val errorCode = TLStream.readInt(input)
                Incoming.BadSalt(badMsgId, errorCode, TLStream.readLong(input))
            }

            NEW_SESSION_CREATED -> Incoming.NewSession(
                firstMsgId = TLStream.readLong(input),
                uniqueId = TLStream.readLong(input),
                serverSalt = TLStream.readLong(input)
            )

            PONG -> Incoming.Pong(
                msgId = TLStream.readLong(input),
                pingId = TLStream.readLong(input)
            )

            else -> Incoming.Unknown(ctor, body)
        }
    }

    // ---------------------------------------------------------------- build

    /** msgs_ack#62d6b459 msg_ids:Vector<long> = MsgsAck */
    fun buildAck(msgIds: List<Long>): ByteArray {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, MSGS_ACK)
        TLStream.writeInt(out, VECTOR)
        TLStream.writeInt(out, msgIds.size)
        msgIds.forEach { TLStream.writeLong(out, it) }
        return out.toByteArray()
    }

    /**
     * Собирает контейнер из нескольких сообщений.
     *
     * Зачем: отправлять ack, ping и запрос тремя пакетами расточительно —
     * в мобильной сети каждый пакет это радиосессия и расход батареи.
     * Контейнер объединяет их в один конверт.
     */
    fun buildContainer(messages: List<Message>): ByteArray {
        if (messages.size > MAX_CONTAINER_MESSAGES)
            throw IllegalArgumentException("too many messages in container")

        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, MSG_CONTAINER)
        TLStream.writeInt(out, messages.size)
        for (m in messages) {
            if (m.body.size % 4 != 0)
                throw IllegalArgumentException("message body must be 4-byte aligned")
            val header = ByteArray(16)
            MTProtoCrypto.writeLongLE(header, 0, m.msgId)
            MTProtoCrypto.writeIntLE(header, 8, m.seqNo)
            MTProtoCrypto.writeIntLE(header, 12, m.body.size)
            out.write(header)
            out.write(m.body)
        }
        return out.toByteArray()
    }

    /** ping#7abe77ec ping_id:long = Pong */
    fun buildPing(pingId: Long): ByteArray {
        val out = ByteArray(12)
        MTProtoCrypto.writeIntLE(out, 0, PING)
        MTProtoCrypto.writeLongLE(out, 4, pingId)
        return out
    }

    /** ping_delay_disconnect#f3427b8c ping_id:long disconnect_delay:int = Pong */
    fun buildPingDelayDisconnect(pingId: Long, delaySeconds: Int): ByteArray {
        val out = ByteArray(16)
        MTProtoCrypto.writeIntLE(out, 0, PING_DELAY_DISCONNECT)
        MTProtoCrypto.writeLongLE(out, 4, pingId)
        MTProtoCrypto.writeIntLE(out, 12, delaySeconds)
        return out
    }

    private const val VECTOR = 0x1cb5c415
    private const val MAX_CONTAINER_MESSAGES = 1024
    private const val MAX_ACK_IDS = 8192
}
