package com.example.mtproto

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.DataInputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Тесты транспорта против настоящего TCP-сервера на localhost.
 * Сеть реальная, но локальная — тесты быстрые и не требуют интернета.
 */
class MTProtoTransportTest {

    private val rnd = SecureRandom()
    private var server: ServerSocket? = null

    @After
    fun tearDown() {
        try { server?.close() } catch (_: Exception) { }
    }

    /**
     * Серверная сторона obfuscated2: выводит ключи из тех же 64 байт,
     * но направления зеркальные относительно клиента.
     */
    private class ServerObfuscation(header: ByteArray) {
        val decryptor: Cipher   // расшифровывает то, что шлёт клиент
        val encryptor: Cipher   // шифрует то, что уходит клиенту

        init {
            // Клиент шифрует ключом из байт 8..56 в прямом порядке.
            val encKey = header.copyOfRange(8, 40)
            val encIv = header.copyOfRange(40, 56)
            // Обратное направление — те же байты задом наперёд.
            val reversed = header.copyOfRange(8, 56).reversedArray()
            val decKey = reversed.copyOfRange(0, 32)
            val decIv = reversed.copyOfRange(32, 48)

            decryptor = ctr(encKey, encIv)
            encryptor = ctr(decKey, decIv)

            // Клиент прогнал через свой шифратор все 64 байта заголовка,
            // поэтому его CTR-поток сдвинут на 64 байта. Сервер обязан
            // сдвинуть свой дешифратор ровно так же, иначе потоки разъедутся.
            decryptor.update(ByteArray(64))
        }

        private fun ctr(key: ByteArray, iv: ByteArray) =
            Cipher.getInstance("AES/CTR/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }

        fun frameOut(payload: ByteArray): ByteArray {
            val words = payload.size / 4
            val framed = if (words < 0x7F) byteArrayOf(words.toByte()) + payload
            else byteArrayOf(0x7F,
                (words and 0xFF).toByte(),
                ((words shr 8) and 0xFF).toByte(),
                ((words shr 16) and 0xFF).toByte()) + payload
            return encryptor.update(framed)
        }
    }

    private fun readFrame(inp: DataInputStream, obf: ServerObfuscation): ByteArray {
        val first = obf.decryptor.update(ByteArray(1).also { inp.readFully(it) })[0].toInt() and 0xFF
        val words = if (first < 0x7F) first else {
            val ext = obf.decryptor.update(ByteArray(3).also { inp.readFully(it) })
            (ext[0].toInt() and 0xFF) or ((ext[1].toInt() and 0xFF) shl 8) or
                ((ext[2].toInt() and 0xFF) shl 16)
        }
        return obf.decryptor.update(ByteArray(words * 4).also { inp.readFully(it) })
    }

    // ------------------------------------------------------------ tests

    @Test
    fun echoThroughObfuscatedChannel() {
        val srv = ServerSocket(0).also { server = it }
        val ready = CountDownLatch(1)

        val thread = Thread {
            srv.accept().use { sock ->
                val inp = DataInputStream(sock.getInputStream())
                val header = ByteArray(64).also { inp.readFully(it) }
                val obf = ServerObfuscation(header)
                ready.countDown()
                repeat(3) {
                    val payload = readFrame(inp, obf)
                    sock.getOutputStream().write(obf.frameOut(payload))  // эхо
                    sock.getOutputStream().flush()
                }
            }
        }
        thread.isDaemon = true
        thread.start()

        val transport = MTProtoTransport("127.0.0.1", srv.localPort, dcId = 2)
        transport.connect()
        assertTrue(ready.await(5, TimeUnit.SECONDS))

        for (size in listOf(16, 64, 1024)) {
            val payload = ByteArray(size).also { rnd.nextBytes(it) }
            transport.send(payload)
            assertArrayEquals(payload, transport.receive())
        }
        transport.close()
    }

    /** Пакеты длиной >= 0x7F слов используют расширенный заголовок. */
    @Test
    fun handlesLargePacketsWithExtendedLength() {
        val srv = ServerSocket(0).also { server = it }
        Thread {
            srv.accept().use { sock ->
                val inp = DataInputStream(sock.getInputStream())
                val header = ByteArray(64).also { inp.readFully(it) }
                val obf = ServerObfuscation(header)
                val payload = readFrame(inp, obf)
                sock.getOutputStream().write(obf.frameOut(payload))
                sock.getOutputStream().flush()
            }
        }.apply { isDaemon = true }.start()

        val transport = MTProtoTransport("127.0.0.1", srv.localPort, dcId = 2)
        transport.connect()
        val big = ByteArray(64 * 1024).also { rnd.nextBytes(it) }
        transport.send(big)
        assertArrayEquals(big, transport.receive())
        transport.close()
    }

