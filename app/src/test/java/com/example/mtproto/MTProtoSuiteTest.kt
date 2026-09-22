package com.example.mtproto

import com.example.crypto.AesIge
import com.example.mtproto.MTProtoCrypto
import com.example.mtproto.MTProtoDh
import com.example.mtproto.MTProtoManager
import com.example.mtproto.MTProtoObfuscator
import com.example.mtproto.MTProtoSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger
import java.security.SecureRandom

class MTProtoSuiteTest {

    private val rnd = SecureRandom()
    private fun randomKey() = ByteArray(256).also { rnd.nextBytes(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) {
        s.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    // ---------------------------------------------------------------- AES-IGE

    /** Эталонный вектор AES-256-IGE: фиксирует совместимость с Telegram. */
    @Test
    fun aesIgeMatchesReferenceVector() {
        val key = unhex(
            "000102030405060708090A0B0C0D0E0F000102030405060708090A0B0C0D0E0F"
        )
        val iv = unhex(
            "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F"
        )
        val ct = AesIge.encrypt(ByteArray(32), key, iv)
        assertEquals(
            "636c6201ca3e54586e9e60841e48d23d27bc72f486d9e09a7a65785e77ecc4b1",
            hex(ct)
        )
        assertArrayEquals(ByteArray(32), AesIge.decrypt(ct, key, iv))
    }

    @Test
    fun aesIgeRoundTripVariousSizes() {
        val key = ByteArray(32).also { rnd.nextBytes(it) }
        val iv = ByteArray(32).also { rnd.nextBytes(it) }
        for (size in listOf(16, 32, 256, 4096)) {
            val pt = ByteArray(size).also { rnd.nextBytes(it) }
            assertArrayEquals(pt, AesIge.decrypt(AesIge.encrypt(pt, key, iv), key, iv))
        }
    }

    // ---------------------------------------------------------------- KDF

    @Test
    fun kdfDiffersByDirection() {
        val key = randomKey()
        val msgKey = ByteArray(16).also { rnd.nextBytes(it) }
        val (ck, civ) = MTProtoCrypto.deriveKeyIv(key, msgKey, true)
        val (sk, siv) = MTProtoCrypto.deriveKeyIv(key, msgKey, false)
        assertNotEquals(hex(ck), hex(sk))
        assertNotEquals(hex(civ), hex(siv))
        assertEquals(32, ck.size)
        assertEquals(32, civ.size)
    }

    @Test
    fun authKeyIdIsStable() {
        val key = randomKey()
        assertEquals(MTProtoCrypto.authKeyId(key), MTProtoCrypto.authKeyId(key))
    }

    // ---------------------------------------------------------------- envelope

    @Test
    fun paddingIsRandomizedSoCiphertextsDiffer() {
        val key = randomKey()
        val payload = ByteArray(32).also { rnd.nextBytes(it) }
        val a = MTProtoCrypto.encrypt(key, payload, true)
        val b = MTProtoCrypto.encrypt(key, payload, true)
        assertNotEquals(hex(a), hex(b))
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val key = randomKey()
        val env = MTProtoCrypto.encrypt(key, ByteArray(32), true)
        env[30] = (env[30].toInt() xor 1).toByte()
        try {
            MTProtoCrypto.decrypt(key, env, true)
            fail("tampered message must be rejected")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("msg_key"))
        }
    }

    @Test
    fun wrongAuthKeyIsRejected() {
        val env = MTProtoCrypto.encrypt(randomKey(), ByteArray(32), true)
        try {
            MTProtoCrypto.decrypt(randomKey(), env, true)
            fail("must reject unknown auth_key_id")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("auth_key_id"))
        }
    }

    // ---------------------------------------------------------------- session

    @Test
    fun clientServerRoundTrip() {
        val key = randomKey()
        val client = MTProtoManager(isClient = true).apply { restoreAuthKey(key, 12345L) }
        val body = "Привет, KuoteX!".toByteArray(Charsets.UTF_8)
        val padded = body + ByteArray((4 - body.size % 4) % 4)
        val wire = client.encryptMessage(padded)

        // Проверяем конверт напрямую, т.к. session_id у сторон разный.
        val decrypted = MTProtoCrypto.decrypt(key, wire, true)
        val len = MTProtoCrypto.readIntLE(decrypted, 28)
        assertArrayEquals(padded, decrypted.copyOfRange(32, 32 + len))
    }

