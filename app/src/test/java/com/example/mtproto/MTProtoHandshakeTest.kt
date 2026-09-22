package com.example.mtproto

import com.example.crypto.AesIge
import com.example.tlschema.TLStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Тесты handshake против фиктивного сервера, который ведёт себя
 * по спецификации MTProto. Сети нет — только байты, поэтому тесты
 * детерминированные и быстрые.
 */
class MTProtoHandshakeTest {

    private val rnd = SecureRandom()

    // ------------------------------------------------------------ fake server

    /** Сервер, реализующий обмен по спецификации. */
    private class FakeServer(
        val rsaPublic: RSAPublicKey,
        val rsaPrivate: RSAPrivateKey,
        val p: Long = 1470695641L,      // простое
        val q: Long = 1470695651L,      // простое
        val unsafeGroup: Boolean = false,
        val corruptNonce: Boolean = false,
        val corruptNewNonceHash: Boolean = false
    ) {
        val rnd = SecureRandom()
        lateinit var clientNonce: ByteArray
        val serverNonce = ByteArray(16).also { rnd.nextBytes(it) }
        lateinit var newNonce: ByteArray
        val a: BigInteger = MTProtoDh.generateSecret()
        var authKey: ByteArray? = null

        val pq: Long get() = p * q

        val rsaKey = MTProtoPq.RsaKey(rsaPublic.modulus, rsaPublic.publicExponent)

        fun handleReqPq(body: ByteArray): ByteArray {
            val input = java.io.ByteArrayInputStream(body)
            val ctor = TLStream.readInt(input)
            assertEquals(MTProtoHandshakeTL.REQ_PQ_MULTI, ctor)
            clientNonce = ByteArray(16).also { input.read(it) }

            val out = ByteArrayOutputStream()
            TLStream.writeInt(out, MTProtoHandshakeTL.RES_PQ)
            out.write(if (corruptNonce) ByteArray(16) else clientNonce)
            out.write(serverNonce)
            TLStream.writeByteArray(out, MTProtoHandshakeTL.toBigEndianBytes(pq))
            TLStream.writeInt(out, 0x1cb5c415)   // Vector
            TLStream.writeInt(out, 1)
            TLStream.writeLong(out, rsaKey.fingerprint)
            return out.toByteArray()
        }

        fun handleReqDhParams(body: ByteArray): ByteArray {
            val input = java.io.ByteArrayInputStream(body)
            assertEquals(MTProtoHandshakeTL.REQ_DH_PARAMS, TLStream.readInt(input))
            input.read(ByteArray(16))            // nonce
            input.read(ByteArray(16))            // server_nonce
            TLStream.readByteArray(input)        // p
            TLStream.readByteArray(input)        // q
            TLStream.readLong(input)             // fingerprint
            val encrypted = TLStream.readByteArray(input)

            // Расшифровываем RSA приватным ключом.
            val m = BigInteger(1, encrypted).modPow(rsaPrivate.privateExponent, rsaPrivate.modulus)
            // Полезная нагрузка RSA — 255 байт; toFixed256 добавляет
            // ведущий нулевой байт, его нужно снять.
            val plain = MTProtoDh.toFixed256(m).copyOfRange(1, 256)
            // plain = SHA1(inner) + inner + padding
            val inner = plain.copyOfRange(20, plain.size)
            val innerInput = java.io.ByteArrayInputStream(inner)
            assertEquals(MTProtoHandshakeTL.P_Q_INNER_DATA_DC, TLStream.readInt(innerInput))
            TLStream.readByteArray(innerInput)   // pq
            TLStream.readByteArray(innerInput)   // p
            TLStream.readByteArray(innerInput)   // q
            innerInput.read(ByteArray(16))       // nonce
            innerInput.read(ByteArray(16))       // server_nonce
            newNonce = ByteArray(32).also { innerInput.read(it) }

            // Собираем server_DH_inner_data.
            val prime = if (unsafeGroup) MTProtoDh.P.subtract(BigInteger.ONE) else MTProtoDh.P
            val gA = MTProtoDh.G.modPow(a, MTProtoDh.P)

            val innerOut = ByteArrayOutputStream()
            TLStream.writeInt(innerOut, MTProtoHandshakeTL.SERVER_DH_INNER_DATA)
            innerOut.write(clientNonce)
            innerOut.write(serverNonce)
            TLStream.writeInt(innerOut, 3)
            TLStream.writeByteArray(innerOut, stripSign(prime.toByteArray()))
            TLStream.writeByteArray(innerOut, stripSign(gA.toByteArray()))
            TLStream.writeInt(innerOut, (System.currentTimeMillis() / 1000L).toInt())
            val innerBytes = innerOut.toByteArray()

            val sha = MessageDigest.getInstance("SHA-1").digest(innerBytes)
            val toEnc = sha + innerBytes
            val padded = toEnc + ByteArray((16 - toEnc.size % 16) % 16)

            val (key, iv) = tempKeyIv()
            val encAnswer = AesIge.encrypt(padded, key, iv)

            val out = ByteArrayOutputStream()
            TLStream.writeInt(out, MTProtoHandshakeTL.SERVER_DH_PARAMS_OK)
            out.write(clientNonce)
            out.write(serverNonce)
            TLStream.writeByteArray(out, encAnswer)
            return out.toByteArray()
        }

        fun handleSetClientDhParams(body: ByteArray): ByteArray {
            val input = java.io.ByteArrayInputStream(body)
            assertEquals(MTProtoHandshakeTL.SET_CLIENT_DH_PARAMS, TLStream.readInt(input))
            input.read(ByteArray(16))
            input.read(ByteArray(16))
            val encrypted = TLStream.readByteArray(input)

            val (key, iv) = tempKeyIv()
            val decrypted = AesIge.decrypt(encrypted, key, iv)
            val payload = decrypted.copyOfRange(20, decrypted.size)
            val innerInput = java.io.ByteArrayInputStream(payload)
            assertEquals(MTProtoHandshakeTL.CLIENT_DH_INNER_DATA, TLStream.readInt(innerInput))
            innerInput.read(ByteArray(16))
            innerInput.read(ByteArray(16))
            TLStream.readLong(innerInput)
            val gBBytes = TLStream.readByteArray(innerInput)

            val gB = BigInteger(1, gBBytes)
            authKey = MTProtoDh.toFixed256(gB.modPow(a, MTProtoDh.P))

            val hash = MTProtoDh.newNonceHash(newNonce, 1, authKey!!)
            val out = ByteArrayOutputStream()
            TLStream.writeInt(out, MTProtoHandshakeTL.DH_GEN_OK)
            out.write(clientNonce)
            out.write(serverNonce)
            out.write(if (corruptNewNonceHash) ByteArray(16) else hash)
            return out.toByteArray()
        }

        fun tempKeyIv(): Pair<ByteArray, ByteArray> {
            fun sha1(vararg parts: ByteArray): ByteArray {
                val d = MessageDigest.getInstance("SHA-1")
                for (p in parts) d.update(p)
                return d.digest()
            }
            val nsn = sha1(newNonce, serverNonce)
            val snn = sha1(serverNonce, newNonce)
            val nnn = sha1(newNonce, newNonce)
            return Pair(nsn + snn.copyOfRange(0, 12),
                        snn.copyOfRange(12, 20) + nnn + newNonce.copyOfRange(0, 4))
        }

        fun serverSalt(): Long {
            val salt = ByteArray(8)
            for (i in 0 until 8) salt[i] = (newNonce[i].toInt() xor serverNonce[i].toInt()).toByte()
            return MTProtoCrypto.readLongLE(salt, 0)
        }

        private fun stripSign(b: ByteArray) =
            if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }

