package com.example.mtproto

import com.example.tlschema.TLStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Тесты роутера против фиктивного сервера.
 *
 * Проверяют не «счастливый путь», а поведение при сбоях: устаревшая
 * соль, разъехавшиеся часы, потеря сессии, ошибки RPC.
 */
class MTProtoRouterTest {

    private val rnd = SecureRandom()
    private fun randomKey() = ByteArray(256).also { rnd.nextBytes(it) }

    private fun aligned(text: String): ByteArray {
        val b = text.toByteArray(Charsets.UTF_8)
        return b + ByteArray((4 - b.size % 4) % 4)
    }

    /**
     * Сервер, который расшифровывает запросы клиента и отвечает
     * по-настоящему зашифрованными конвертами.
     */
    private inner class FakeServer(val key: ByteArray, val clientSessionId: Long) {
        val received = Collections.synchronizedList(ArrayList<MTProtoSession.Incoming>())
        val acked = Collections.synchronizedList(ArrayList<Long>())
        var serverSalt = 0L
        private var msgIdCounter = (System.currentTimeMillis() / 1000L shl 32) or 1L

        fun nextMsgId(): Long {
            msgIdCounter += 4
            return msgIdCounter
        }

        /** Расшифровывает конверт клиента. */
        fun receive(envelope: ByteArray): MTProtoSession.Incoming {
            val decrypted = MTProtoCrypto.decrypt(key, envelope, true)
            val salt = MTProtoCrypto.readLongLE(decrypted, 0)
            val sid = MTProtoCrypto.readLongLE(decrypted, 8)
            val msgId = MTProtoCrypto.readLongLE(decrypted, 16)
            val seqNo = MTProtoCrypto.readIntLE(decrypted, 24)
            val len = MTProtoCrypto.readIntLE(decrypted, 28)
            val incoming = MTProtoSession.Incoming(
                salt, sid, msgId, seqNo, decrypted.copyOfRange(32, 32 + len)
            )
            received.add(incoming)

            // Запоминаем, какие msg_id клиент подтвердил.
            if (MTProtoCrypto.readIntLE(incoming.body, 0) == MTProtoService.MSGS_ACK) {
                val parsed = MTProtoService.parse(msgId, seqNo, incoming.body)
                (parsed.single().second as MTProtoService.Incoming.Ack).msgIds
                    .let { acked.addAll(it) }
            }
            return incoming
        }

        /** Собирает зашифрованный ответ сервера. */
        fun reply(body: ByteArray, contentRelated: Boolean = true): ByteArray {
            val msgId = nextMsgId()
            val seqNo = if (contentRelated) 1 else 0
            val payload = ByteArray(32 + body.size)
            MTProtoCrypto.writeLongLE(payload, 0, serverSalt)
            MTProtoCrypto.writeLongLE(payload, 8, clientSessionId)
            MTProtoCrypto.writeLongLE(payload, 16, msgId)
            MTProtoCrypto.writeIntLE(payload, 24, seqNo)
            MTProtoCrypto.writeIntLE(payload, 28, body.size)
            System.arraycopy(body, 0, payload, 32, body.size)
            return MTProtoCrypto.encrypt(key, payload, false)
        }

        fun rpcResult(requestMsgId: Long, result: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            TLStream.writeInt(out, MTProtoService.RPC_RESULT)
            TLStream.writeLong(out, requestMsgId)
            out.write(result)
            return out.toByteArray()
        }

        fun rpcError(requestMsgId: Long, code: Int, text: String): ByteArray {
            val inner = ByteArrayOutputStream()
            TLStream.writeInt(inner, MTProtoService.RPC_ERROR)
            TLStream.writeInt(inner, code)
            TLStream.writeString(inner, text)
            return rpcResult(requestMsgId, inner.toByteArray())
        }

        fun badServerSalt(badMsgId: Long, newSalt: Long): ByteArray {
            val out = ByteArrayOutputStream()
            TLStream.writeInt(out, MTProtoService.BAD_SERVER_SALT)
            TLStream.writeLong(out, badMsgId)
            TLStream.writeInt(out, 1)
            TLStream.writeInt(out, 48)
            TLStream.writeLong(out, newSalt)
            return out.toByteArray()
        }

        fun badMsgNotification(badMsgId: Long, code: Int): ByteArray {
            val out = ByteArrayOutputStream()
            TLStream.writeInt(out, MTProtoService.BAD_MSG_NOTIFICATION)
            TLStream.writeLong(out, badMsgId)
            TLStream.writeInt(out, 1)
            TLStream.writeInt(out, code)
            return out.toByteArray()
        }

        fun newSessionCreated(firstMsgId: Long, salt: Long): ByteArray {
            val out = ByteArrayOutputStream()
            TLStream.writeInt(out, MTProtoService.NEW_SESSION_CREATED)
            TLStream.writeLong(out, firstMsgId)
            TLStream.writeLong(out, 42L)
            TLStream.writeLong(out, salt)
            return out.toByteArray()
        }
    }

