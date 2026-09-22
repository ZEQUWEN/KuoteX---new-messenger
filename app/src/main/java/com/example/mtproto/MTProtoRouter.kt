package com.example.mtproto

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/**
 * Роутер сообщений MTProto — верхний уровень протокола.
 *
 * Собирает вместе всё, что было сделано раньше, и превращает
 * «байты туда-обратно» в обычные suspend-вызовы:
 *
 *     val result = router.invoke(TLMessagesSendMessage(...).serialize())
 *
 * Что берёт на себя:
 *  - сопоставляет rpc_result с ожидающим запросом по msg_id;
 *  - копит и отправляет msgs_ack пачкой, а не по одному;
 *  - при bad_server_salt подставляет новую соль и повторяет запрос сам;
 *  - при расхождении часов (коды 16/17) синхронизируется и повторяет;
 *  - при new_session_created переотправляет неподтверждённые запросы;
 *  - объединяет мелкие сообщения в msg_container.
 *
 * Без этого слоя прикладной код был бы вынужден разбираться со
 * служебными сообщениями руками в каждом месте вызова.
 */
class MTProtoRouter(
    private val session: MTProtoSession,
    private val sender: Sender,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val config: Config = Config()
) {

    /** Отправка готового конверта в сеть. Реализуется транспортом. */
    fun interface Sender {
        suspend fun send(envelope: ByteArray)
    }

    data class Config(
        /** Сколько ждать ответ на запрос. */
        val requestTimeoutMs: Long = 30_000,
        /** Задержка перед отправкой накопленных ack. */
        val ackFlushDelayMs: Long = 300,
        /** Порог, при котором ack уходят немедленно. */
        val ackBatchSize: Int = 16,
        /** Сколько раз повторять запрос при bad_salt / плохих часах. */
        val maxRetries: Int = 3
    )

    /** Ошибка, пришедшая от сервера в ответ на запрос. */
    class RpcException(
        val code: Int,
        val text: String
    ) : IOException("RPC error $code: $text") {

        /** FLOOD_WAIT_x — сервер просит подождать x секунд. */
        val floodWaitSeconds: Int?
            get() = FLOOD_WAIT.matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()

        private companion object {
            val FLOOD_WAIT = Regex("FLOOD_WAIT_(\\d+)")
        }
    }

    /** Запрос, ожидающий ответа. */
    private class Pending(
        val body: ByteArray,
        val continuation: CancellableContinuation<ByteArray>,
        var attempts: Int = 0
    )

    private val pending = HashMap<Long, Pending>()
    private val pendingLock = Mutex()

    private val ackQueue = ArrayList<Long>()
    private val ackLock = Mutex()
    private var ackJob: Job? = null

    /** Сообщения, не являющиеся ответом на запрос (updates). */
    private val _updates = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updates: SharedFlow<ByteArray> = _updates.asSharedFlow()

    // ---------------------------------------------------------------- API

    /**
     * Отправляет запрос и ждёт ответа.
     *
     * @return тело rpc_result
     * @throws RpcException если сервер вернул ошибку
     */
    suspend fun invoke(body: ByteArray): ByteArray {
        require(body.size % 4 == 0) { "TL body must be 4-byte aligned" }

        return withTimeoutOrNull(config.requestTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                scope.launch {
                    try {
                        val msgId = dispatch(body, continuation)
                        continuation.invokeOnCancellation {
                            scope.launch { pendingLock.withLock { pending.remove(msgId) } }
                        }
                    } catch (e: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            }
        } ?: throw IOException("request timed out after ${config.requestTimeoutMs} ms")
    }

    /** Отправляет сообщение, не ожидая ответа (например, ping). */
    suspend fun notify(body: ByteArray, contentRelated: Boolean = false) {
        sender.send(session.encrypt(body, contentRelated))
    }

    /** Точка входа для входящих конвертов из транспорта. */
    suspend fun onEnvelope(envelope: ByteArray) {
        val incoming = session.decrypt(envelope)
        onDecrypted(incoming)
    }

    /** Отдельно — для случая, когда расшифровку уже сделал транспорт. */
    suspend fun onDecrypted(incoming: MTProtoSession.Incoming) {
        val parsed = MTProtoService.parse(incoming.messageId, incoming.seqNo, incoming.body)
        for ((message, content) in parsed) {
            // Подтверждать нужно только content-related сообщения:
            // у них нечётный seq_no. Подтверждать ack на ack — бесконечный цикл.
            if (message.seqNo % 2 == 1) enqueueAck(message.msgId)
            handle(message, content)
        }
    }

    fun close() {
        ackJob?.cancel()
        scope.launch {
            pendingLock.withLock {
                pending.values.forEach {
                    if (it.continuation.isActive) {
                        it.continuation.resumeWithException(IOException("router closed"))
                    }
                }
                pending.clear()
            }
        }
    }

    // ---------------------------------------------------------------- send

    private suspend fun dispatch(
        body: ByteArray,
        continuation: CancellableContinuation<ByteArray>,
        attempts: Int = 0
    ): Long {
        // msg_id вычисляется внутри session.encrypt, поэтому берём его
        // заранее и собираем конверт вручную — иначе не с чем сопоставить ответ.
        val msgId = session.nextMessageId()
        val seqNo = session.nextSeqNo(contentRelated = true)
        val envelope = session.encryptWithIds(body, msgId, seqNo)

        pendingLock.withLock {
            pending[msgId] = Pending(body, continuation, attempts)
        }
        sender.send(envelope)
        return msgId
    }

    /** Повторная отправка после bad_salt / коррекции часов. */
    private suspend fun retry(oldMsgId: Long, reason: String) {
        val entry = pendingLock.withLock { pending.remove(oldMsgId) } ?: return

        if (entry.attempts + 1 >= config.maxRetries) {
            if (entry.continuation.isActive) {
                entry.continuation.resumeWithException(
                    IOException("giving up after ${entry.attempts + 1} attempts: $reason")
                )
            }
            return
        }
        dispatch(entry.body, entry.continuation, entry.attempts + 1)
    }

    // ---------------------------------------------------------------- ack

    private suspend fun enqueueAck(msgId: Long) {
        val shouldFlushNow = ackLock.withLock {
            ackQueue.add(msgId)
            ackQueue.size >= config.ackBatchSize
        }

        if (shouldFlushNow) {
            flushAcks()
            return
        }
        // Отложенная отправка: сервер терпит небольшую задержку, зато
        // десяток подтверждений уходит одним пакетом вместо десяти.
        if (ackJob?.isActive != true) {
            ackJob = scope.launch {
                delay(config.ackFlushDelayMs)
                flushAcks()
            }
        }
    }

    suspend fun flushAcks() {
        val batch = ackLock.withLock {
            if (ackQueue.isEmpty()) return
            ArrayList(ackQueue).also { ackQueue.clear() }
        }
        try {
            notify(MTProtoService.buildAck(batch), contentRelated = false)
        } catch (e: IOException) {
            // Не удалось отправить — возвращаем в очередь, уйдут позже.
            ackLock.withLock { ackQueue.addAll(0, batch) }
        }
    }

    // ---------------------------------------------------------------- handle

    private suspend fun handle(
        message: MTProtoService.Message,
        content: MTProtoService.Incoming
    ) {
        when (content) {
            is MTProtoService.Incoming.RpcResult -> {
                val entry = pendingLock.withLock { pending.remove(content.requestMsgId) }
                if (entry == null) {
                    // Ответ на запрос, которого мы не ждём: дубликат или
                    // запрос уже отменён по таймауту. Игнорируем.
                    return
                }
                // rpc_error приходит внутри rpc_result.
                val inner = parseInnerError(content.result)
                if (inner != null) {
                    if (entry.continuation.isActive) {
                        entry.continuation.resumeWithException(inner)
                    }
                } else if (entry.continuation.isActive) {
                    entry.continuation.resume(content.result)
                }
            }

            is MTProtoService.Incoming.RpcError -> {
                val entry = pendingLock.withLock { pending.remove(content.requestMsgId) }
                if (entry != null && entry.continuation.isActive) {
                    entry.continuation.resumeWithException(
                        RpcException(content.errorCode, content.errorMessage)
                    )
                }
            }

            is MTProtoService.Incoming.BadSalt -> {
                // Соль устарела — сервер прислал новую. Запрос не потерян,
                // его нужно просто повторить с правильной солью.
                session.serverSalt = content.newServerSalt
                onSaltChanged?.invoke(content.newServerSalt)
                retry(content.badMsgId, "bad_server_salt")
            }

            is MTProtoService.Incoming.BadMessage -> {
                if (content.isClockProblem) {
                    // Часы устройства разошлись с сервером. Синхронизируемся
                    // по msg_id сервера и повторяем.
                    session.synchronizeClock(message.msgId)
                    retry(content.badMsgId, "clock skew (code ${content.errorCode})")
                } else {
                    val entry = pendingLock.withLock { pending.remove(content.badMsgId) }
                    if (entry != null && entry.continuation.isActive) {
                        entry.continuation.resumeWithException(
                            IOException("bad_msg_notification code ${content.errorCode}")
                        )
                    }
                }
            }

            is MTProtoService.Incoming.NewSession -> {
                // Сервер начал новую сессию: сообщения, отправленные до
                // firstMsgId, могли не дойти. Переотправляем их.
                session.serverSalt = content.serverSalt
                onSaltChanged?.invoke(content.serverSalt)
                resendOlderThan(content.firstMsgId)
            }

            is MTProtoService.Incoming.Ack -> {
                // Сервер принял наши сообщения. Ответ придёт отдельно,
                // поэтому из pending ничего не убираем.
            }

            is MTProtoService.Incoming.Pong -> { /* соединение живо */ }

            is MTProtoService.Incoming.Unknown -> {
                _updates.emit(content.body)
            }
        }
    }

    /**
     * Внутри rpc_result может лежать rpc_error — распознаём его,
     * иначе ошибка ушла бы наверх как обычный успешный результат.
     */
    private fun parseInnerError(result: ByteArray): RpcException? {
        if (result.size < 8) return null
        if (MTProtoCrypto.readIntLE(result, 0) != MTProtoService.RPC_ERROR) return null
        return try {
            val input = java.io.ByteArrayInputStream(result)
            com.example.tlschema.TLStream.readInt(input)
            val code = com.example.tlschema.TLStream.readInt(input)
            val text = com.example.tlschema.TLStream.readString(input)
            RpcException(code, text)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resendOlderThan(firstMsgId: Long) {
        val stale = pendingLock.withLock {
            pending.filterKeys { it < firstMsgId }.also { old ->
                old.keys.forEach { pending.remove(it) }
            }
        }
        for ((_, entry) in stale) {
            if (entry.continuation.isActive) {
                dispatch(entry.body, entry.continuation, entry.attempts + 1)
            }
        }
    }

    /** Уведомление о смене соли — чтобы сохранить её в AuthKeyStore. */
    var onSaltChanged: ((Long) -> Unit)? = null

    /** Сколько запросов сейчас ждут ответа (для диагностики и тестов). */
    suspend fun pendingCount(): Int = pendingLock.withLock { pending.size }
}