    private fun newServer(
        unsafeGroup: Boolean = false,
        corruptNonce: Boolean = false,
        corruptNewNonceHash: Boolean = false
    ): FakeServer {
        val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val pair = gen.generateKeyPair()
        return FakeServer(
            pair.public as RSAPublicKey,
            pair.private as RSAPrivateKey,
            unsafeGroup = unsafeGroup,
            corruptNonce = corruptNonce,
            corruptNewNonceHash = corruptNewNonceHash
        )
    }

    // ------------------------------------------------------------ tests

    @Test
    fun fullHandshakeSucceedsAndKeysMatch() {
        val server = newServer()
        val client = MTProtoHandshake(dcId = 2, rsaKeys = listOf(server.rsaKey))

        val reqPq = client.start()
        val resPq = server.handleReqPq(reqPq)

        val reqDh = client.onResPq(resPq)
        val serverDhParams = server.handleReqDhParams(reqDh)

        val setClientDh = client.onServerDhParams(serverDhParams)
        val dhGen = server.handleSetClientDhParams(setClientDh)

        assertTrue(client.onDhGen(dhGen))
        assertEquals(MTProtoHandshake.State.DONE, client.state)

        assertNotNull(client.authKey)
        assertEquals(256, client.authKey!!.size)
        // Обе стороны получили один и тот же ключ — суть DH.
        assertArrayEquals(server.authKey, client.authKey)
        assertEquals(server.serverSalt(), client.serverSalt)
    }

