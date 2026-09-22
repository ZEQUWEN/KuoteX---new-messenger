package com.example.mtproto

import com.example.tlschema.TLStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

/** Тесты служебного слоя: контейнеры, ack, уведомления об ошибках. */
class MTProtoServiceTest {

    private val rnd = SecureRandom()

    private fun aligned(text: String): ByteArray {
        val b = text.toByteArray(Charsets.UTF_8)
        return b + ByteArray((4 - b.size % 4) % 4)
    }

    // ------------------------------------------------------------ container

    @Test
    fun containerRoundTrip() {
        val messages = listOf(
            MTProtoService.Message(0x5000000000000004L, 1, aligned("first")),
            MTProtoService.Message(0x5000000000000008L, 3, aligned("second")),
            MTProtoService.Message(0x500000000000000CL, 5, aligned("third"))
        )
        val container = MTProtoService.buildContainer(messages)
        val parsed = MTProtoService.parseContainer(container)

        assertEquals(3, parsed.size)
        for (i in messages.indices) {
            assertEquals(messages[i].msgId, parsed[i].msgId)
            assertEquals(messages[i].seqNo, parsed[i].seqNo)
            assertArrayEquals(messages[i].body, parsed[i].body)
        }
    }

    @Test
    fun emptyContainerIsValid() {
        val parsed = MTProtoService.parseContainer(
            MTProtoService.buildContainer(emptyList())
        )
        assertTrue(parsed.isEmpty())
    }

    /** Контейнер должен раскрываться автоматически при разборе. */
    @Test
    fun parseUnwrapsContainer() {
        val ack = MTProtoService.buildAck(listOf(111L, 222L))
        val pong = ByteArray(20).also {
            MTProtoCrypto.writeIntLE(it, 0, MTProtoService.PONG)
            MTProtoCrypto.writeLongLE(it, 4, 555L)
            MTProtoCrypto.writeLongLE(it, 12, 777L)
        }
        val container = MTProtoService.buildContainer(
            listOf(
                MTProtoService.Message(4L, 2, ack),
                MTProtoService.Message(8L, 4, pong)
            )
        )

        val result = MTProtoService.parse(100L, 0, container)
        assertEquals(2, result.size)

        val first = result[0].second as MTProtoService.Incoming.Ack
        assertEquals(listOf(111L, 222L), first.msgIds)
        assertEquals(4L, result[0].first.msgId)

        val second = result[1].second as MTProtoService.Incoming.Pong
        assertEquals(777L, second.pingId)
    }

    /** Вредоносный контейнер не должен вызывать чтение за границей буфера. */
    @Test
    fun rejectsTruncatedContainer() {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, MTProtoService.MSG_CONTAINER)
        TLStream.writeInt(out, 2)                 // заявлено 2 сообщения
        val header = ByteArray(16)
        MTProtoCrypto.writeLongLE(header, 0, 4L)
        MTProtoCrypto.writeIntLE(header, 8, 1)
        MTProtoCrypto.writeIntLE(header, 12, 4)
        out.write(header)
        out.write(ByteArray(4))                   // а второго нет