    /** Транспортная ошибка (4 байта) должна распознаваться, а не считаться данными. */
    @Test
    fun recognizesTransportErrorCode() {
        val srv = ServerSocket(0).also { server = it }
        Thread {
            srv.accept().use { sock ->
                val inp = DataInputStream(sock.getInputStream())
                val header = ByteArray(64).also { inp.readFully(it) }
                val obf = ServerObfuscation(header)
                readFrame(inp, obf)
                val err = ByteArray(4)
                MTProtoCrypto.writeIntLE(err, 0, -404)
                sock.getOutputStream().write(obf.frameOut(err))
                sock.getOutputStream().flush()
            }
        }.apply { isDaemon = true }.start()

        val transport = MTProtoTransport("127.0.0.1", srv.localPort, dcId = 2)
        transport.connect()
        transport.send(ByteArray(16))
        try {
            transport.receive()
            fail("transport error must be raised")
        } catch (e: MTProtoTransport.TransportError) {
            assertEquals(-404, e.code)
            assertTrue(e.requiresNewAuthKey)
        }
        transport.close()
    }

    /** Заголовок не должен выдавать протокол: ни 0xEF, ни HTTP, ни TLS. */
    @Test
    fun handshakeHeaderHasNoRecognizableSignature() {
        val srv = ServerSocket(0).also { server = it }
        val headers = mutableListOf<ByteArray>()
        val latch = CountDownLatch(20)

        Thread {
            repeat(20) {
                srv.accept().use { sock ->
                    val inp = DataInputStream(sock.getInputStream())
                    val h = ByteArray(64).also { b -> inp.readFully(b) }
                    synchronized(headers) { headers.add(h) }
                    latch.countDown()
                }
            }
        }.apply { isDaemon = true }.start()

        repeat(20) {
            MTProtoTransport("127.0.0.1", srv.localPort, dcId = 2).apply {
                connect(); close()
            }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))

        val forbidden = listOf("POST", "GET ", "HEAD", "OPTI")
        for (h in headers) {
            assertTrue("first byte must not be 0xEF", h[0] != 0xEF.toByte())
            val prefix = String(h.copyOfRange(0, 4), Charsets.ISO_8859_1)
            assertTrue("must not look like HTTP: $prefix", prefix !in forbidden)
            assertTrue("must not look like TLS",
                !(h[0] == 0x16.toByte() && h[1] == 0x03.toByte()))
        }

        // Заголовки должны отличаться: одинаковые выдали бы клиента.
        val distinct = headers.map { it.joinToString("") { b -> "%02x".format(b) } }.toSet()
        assertEquals(20, distinct.size)
    }

    /** Обфусцированный поток не должен содержать открытый текст. */
    @Test
    fun payloadIsNotVisibleOnTheWire() {
        val srv = ServerSocket(0).also { server = it }
        val captured = ArrayList<Byte>()
        val latch = CountDownLatch(1)

        Thread {
            srv.accept().use { sock ->
                val inp = DataInputStream(sock.getInputStream())
                inp.readFully(ByteArray(64))
                val buf = ByteArray(256)
                val n = inp.read(buf)
                synchronized(captured) {
                    for (i in 0 until n) captured.add(buf[i])
                }
                latch.countDown()
            }
        }.apply { isDaemon = true }.start()

        val transport = MTProtoTransport("127.0.0.1", srv.localPort, dcId = 2)
        transport.connect()
        val marker = "KUOTEX_SECRET_MARKER_1234".toByteArray()
        transport.send(marker + ByteArray((4 - marker.size % 4) % 4))
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        transport.close()

        val wire = synchronized(captured) { captured.toByteArray() }
        val wireStr = String(wire, Charsets.ISO_8859_1)
        assertTrue("plaintext leaked to the wire!",
            !wireStr.contains("KUOTEX_SECRET_MARKER"))
    }

    @Test
    fun rejectsOversizedFrame() {
        val srv = ServerSocket(0).also { server = it }
        Thread {
            srv.accept().use { sock ->
                val inp = DataInputStream(sock.getInputStream())
                val header = ByteArray(64).also { inp.readFully(it) }
                val obf = ServerObfuscation(header)
                // Заявляем гигантский размер, чтобы спровоцировать OOM.
                val evil = byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
                sock.getOutputStream().write(obf.encryptor.update(evil))
                sock.getOutputStream().flush()
                Thread.sleep(500)
            }
        }.apply { isDaemon = true }.start()

        val transport = MTProtoTransport("127.0.0.1", srv.localPort, dcId = 2)
        transport.connect()
        try {
            transport.receive()
            fail("oversized frame must be rejected")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("too large"))
        }
        transport.close()
    }

    @Test
    fun detectsPeerDisconnect() {
        val srv = ServerSocket(0).also { server = it }
        Thread {
            val sock = srv.accept()
            DataInputStream(sock.getInputStream()).readFully(ByteArray(64))
            sock.close()   // резкий обрыв, как в мобильной сети
        }.apply { isDaemon = true }.start()

        val transport = MTProtoTransport("127.0.0.1", srv.localPort, dcId = 2)
        transport.connect()
        try {
            transport.receive()
            fail("must detect closed connection")
        } catch (e: IOException) {
            assertTrue(true)
        }
        transport.close()
    }
}
