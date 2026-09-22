package com.example.mtproto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.min
import kotlin.random.Random

/**
 * Живое соединение с дата-центром: корутины, автопереподключение, ping.
 *
 * Рассчитано на мобильные сети: переход Wi-Fi → LTE, туннели в метро,
 * молчаливые обрывы у операторов. Соединение восстанавливается само,
 * очередь исходящих переживает разрыв.
 *
 * Интеграция с KuoteX: слой отдаёт Flow, как и WebSocketManager,
 * поэтому подключается к существующей шине событий один в один.
 */
class MTProtoConnection(
    private val host: String,
    private val port: Int,
    private val dcId: Int,
    private val session: MTProtoSession,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val transportFactory: () -> MTProtoTransport = {
        MTProtoTransport(host, port, dcId)
    }
) {

    enum class Status { DISCONNECTED, CONNECTING, CONNECTED, FAILED_AUTH }

    private val _status = MutableStateFlow(Status.DISCONNECTED)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Расшифрованные тела входящих сообщений. */
    private val _incoming = MutableSharedFlow<MTProtoSession.Incoming>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incoming: SharedFlow<MTProtoSession.Incoming> = _incoming.asSharedFlow()

    /** Сообщения, ждущие отправки: переживают разрыв связи. */
    private val pending = ArrayDeque<ByteArray>()
    private val pendingLock = Mutex()
    private val sendLock = Mutex()

    private var transport: MTProtoTransport? = null
    private var readerJob: Job? = null
    private var pingJob: Job? = null

    @Volatile
    private var running = false

    /** Сколько неудачных попыток подряд — определяет задержку backoff. */
    private var failureCount = 0

    // ------------------------------------------------------------ lifecycle

    fun start() {
        if (running) return
        running = true
        readerJob = scope.launch { connectionLoop() }
        pingJob = scope.launch { pingLoop() }
    }

    fun stop() {
        running = false
        pingJob?.cancel()
        readerJob?.cancel()
        transport?.close()
        transport = null
        _status.value = Status.DISCONNECTED
    }

    // ------------------------------------------------------------ sending

    /**
     * Ставит сообщение в очередь и пытается отправить.
     * Если связи нет — сообщение дождётся восстановления, а не потеряется.
     */
    suspend fun send(body: ByteArray, contentRelated: Boolean = true) {
        val envelope = session.encrypt(body, contentRelated)
        pendingLock.withLock { pending.addLast(envelope) }
        flushPending()
    }

    private suspend fun flushPending() {
        val active = transport ?: return
        if (!active.isConnected) return

        sendLock.withLock {
            while (true) {
                val next = pendingLock.withLock { pending.firstOrNull() } ?: break
                try {
                    withContext(Dispatchers.IO) { active.send(next) }
                    pendingLock.withLock {
                        if (pending.isNotEmpty()) pending.removeFirst()
                    }
                } catch (e: IOException) {
                    // Не удалось — оставляем в очереди, переподключение повторит.
                    break
                }
            }
        }
    }

    // ------------------------------------------------------------ loops

    private suspend fun connectionLoop() {
        while (running && scope.isActive) {
            try {
                _status.value = Status.CONNECTING
                val t = transportFactory()
                withContext(Dispatchers.IO) { t.connect() }
                transport = t
                failureCount = 0
                _status.value = Status.CONNECTED

                flushPending()
                readLoop(t)
            } catch (e: MTProtoTransport.TransportError) {
                if (e.requiresNewAuthKey) {
                    // Сервер забыл наш ключ — переподключение не поможет,
                    // нужен новый handshake. Сообщаем наверх и останавливаемся.
                    _status.value = Status.FAILED_AUTH
                    running = false
                    return
                }
                onFailure()
            } catch (e: MTProtoCrypto.SecurityViolation) {
                // Криптографическое нарушение: соединение скомпрометировано.
                // Рвём и переподключаемся с чистого листа.
                onFailure()
            } catch (e: IOException) {
                onFailure()
            } catch (e: Exception) {
                if (!running) return
                onFailure()
            }

            if (!running) return
            delay(backoffDelayMs())
        }
    }

    private suspend fun readLoop(active: MTProtoTransport) {
        while (running && active.isConnected) {
            val packet = withContext(Dispatchers.IO) { active.receive() }
            try {
                val message = session.decrypt(packet)
                _incoming.emit(message)
            } catch (e: MTProtoCrypto.SecurityViolation) {
                // Одно битое сообщение не повод рвать соединение,
                // но подделка — повод. Разрываем: безопасность важнее.
                throw e
            }
        }
    }

    /**
     * Пинг с отложенным закрытием: сервер сам закроет соединение,
     * если клиент пропал. Заодно не даёт NAT оператора «схлопнуть»
     * простаивающее соединение — типичная причина зависаний в мобильных сетях.
     */
    private suspend fun pingLoop() {
        while (running && scope.isActive) {
            delay(PING_INTERVAL_MS)
            if (_status.value != Status.CONNECTED) continue
            try {
                send(buildPingDelayDisconnect(), contentRelated = false)
            } catch (_: Exception) {
                // Не страшно: connectionLoop переподключится.
            }
        }
    }

    private fun onFailure() {
        failureCount++
        transport?.close()
        transport = null
        _status.value = Status.DISCONNECTED
    }

    /**
     * Экспоненциальный backoff со случайным разбросом (jitter).
     *
     * Jitter обязателен: без него все клиенты после сбоя дата-центра
     * ломятся одновременно и добивают сервер (thundering herd).
     */
    private fun backoffDelayMs(): Long {
        val exponential = min(
            MAX_BACKOFF_MS,
            BASE_BACKOFF_MS shl min(failureCount, 6)
        )
        val jitter = Random.nextLong(exponential / 2 + 1)
        return exponential / 2 + jitter
    }

    /** ping_delay_disconnect#f3427b8c ping_id:long disconnect_delay:int */
    private fun buildPingDelayDisconnect(): ByteArray {
        val out = ByteArray(16)
        MTProtoCrypto.writeIntLE(out, 0, 0xf3427b8c.toInt())
        MTProtoCrypto.writeLongLE(out, 4, Random.nextLong())
        MTProtoCrypto.writeIntLE(out, 12, PING_DISCONNECT_DELAY_SEC)
        return out
    }

    companion object {
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 60_000L
        private const val PING_INTERVAL_MS = 30_000L
        private const val PING_DISCONNECT_DELAY_SEC = 75
    }
}
