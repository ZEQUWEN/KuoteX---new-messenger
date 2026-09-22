package com.example.mtproto

import com.example.tlschema.TLObject
import com.example.tlschema.TLStream
import java.io.ByteArrayOutputStream

/**
 * Структура нешифрованного сообщения MTProto.
 * Используется в основном для начального обмена ключами.
 */
data class MTProtoUnencryptedMessage(
    val authKeyId: Long = 0L, // Всегда 0 для нешифрованных
    val messageId: Long,
    val messageDataLength: Int,
    val messageData: ByteArray
) {
    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        TLStream.writeLong(out, authKeyId)
        TLStream.writeLong(out, messageId)
        TLStream.writeInt(out, messageDataLength)
        out.write(messageData)
        return out.toByteArray()
    }
}

/**
 * Структура зашифрованного сообщения MTProto (Data Message).
 */
data class MTProtoEncryptedMessage(
    val authKeyId: Long,
    val msgKey: ByteArray,     // 16 байт
    val encryptedData: ByteArray
) {
    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        TLStream.writeLong(out, authKeyId)
        out.write(msgKey)
        out.write(encryptedData)
        return out.toByteArray()
    }
}

/**
 * Расшифрованная полезная нагрузка сообщения MTProto.
 */
data class MTProtoPayload(
    val salt: Long,
    val sessionId: Long,
    val messageId: Long,
    val seqNo: Int,
    val messageDataLength: Int,
    val messageData: ByteArray
) {
    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        TLStream.writeLong(out, salt)
        TLStream.writeLong(out, sessionId)
        TLStream.writeLong(out, messageId)
        TLStream.writeInt(out, seqNo)
        TLStream.writeInt(out, messageDataLength)
        out.write(messageData)
        return out.toByteArray()
    }
}