        try {
            MTProtoService.parseContainer(out.toByteArray())
            fail("truncated container must be rejected")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("truncated"))
        }
    }

    @Test
    fun rejectsInnerLengthOverflow() {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, MTProtoService.MSG_CONTAINER)
        TLStream.writeInt(out, 1)
        val header = ByteArray(16)
        MTProtoCrypto.writeLongLE(header, 0, 4L)
        MTProtoCrypto.writeIntLE(header, 8, 1)
        // Кратная 4, но выходящая за буфер: проверяем именно границу,
        // а не выравнивание.
        MTProtoCrypto.writeIntLE(header, 12, 0x0FFFFFFC)
        out.write(header)
        out.write(ByteArray(4))

        try {
            MTProtoService.parseContainer(out.toByteArray())
            fail("must reject oversized inner length")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("exceeds"))
        }
    }

    @Test
    fun rejectsAbsurdMessageCount() {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, MTProtoService.MSG_CONTAINER)
        TLStream.writeInt(out, 100_000_000)
        try {
            MTProtoService.parseContainer(out.toByteArray())
            fail("must reject absurd count")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("container size"))
        }
    }

    @Test
    fun rejectsUnalignedBodyWhenBuilding() {
        try {
            MTProtoService.buildContainer(
                listOf(MTProtoService.Message(4L, 1, ByteArray(5)))
            )
            fail("unaligned body must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("4-byte"))
        }
    }

    // ------------------------------------------------------------ ack

    @Test
    fun ackRoundTrip() {
        val ids = listOf(0x5000000000000004L, 0x5000000000000008L, -42L)
        val parsed = MTProtoService.parse(1L, 0, MTProtoService.buildAck(ids))
        val ack = parsed.single().second as MTProtoService.Incoming.Ack
        assertEquals(ids, ack.msgIds)
    }

    @Test
    fun emptyAckIsValid() {
        val parsed = MTProtoService.parse(1L, 0, MTProtoService.buildAck(emptyList()))
        val ack = parsed.single().second as MTProtoService.Incoming.Ack
        assertTrue(ack.msgIds.isEmpty())
    }

    // ------------------------------------------------------------ service msgs

    @Test
    fun parsesBadServerSalt() {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, MTProtoService.BAD_SERVER_SALT)
        TLStream.writeLong(out, 0x5000000000000004L)   // bad_msg_id
        TLStream.writeInt(out, 7)                      // bad_msg_seqno
        TLStream.writeInt(out, 48)                     // error_code
        TLStream.writeLong(out, 0x1122334455667788L)   // new_server_salt

        val parsed = MTProtoService.parse(1L, 0, out.toByteArray())
        val salt = parsed.single().second as MTProtoService.Incoming.BadSalt

        assertEquals(0x5000000000000004L, salt.badMsgId)
        assertEquals(48, salt.errorCode)
        assertEquals(0x1122334455667788L, salt.newServerSalt)
    }

    @Test
    fun parsesBadMessageNotificationAndDetectsClockIssue() {
        fun build(code: Int): ByteArray {
            val out = ByteArrayOutputStream()
            TLStream.writeInt(out, MTProtoService.BAD_MSG_NOTIFICATION)
            TLStream.writeLong(out, 0x5000000000000004L)
            TLStream.writeInt(out, 3)
            TLStream.writeInt(out, code)
            return out.toByteArray()
        }

        for (code in listOf(16, 17)) {
            val msg = MTProtoService.parse(1L, 0, build(code)).single().second
                as MTProtoService.Incoming.BadMessage
            assertTrue("code $code must be a clock problem", msg.isClockProblem)
            assertEquals(code, msg.errorCode)
        }

        val other = MTProtoService.parse(1L, 0, build(32)).single().second
            as MTProtoService.Incoming.BadMessage
        assertFalse(other.isClockProblem)
    }

    @Test
    fun parsesNewSessionCreated() {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, MTProtoService.NEW_SESSION_CREATED)
        TLStream.writeLong(out, 111L)
        TLStream.writeLong(out, 222L)
        TLStream.writeLong(out, 333L)

        val session = MTProtoService.parse(1L, 0, out.toByteArray()).single().second
            as MTProtoService.Incoming.NewSession
        assertEquals(111L, session.firstMsgId)
        assertEquals(333L, session.serverSalt)
    }

    @Test
    fun parsesRpcResult() {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, MTProtoService.RPC_RESULT)
        TLStream.writeLong(out, 0x5000000000000004L)
        out.write(aligned("payload"))

        val result = MTProtoService.parse(1L, 0, out.toByteArray()).single().second
            as MTProtoService.Incoming.RpcResult
        assertEquals(0x5000000000000004L, result.requestMsgId)
        assertArrayEquals(aligned("payload"), result.result)
    }

    @Test
    fun parsesRpcError() {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, MTProtoService.RPC_ERROR)
        TLStream.writeInt(out, 420)
        TLStream.writeString(out, "FLOOD_WAIT_30")

        val error = MTProtoService.parse(1L, 0, out.toByteArray()).single().second
            as MTProtoService.Incoming.RpcError
        assertEquals(420, error.errorCode)
        assertEquals("FLOOD_WAIT_30", error.errorMessage)
    }

    @Test
    fun unknownConstructorIsPassedThrough() {
        val body = ByteArray(8)
        MTProtoCrypto.writeIntLE(body, 0, 0x11223344)
        val unknown = MTProtoService.parse(1L, 0, body).single().second
            as MTProtoService.Incoming.Unknown
        assertEquals(0x11223344, unknown.constructorId)
    }

    @Test
    fun pingBuildersProduceCorrectLayout() {
        val ping = MTProtoService.buildPing(0xAABBL)
        assertEquals(12, ping.size)
        assertEquals(MTProtoService.PING, MTProtoCrypto.readIntLE(ping, 0))
        assertEquals(0xAABBL, MTProtoCrypto.readLongLE(ping, 4))

        val delayed = MTProtoService.buildPingDelayDisconnect(0xCCDDL, 75)
        assertEquals(16, delayed.size)
        assertEquals(MTProtoService.PING_DELAY_DISCONNECT, MTProtoCrypto.readIntLE(delayed, 0))
        assertEquals(75, MTProtoCrypto.readIntLE(delayed, 12))
    }

    // ------------------------------------------------------------ end-to-end

    /** Контейнер должен проходить через реальное шифрование сессии. */
    @Test
    fun containerSurvivesEncryption() {
        val key = ByteArray(256).also { rnd.nextBytes(it) }
        val session = MTProtoSession(key, isClient = true)

        val container = MTProtoService.buildContainer(
            listOf(
                MTProtoService.Message(4L, 2, MTProtoService.buildAck(listOf(99L))),
                MTProtoService.Message(8L, 4, MTProtoService.buildPing(1234L))
            )
        )
        val envelope = session.encrypt(container)

        val decrypted = MTProtoCrypto.decrypt(key, envelope, true)
        val len = MTProtoCrypto.readIntLE(decrypted, 28)
        val body = decrypted.copyOfRange(32, 32 + len)

        val parsed = MTProtoService.parse(0L, 0, body)
        assertEquals(2, parsed.size)
        assertTrue(parsed[0].second is MTProtoService.Incoming.Ack)
    }
}