    /** Собирает связку session + router + сервер. */
    private inner class Harness(
        config: MTProtoRouter.Config = MTProtoRouter.Config(
            requestTimeoutMs = 5_000,
            ackFlushDelayMs = 50
        )
    ) {
        val key = randomKey()
        val session = MTProtoSession(key, isClient = true)
        val server = FakeServer(key, session.sessionId)

        /** Что сервер делает с каждым принятым запросом. */
        var responder: (MTProtoSession.Incoming) -> List<ByteArray> = { emptyList() }

        lateinit var router: MTProtoRouter

        init {
            router = MTProtoRouter(
                session = session,
                sender = { envelope ->
                    val incoming = server.receive(envelope)
                    for (reply in responder(incoming)) {
                        router.onEnvelope(reply)
                    }
                },
                config = config
            )
        }
    }

    // ---------------------------------------------------------------- basic

    @Test
    fun invokeReturnsRpcResult() = runBlocking {
        val h = Harness()
        val expected = aligned("result payload")
        h.responder = { incoming ->
            listOf(h.server.reply(h.server.rpcResult(incoming.messageId, expected)))
        }

        val result = h.router.invoke(aligned("request"))
        assertArrayEquals(expected, result)
        assertEquals(0, h.router.pendingCount())
    }

    @Test
    fun rpcErrorBecomesException() = runBlocking {
        val h = Harness()
        h.responder = { incoming ->
            listOf(h.server.reply(h.server.rpcError(incoming.messageId, 400, "CHAT_INVALID")))
        }

        try {
            h.router.invoke(aligned("request"))
            fail("must throw RpcException")
        } catch (e: MTProtoRouter.RpcException) {
            assertEquals(400, e.code)
            assertEquals("CHAT_INVALID", e.text)
        }
        assertEquals(0, h.router.pendingCount())
    }

    @Test
    fun floodWaitIsParsed() = runBlocking {
        val h = Harness()
        h.responder = { incoming ->
            listOf(h.server.reply(h.server.rpcError(incoming.messageId, 420, "FLOOD_WAIT_30")))
        }

        try {
            h.router.invoke(aligned("request"))
            fail("must throw")
        } catch (e: MTProtoRouter.RpcException) {
            assertEquals(30, e.floodWaitSeconds)
        }
    }

    @Test
    fun parallelRequestsGetTheirOwnAnswers() = runBlocking {
        val h = Harness()
        h.responder = { incoming ->
            // Эхо: ответ повторяет тело запроса.
            listOf(h.server.reply(h.server.rpcResult(incoming.messageId, incoming.body)))
        }

        val results = coroutineScope {
            (1..20).map { i ->
                async(Dispatchers.Default) {
                    val body = aligned("request-$i")
                    body to h.router.invoke(body)
                }
            }.awaitAll()
        }

        // Каждый запрос обязан получить именно свой ответ, а не чужой.
        for ((sent, received) in results) {
            assertArrayEquals(sent, received)
        }
        assertEquals(0, h.router.pendingCount())
    }