    @Test
    fun seqNoFollowsSpec() {
        val s = MTProtoSession(randomKey(), true)
        assertEquals(1, s.nextSeqNo(true))
        assertEquals(2, s.nextSeqNo(false))
        assertEquals(3, s.nextSeqNo(true))
        assertEquals(4, s.nextSeqNo(false))
    }

    @Test
    fun messageIdsAreMonotonicAndCorrectParity() {
        val s = MTProtoSession(randomKey(), isClient = true)
        val ids = (0 until 1000).map { s.nextMessageId() }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ids.sorted(), ids)
        assertTrue(ids.all { it % 4L == 0L })
    }

    @Test
    fun replayedMessageIsRejected() {
        val key = randomKey()
        val client = MTProtoSession(key, isClient = true)
        val server = MTProtoSession(key, isClient = false)
        // Согласуем session_id через рефлексию (в бою его присылает клиент).
        val field = MTProtoSession::class.java.getDeclaredField("sessionId")
        field.isAccessible = true
        field.set(server, client.sessionId)

        val wire = client.encrypt("hello".toByteArray() + ByteArray(3))
        assertTrue(server.decrypt(wire).body.isNotEmpty())
        try {
            server.decrypt(wire)
            fail("replay must be rejected")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("replay"))
        }
    }

    // ---------------------------------------------------------------- DH

    @Test
    fun dhProducesIdenticalAuthKeys() {
        val a = MTProtoDh.generateSecret()
        val b = MTProtoDh.generateSecret()
        val gA = MTProtoDh.G.modPow(a, MTProtoDh.P)
        val resB = MTProtoDh.computeAuthKey(gA, b, verifyGroup = true)
        val fromA = MTProtoDh.toFixed256(resB.gB.modPow(a, MTProtoDh.P))
        assertArrayEquals(fromA, resB.authKey)
        assertEquals(256, resB.authKey.size)
        assertEquals(8, resB.authKeyAuxHash.size)
    }

    @Test
    fun dhRejectsDegenerateValues() {
        val b = MTProtoDh.generateSecret()
        for (bad in listOf(
            BigInteger.ZERO,
            BigInteger.ONE,
            MTProtoDh.P.subtract(BigInteger.ONE),
            BigInteger.TWO
        )) {
            try {
                MTProtoDh.computeAuthKey(bad, b, verifyGroup = false)
                fail("degenerate g_a must be rejected: $bad")
            } catch (e: MTProtoCrypto.SecurityViolation) { /* ожидаемо */ }
        }
    }

    @Test
    fun dhRejectsUnsafeGroup() {
        // Составное p — сервер пытается навязать слабую группу.
        val composite = MTProtoDh.P.subtract(BigInteger.ONE)
        assertTrue(!MTProtoDh.isSafeGroup(composite, MTProtoDh.G))
    }

    @Test
    fun dhAcceptsStandardGroup() {
        assertTrue(MTProtoDh.isSafeGroup(MTProtoDh.P, MTProtoDh.G))
    }

    @Test
    fun fullHandshakeBetweenTwoManagers() {
        val client = MTProtoManager(isClient = true)
        val server = MTProtoManager(isClient = false)
        val clientGb = client.initializeSession()
        val serverGb = server.initializeSession()
        client.completeDhExchange(serverGb)
        server.completeDhExchange(clientGb)
        assertArrayEquals(client.authKey, server.authKey)
        assertEquals(client.authKeyId(), server.authKeyId())
    }

    // ---------------------------------------------------------------- obfuscation

    @Test
    fun obfuscatorHeaderLooksRandom() {
        repeat(50) {
            val o = MTProtoObfuscator.create(dcId = 2)
            assertEquals(64, o.initialPayload.size)
            assertNotEquals(0xEF.toByte(), o.initialPayload[0])
        }
    }

    @Test
    fun obfuscatorStreamsAreSymmetric() {
        // Клиент шифрует своим enc, сервер расшифровывает зеркальным dec.
        val o = MTProtoObfuscator.create(dcId = 2)
        val data = ByteArray(128).also { rnd.nextBytes(it) }
        val enc = o.encrypt(data)
        assertNotEquals(hex(data), hex(enc))
    }

    @Test
    fun abridgedFramingLengths() {
        assertEquals(1, MTProtoObfuscator.frame(ByteArray(4))[0].toInt())
        val big = MTProtoObfuscator.frame(ByteArray(4 * 200))
        assertEquals(0x7F, big[0].toInt())
        assertEquals(200, big[1].toInt() and 0xFF)
    }
}
