package com.example.mtproto

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Транспортный клиент MTProto: TCP + obfuscated2.
 *
 * Задача — сделать поток неотличимым от случайного шума, чтобы ТСПУ/DPI
 * не могли опознать его по сигнатурам.
 *
 * Как это устроено:
 *  1. Первыми уходят 64 случайных байта (nonce). Из них выводятся ключи
 *     AES-CTR для обоих направлений.
 *  2. Всё дальнейшее шифруется AES-CTR. Поток не имеет ни заголовков,
 *     ни постоянных полей — статистически это белый шум.
 *  3. Длина пакета передаётся внутри шифрованного слоя (abridged framing),
 *     поэтому границы сообщений снаружи тоже не видны.
 *
 * Класс синхронный и работает с «сырым» Socket: его оборачивает
 * MTProtoConnection, добавляя корутины и переподключение.
 */
class MTProtoTransport(
    private val host: String,
    private val port: Int,
    private val dcId: Int,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 75_000,
    private val socketFactory: () -> Socket = { Socket() }
) {

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var obfuscator: MTProtoObfuscator? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    /**
     * Устанавливает TCP-соединение и отправляет обфускационный заголовок.
     * Блокирующий вызов — запускать на Dispatchers.IO.
     */
    @Throws(IOException::class)
    fun connect() {
        close()

        val s = socketFactory()
        // TCP_NODELAY: без него мелкие пакеты копятся в буфере Nagle,
        // и сообщения в чате уходят с задержкой до 200 мс.
        s.tcpNoDelay = true
        s.keepAlive = true
        s.soTimeout = readTimeoutMs
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)

        val obf = MTProtoObfuscator.create(dcId)
        val out = s.getOutputStream()
        // Заголовок уходит в открытом виде — сервер по нему выводит те же ключи.
        out.write(obf.initialPayload)
        out.flush()

        socket = s
        input = s.getInputStream()
        output = out
        obfuscator = obf
    }

    /** Отправляет один MTProto-пакет (конверт целиком). */
    @Throws(IOException::class)
    fun send(payload: ByteArray) {
        val obf = obfuscator ?: throw IOException("transport not connected")
        val out = output ?: throw IOException("transport not connected")
        val framed = MTProtoObfuscator.frame(payload)
        synchronized(this) {
            out.write(obf.encrypt(framed))
            out.flush()
        }
    }

    /**
     * Читает один пакет. Блокирует до прихода данных или таймаута.
     *
     * @throws TransportError если сервер вернул код ошибки (например -404).
     */
    @Throws(IOException::class)
    fun receive(): ByteArray {
        val obf = obfuscator ?: throw IOException("transport not connected")
        val inp = input ?: throw IOException("transport not connected")

        // Первый байт: длина в 4-байтных словах, либо 0x7F = длина в 3 байтах.
        val firstByte = obf.decrypt(readExactly(inp, 1))[0].toInt() and 0xFF
        val words = if (firstByte < 0x7F) {
            firstByte
        } else {
            val ext = obf.decrypt(readExactly(inp, 3))
            (ext[0].toInt() and 0xFF) or
                ((ext[1].toInt() and 0xFF) shl 8) or
                ((ext[2].toInt() and 0xFF) shl 16)
        }

        if (words <= 0) throw IOException("invalid frame length: $words")
        // Ограничение: 16 МБ. Иначе вредоносный сервер закажет
        // аллокацию гигабайта и приложение упадёт по OutOfMemory.
        if (words > 4 * 1024 * 1024) throw IOException("frame too large: $words words")

        val body = obf.decrypt(readExactly(inp, words * 4))

        // Пакет длиной 4 байта — транспортная ошибка, а не сообщение.
        if (body.size == 4) {
            val code = MTProtoCrypto.readIntLE(body, 0)
            throw TransportError(code)
        }
        return body
    }

    /**
     * InputStream.read() может вернуть меньше запрошенного — при плохой
     * мобильной сети это происходит регулярно. Дочитываем в цикле.
     */
    @Throws(IOException::class)
    private fun readExactly(inp: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = inp.read(buf, read, count - read)
            if (n < 0) throw IOException("connection closed by peer")
            read += n
        }
        return buf
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        input = null
        output = null
        obfuscator = null
    }

    /**
     * Транспортная ошибка MTProto. Коды передаются как отрицательные числа,
     * например -404 (неизвестный auth_key), -429 (слишком много запросов).
     */
    class TransportError(val code: Int) : IOException("MTProto transport error: $code") {
        /** -404 означает, что сервер не знает наш auth_key: нужен новый handshake. */
        val requiresNewAuthKey: Boolean get() = code == -404
    }
}