    @Test
    fun derivedAuthKeyWorksForRealMessages() {
        val server = newServer()
        val client = MTProtoHandshake(2, listOf(server.rsaKey))
        client.onDhGen(
            server.handleSetClientDhParams(
                client.onServerDhParams(
                    server.handleReqDhParams(client.onResPq(server.handleReqPq(client.start())))
                )
            )
        )
        val key = client.authKey!!

        // Ключ пригоден для обычного обмена сообщениями.
        val manager = MTProtoManager(isClient = true).apply {
            restoreAuthKey(key, client.serverSalt)
        }
        val body = "оплата 100 звёзд".toByteArray(Charsets.UTF_8)
        val aligned = body + ByteArray((4 - body.size % 4) % 4)
        val envelope = manager.encryptMessage(aligned)

        val decrypted = MTProtoCrypto.decrypt(key, envelope, true)
        val len = MTProtoCrypto.readIntLE(decrypted, 28)
        assertArrayEquals(aligned, decrypted.copyOfRange(32, 32 + len))
    }

    @Test
    fun rejectsUnsafeDhGroup() {
        val server = newServer(unsafeGroup = true)
        val client = MTProtoHandshake(2, listOf(server.rsaKey))
        val resPq = server.handleReqPq(client.start())
        val serverDh = server.handleReqDhParams(client.onResPq(resPq))
        try {
            client.onServerDhParams(serverDh)
            fail("unsafe group must be rejected")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("unsafe DH group"))
            assertEquals(MTProtoHandshake.State.FAILED, client.state)
        }
    }

    @Test
    fun rejectsMismatchedNonce() {
        val server = newServer(corruptNonce = true)
        val client = MTProtoHandshake(2, listOf(server.rsaKey))
        val resPq = server.handleReqPq(client.start())
        try {
            client.onResPq(resPq)
            fail("nonce mismatch must be rejected")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("nonce mismatch"))
        }
    }

    /** Главная защита от MITM: сервер обязан доказать, что знает auth_key. */
    @Test
    fun rejectsBadNewNonceHash() {
        val server = newServer(corruptNewNonceHash = true)
        val client = MTProtoHandshake(2, listOf(server.rsaKey))
        val dhGen = server.handleSetClientDhParams(
            client.onServerDhParams(
                server.handleReqDhParams(client.onResPq(server.handleReqPq(client.start())))
            )
        )
        try {
            client.onDhGen(dhGen)
            fail("bad new_nonce_hash must be rejected")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("MITM"))
        }
    }

    @Test
    fun rejectsUnknownRsaFingerprint() {
        val server = newServer()
        val other = newServer()
        // Клиент знает только чужой ключ.
        val client = MTProtoHandshake(2, listOf(other.rsaKey))
        val resPq = server.handleReqPq(client.start())
        try {
            client.onResPq(resPq)
            fail("unknown fingerprint must be rejected")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("no known RSA key"))
        }
    }

    // ------------------------------------------------------------ pq

    @Test
    fun factorizesPqCorrectly() {
        val cases = listOf(
            1470695641L to 1470695651L,
            2000000011L to 2000000033L,
            17L to 19L
        )
        for ((p, q) in cases) {
            val (fp, fq) = MTProtoPq.factorize(p * q)
            assertEquals(minOf(p, q), fp)
            assertEquals(maxOf(p, q), fq)
        }
    }

    @Test
    fun factorizationIsFastEnoughForWeakCpu() {
        val pq = 1470695641L * 1470695651L
        val start = System.nanoTime()
        repeat(20) { MTProtoPq.factorize(pq) }
        val msPerOp = (System.nanoTime() - start) / 1e6 / 20
        assertTrue("factorization too slow: $msPerOp ms", msPerOp < 500.0)
    }

    @Test
    fun rsaFingerprintIsStable() {
        val server = newServer()
        assertEquals(server.rsaKey.fingerprint, server.rsaKey.fingerprint)
    }

    // ------------------------------------------------------------ envelope

    @Test
    fun unencryptedEnvelopeRoundTrip() {
        val body = "test".toByteArray()
        val wrapped = MTProtoHandshakeTL.wrapUnencrypted(body, 0x51e57ac42770964aL)
        assertEquals(0L, MTProtoCrypto.readLongLE(wrapped, 0))
        assertArrayEquals(body, MTProtoHandshakeTL.unwrapUnencrypted(wrapped))
    }

    @Test
    fun rejectsEncryptedMessageDuringHandshake() {
        val fake = ByteArray(40)
        MTProtoCrypto.writeLongLE(fake, 0, 12345L)   // auth_key_id != 0
        try {
            MTProtoHandshakeTL.unwrapUnencrypted(fake)
            fail("must reject non-zero auth_key_id")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("unencrypted"))
        }
    }
}
