package com.example.tlschema

import java.io.ByteArrayOutputStream

/**
 * Объект `auth.authorization`, представляющий успешную авторизацию.
 * Референс: https://core.telegram.org/constructor/auth.authorization
 */
data class TLAuthAuthorization(
    val userId: Long,
    val firstName: String,
    val lastName: String?,
    val username: String?
) : TLObject {
    override val constructorId: Int = 0xcd050916.toInt()

    override fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, constructorId)
        
        var flags = 0
        if (lastName != null) flags = flags or 2
        if (username != null) flags = flags or 8
        TLStream.writeInt(out, flags)
        
        TLStream.writeLong(out, userId)
        TLStream.writeString(out, firstName)
        if (lastName != null) TLStream.writeString(out, lastName)
        if (username != null) TLStream.writeString(out, username)
        
        return out.toByteArray()
    }
}

/**
 * Метод `messages.sendMessage`.
 * Референс: https://core.telegram.org/method/messages.sendMessage
 */
data class TLMessagesSendMessage(
    val peerId: Long,
    val message: String,
    val randomId: Long
) : TLObject {
    override val constructorId: Int = 0x520c3870.toInt()

    override fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, constructorId)
        TLStream.writeInt(out, 0) // flags
        
        // Peer (упрощенно)
        TLStream.writeLong(out, peerId) 
        TLStream.writeLong(out, randomId)
        TLStream.writeString(out, message)
        
        return out.toByteArray()
    }
}