    @Test
    fun timesOutWhenServerSilent() = runBlocking {
        val h = Harness(MTProtoRouter.Config(requestTimeoutMs = 300))
        h.responder = { emptyList() }        // сервер молчит

        try {
            h.router.invoke(aligned("request"))
            fail("must time out")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("timed out"))
        }
    }

    // ---------------------------------------------------------------- recovery

    /** Устаревшая соль: роутер обязан подставить новую и повторить сам. */
    @Test
    fun retriesAfterBadServerSalt() = runBlocking {
        val h = Harness()
        val newSalt = 0x1122334455667788L
        val attempts = AtomicInteger()
        var saltSeen: Long? = null
        h.router.onSaltChanged = { saltSeen = it }

        h.responder = { incoming ->
            if (MTProtoCrypto.readIntLE(incoming.body, 0) == MTProtoService.MSGS_ACK) {
                emptyList()
            } else if (attempts.incrementAndGet() == 1) {
                listOf(h.server.reply(h.server.badServerSalt(incoming.messageId, newSalt)))
            } else {
                listOf(h.server.reply(h.server.rpcResult(incoming.messageId, aligned("ok"))))
            }
        }

        val result = h.router.invoke(aligned("request"))
        assertArrayEquals(aligned("ok"), result)
        assertEquals(2, attempts.get())
        assertEquals(newSalt, h.session.serverSalt)
        assertEquals(newSalt, saltSeen)
    }

    /** Часы разъехались (код 16/17): синхронизация и повтор. */
    @Test
    fun retriesAfterClockSkew() = runBlocking {
        val h = Harness()
        val attempts = AtomicInteger()

        h.responder = { incoming ->
            if (MTProtoCrypto.readIntLE(incoming.body, 0) == MTProtoService.MSGS_ACK) {
                emptyList()
            } else if (attempts.incrementAndGet() == 1) {
                listOf(h.server.reply(h.server.badMsgNotification(incoming.messageId, 16)))
            } else {
                listOf(h.server.reply(h.server.rpcResult(incoming.messageId, aligned("ok"))))
            }
        }

        assertArrayEquals(aligned("ok"), h.router.invoke(aligned("request")))
        assertEquals(2, attempts.get())
    }

    /** Не связанный с часами bad_msg должен завершить запрос ошибкой. */
    @Test
    fun failsOnNonClockBadMessage() = runBlocking {
        val h = Harness()
        h.responder = { incoming ->
            if (MTProtoCrypto.readIntLE(incoming.body, 0) == MTProtoService.MSGS_ACK) {
                emptyList()
            } else {
                listOf(h.server.reply(h.server.badMsgNotification(incoming.messageId, 32)))
            }
        }

        try {
            h.router.invoke(aligned("request"))
            fail("must fail")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("bad_msg_notification"))
        }
    }

    /** Бесконечный bad_salt не должен зациклить роутер. */
    @Test
    fun givesUpAfterMaxRetries() = runBlocking {
        val h = Harness(MTProtoRouter.Config(requestTimeoutMs = 5_000, maxRetries = 3))
        val attempts = AtomicInteger()

        h.responder = { incoming ->
            if (MTProtoCrypto.readIntLE(incoming.body, 0) == MTProtoService.MSGS_ACK) {
                emptyList()
            } else {
                attempts.incrementAndGet()
                listOf(h.server.reply(h.server.badServerSalt(incoming.messageId, 777L)))
            }
        }

        try {
            h.router.invoke(aligned("request"))
            fail("must give up")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("giving up"))
        }
        assertEquals(3, attempts.get())
    }

    /** new_session_created: неподтверждённые запросы уходят заново. */
    @Test
    fun resendsPendingAfterNewSession() = runBlocking {
        val h = Harness()
        val attempts = AtomicInteger()

        h.responder = { incoming ->
            if (MTProtoCrypto.readIntLE(incoming.body, 0) == MTProtoService.MSGS_ACK) {
                emptyList()
            } else if (attempts.incrementAndGet() == 1) {
                // Сессия пересоздана: firstMsgId больше нашего запроса,
                // значит запрос считается потерянным.
                listOf(h.server.reply(h.server.newSessionCreated(Long.MAX_VALUE, 555L)))
            } else {
                listOf(h.server.reply(h.server.rpcResult(incoming.messageId, aligned("ok"))))
            }
        }

        assertArrayEquals(aligned("ok"), h.router.invoke(aligned("request")))
        assertEquals(2, attempts.get())
        assertEquals(555L, h.session.serverSalt)
    }

    // ---------------------------------------------------------------- ack

    @Test
    fun acksContentRelatedMessages() = runBlocking {
        val h = Harness(MTProtoRouter.Config(ackFlushDelayMs = 30))
        h.responder = { incoming ->
            listOf(h.server.reply(h.server.rpcResult(incoming.messageId, aligned("ok"))))
        }

        h.router.invoke(aligned("request"))
        delay(200)                            // ждём отложенную отправку ack

        assertTrue("server must receive an ack", h.server.acked.isNotEmpty())
    }

    /**
     * Подтверждать ack на ack нельзя — иначе стороны будут бесконечно
     * обмениваться подтверждениями.
     */
    @Test
    fun doesNotAckServiceMessages() = runBlocking {
        val h = Harness(MTProtoRouter.Config(ackFlushDelayMs = 30))

        // Присылаем ack от сервера (чётный seq_no = служебное).
        val serverAck = h.server.reply(
            MTProtoService.buildAck(listOf(4L, 8L)), contentRelated = false
        )
        h.router.onEnvelope(serverAck)
        delay(150)

        val sentAcks = h.server.received.count {
            it.body.size >= 4 &&
                MTProtoCrypto.readIntLE(it.body, 0) == MTProtoService.MSGS_ACK
        }
        assertEquals("must not ack a service message", 0, sentAcks)
    }

    @Test
    fun batchesAcksIntoSinglePacket() = runBlocking {
        val h = Harness(MTProtoRouter.Config(ackFlushDelayMs = 100, ackBatchSize = 100))

        // Десять content-related сообщений от сервера.
        repeat(10) {
            h.router.onEnvelope(h.server.reply(aligned("update-$it"), contentRelated = true))
        }
        delay(300)

        val ackPackets = h.server.received.count {
            it.body.size >= 4 &&
                MTProtoCrypto.readIntLE(it.body, 0) == MTProtoService.MSGS_ACK
        }
        assertEquals("10 messages must produce 1 ack packet", 1, ackPackets)
        assertEquals(10, h.server.acked.size)
    }

    @Test
    fun flushesImmediatelyWhenBatchIsFull() = runBlocking {
        val h = Harness(MTProtoRouter.Config(ackFlushDelayMs = 10_000, ackBatchSize = 3))

        repeat(3) {
            h.router.onEnvelope(h.server.reply(aligned("update-$it"), contentRelated = true))
        }
        delay(100)                            // намного меньше ackFlushDelayMs

        assertEquals(3, h.server.acked.size)
    }

    // ---------------------------------------------------------------- updates

    @Test
    fun unknownMessagesGoToUpdatesFlow() = runBlocking {
        val h = Harness()
        val update = ByteArray(12).also {
            MTProtoCrypto.writeIntLE(it, 0, 0x11223344)
        }

        val collected = CompletableDeferred<ByteArray>()
        val watcher = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = watcher.launch {
            h.router.updates.collect { collected.complete(it) }
        }
        delay(100)

        h.router.onEnvelope(h.server.reply(update, contentRelated = true))

        val received = withTimeout(2000) { collected.await() }
        assertEquals(0x11223344, MTProtoCrypto.readIntLE(received, 0))
        job.cancel()
    }

    /** Контейнер должен раскрываться: два ответа в одном конверте. */
    @Test
    fun handlesContainerWithTwoResults() = runBlocking {
        val h = Harness()
        val firstBody = aligned("first")
        val secondBody = aligned("second")

        val ids = Collections.synchronizedList(ArrayList<Long>())
        h.responder = { incoming ->
            if (MTProtoCrypto.readIntLE(incoming.body, 0) == MTProtoService.MSGS_ACK) {
                emptyList()
            } else {
                ids.add(incoming.messageId)
                if (ids.size < 2) {
                    emptyList()               // копим два запроса
                } else {
                    // Отвечаем на оба одним контейнером.
                    val container = MTProtoService.buildContainer(
                        listOf(
                            MTProtoService.Message(
                                h.server.nextMsgId(), 1,
                                h.server.rpcResult(ids[0], firstBody)
                            ),
                            MTProtoService.Message(
                                h.server.nextMsgId(), 3,
                                h.server.rpcResult(ids[1], secondBody)
                            )
                        )
                    )
                    listOf(h.server.reply(container))
                }
            }
        }

        val results = coroutineScope {
            val a = async(Dispatchers.Default) { h.router.invoke(aligned("req-1")) }
            delay(100)
            val b = async(Dispatchers.Default) { h.router.invoke(aligned("req-2")) }
            listOf(a.await(), b.await())
        }

        assertArrayEquals(firstBody, results[0])
        assertArrayEquals(secondBody, results[1])
    }

    @Test
    fun ignoresResultForUnknownRequest() = runBlocking {
        val h = Harness()
        // Ответ на запрос, которого не было — не должен ничего сломать.
        h.router.onEnvelope(
            h.server.reply(h.server.rpcResult(0xDEADBEEFL, aligned("orphan")))
        )
        assertEquals(0, h.router.pendingCount())
    }

    @Test
    fun closeReleasesPendingRequests() = runBlocking {
        val h = Harness(MTProtoRouter.Config(requestTimeoutMs = 10_000))
        h.responder = { emptyList() }

        val caller = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val deferred = caller.async {
            try {
                h.router.invoke(aligned("request"))
                "no error"
            } catch (e: IOException) {
                e.message ?: ""
            }
        }
        delay(200)
        h.router.close()

        val message = withTimeout(3000) { deferred.await() }
        assertTrue("expected close error, got: $message", message.contains("closed"))
    }
}
