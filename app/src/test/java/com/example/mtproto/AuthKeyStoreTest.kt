package com.example.mtproto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicInteger

/**
 * Тесты хранилища ключей и провайдера.
 *
 * Работают на чистой JVM: Android-специфика вынесена в
 * EncryptedAuthKeyStore, а логика жизненного цикла ключа проверяется
 * на InMemoryAuthKeyStore.
 */
class AuthKeyStoreTest {

    private val rnd = SecureRandom()
    private fun randomKey() = ByteArray(256).also { rnd.nextBytes(it) }

    private fun entry(
        dcId: Int = 2,
        key: ByteArray = randomKey(),
        salt: Long = 12345L,
        createdAt: Long = System.currentTimeMillis()
    ) = AuthKeyStore.Entry(dcId, key, salt, createdAt)

    // ------------------------------------------------------------ store

    @Test
    fun savesAndLoadsEntry() {
        val store = InMemoryAuthKeyStore()
        val key = randomKey()
        store.save(entry(dcId = 2, key = key, salt = 999L))

        val loaded = store.load(2)
        assertNotNull(loaded)
        assertArrayEquals(key, loaded!!.authKey)
        assertEquals(999L, loaded.serverSalt)
        assertEquals(2, loaded.dcId)
    }

    @Test
    fun returnsNullForUnknownDc() {
        assertNull(InMemoryAuthKeyStore().load(4))
    }

    @Test
    fun keepsDataCentersIndependent() {
        val store = InMemoryAuthKeyStore()
        val k2 = randomKey()
        val k4 = randomKey()
        store.save(entry(dcId = 2, key = k2))
        store.save(entry(dcId = 4, key = k4))

        assertArrayEquals(k2, store.load(2)!!.authKey)
        assertArrayEquals(k4, store.load(4)!!.authKey)
        assertEquals(setOf(2, 4), store.storedDcIds())

        store.remove(2)
        assertNull(store.load(2))
        assertNotNull(store.load(4))
    }

    /**
     * Хранилище обязано копировать массив. Иначе вызывающий код,
     * затерев свой экземпляр ключа, незаметно испортит сохранённый.
     */
    @Test
    fun storeKeepsItsOwnCopyOfKey() {
        val store = InMemoryAuthKeyStore()
        val key = randomKey()
        val original = key.copyOf()
        store.save(entry(dcId = 2, key = key))

        java.util.Arrays.fill(key, 0)          // затираем исходный массив

        assertArrayEquals(original, store.load(2)!!.authKey)
    }

    @Test
    fun loadedCopyIsIsolatedFromStore() {
        val store = InMemoryAuthKeyStore()
        val original = randomKey()
        store.save(entry(dcId = 2, key = original.copyOf()))

        val first = store.load(2)!!
        java.util.Arrays.fill(first.authKey, 0)   // портим полученную копию

        assertArrayEquals(original, store.load(2)!!.authKey)
    }

    @Test
    fun updatesSaltWithoutTouchingKey() {
        val store = InMemoryAuthKeyStore()
        val key = randomKey()
        store.save(entry(dcId = 2, key = key, salt = 1L))

        store.updateSalt(2, 777L)

        val loaded = store.load(2)!!
        assertEquals(777L, loaded.serverSalt)
        assertArrayEquals(key, loaded.authKey)
    }

    @Test
    fun clearRemovesEverything() {
        val store = InMemoryAuthKeyStore()
        store.save(entry(dcId = 1))
        store.save(entry(dcId = 2))
        store.clear()
        assertTrue(store.storedDcIds().isEmpty())
        assertNull(store.load(1))
    }

    @Test
    fun rejectsWrongKeySize() {
        try {
            AuthKeyStore.Entry(2, ByteArray(128), 0L, 0L)
            fail("must reject key shorter than 256 bytes")
        } catch (e: MTProtoCrypto.SecurityViolation) {
            assertTrue(e.message!!.contains("256"))
        }
    }

    @Test
    fun exposesAuthKeyId() {
        val key = randomKey()
        assertEquals(MTProtoCrypto.authKeyId(key), entry(key = key).authKeyId)
    }

    // ------------------------------------------------------------ provider

    private class FakeServer {
        val rnd = SecureRandom()
        val pair = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }.generateKeyPair()
        val pub = pair.public as RSAPublicKey
        val priv = pair.private as RSAPrivateKey
        val rsaKey = MTProtoPq.RsaKey(pub.modulus, pub.publicExponent)

        val p = 1470695641L
        val q = 1470695651L
        val a: BigInteger = MTProtoDh.generateSecret()

        lateinit var clientNonce: ByteArray
        val serverNonce = ByteArray(16).also { rnd.nextBytes(it) }
        lateinit var newNonce: ByteArray
        var authKey: ByteArray? = null
        var handshakeCount = 0

        fun handle(request: ByteArray): ByteArray {
            val ctor = MTProtoCrypto.readIntLE(request, 0)
            return when (ctor) {
                MTProtoHandshakeTL.REQ_PQ_MULTI -> resPq(request)
                MTProtoHandshakeTL.REQ_DH_PARAMS -> serverDhParams(request)
                MTProtoHandshakeTL.SET_CLIENT_DH_PARAMS -> dhGen(request)
                else -> throw IllegalStateException("unexpected ctor")
            }
        }

        private fun resPq(body: ByteArray): ByteArray {
            handshakeCount++
            val input = java.io.ByteArrayInputStream(body)
            com.example.tlschema.TLStream.readInt(input)
            clientNonce = ByteArray(16).also { input.read(it) }

            val out = java.io.ByteArrayOutputStream()
            com.example.tlschema.TLStream.writeInt(out, MTProtoHandshakeTL.RES_PQ)
            out.write(clientNonce)
            out.write(serverNonce)
            com.example.tlschema.TLStream.writeByteArray(
                out, MTProtoHandshakeTL.toBigEndianBytes(p * q)
            )
            com.example.tlschema.TLStream.writeInt(out, 0x1cb5c415)
            com.example.tlschema.TLStream.writeInt(out, 1)
            com.example.tlschema.TLStream.writeLong(out, rsaKey.fingerprint)
            return out.toByteArray()
        }

        private fun serverDhParams(body: ByteArray): ByteArray {
            val input = java.io.ByteArrayInputStream(body)
            com.example.tlschema.TLStream.readInt(input)
            input.read(ByteArray(16)); input.read(ByteArray(16))
            com.example.tlschema.TLStream.readByteArray(input)
            com.example.tlschema.TLStream.readByteArray(input)
            com.example.tlschema.TLStream.readLong(input)
            val encrypted = com.example.tlschema.TLStream.readByteArray(input)

            val m = BigInteger(1, encrypted).modPow(priv.privateExponent, priv.modulus)
            val plain = MTProtoDh.toFixed256(m).copyOfRange(1, 256)
            val inner = plain.copyOfRange(20, plain.size)
            val ii = java.io.ByteArrayInputStream(inner)
            com.example.tlschema.TLStream.readInt(ii)
            com.example.tlschema.TLStream.readByteArray(ii)
            com.example.tlschema.TLStream.readByteArray(ii)
            com.example.tlschema.TLStream.readByteArray(ii)
            ii.read(ByteArray(16)); ii.read(ByteArray(16))
            newNonce = ByteArray(32).also { ii.read(it) }

            val gA = MTProtoDh.G.modPow(a, MTProtoDh.P)
            val innerOut = java.io.ByteArrayOutputStream()
            com.example.tlschema.TLStream.writeInt(
                innerOut, MTProtoHandshakeTL.SERVER_DH_INNER_DATA
            )
            innerOut.write(clientNonce)
            innerOut.write(serverNonce)
            com.example.tlschema.TLStream.writeInt(innerOut, 3)
            com.example.tlschema.TLStream.writeByteArray(innerOut, strip(MTProtoDh.P.toByteArray()))
            com.example.tlschema.TLStream.writeByteArray(innerOut, strip(gA.toByteArray()))
            com.example.tlschema.TLStream.writeInt(
                innerOut, (System.currentTimeMillis() / 1000L).toInt()
            )
            val innerBytes = innerOut.toByteArray()
            val sha = MessageDigest.getInstance("SHA-1").digest(innerBytes)
            val toEnc = sha + innerBytes
            val padded = toEnc + ByteArray((16 - toEnc.size % 16) % 16)
            val (key, iv) = tempKeyIv()
            val enc = com.example.crypto.AesIge.encrypt(padded, key, iv)

            val out = java.io.ByteArrayOutputStream()
            com.example.tlschema.TLStream.writeInt(
                out, MTProtoHandshakeTL.SERVER_DH_PARAMS_OK
            )
            out.write(clientNonce)
            out.write(serverNonce)
            com.example.tlschema.TLStream.writeByteArray(out, enc)
            return out.toByteArray()
        }

        private fun dhGen(body: ByteArray): ByteArray {
            val input = java.io.ByteArrayInputStream(body)
            com.example.tlschema.TLStream.readInt(input)
            input.read(ByteArray(16)); input.read(ByteArray(16))
            val encrypted = com.example.tlschema.TLStream.readByteArray(input)
            val (key, iv) = tempKeyIv()
            val dec = com.example.crypto.AesIge.decrypt(encrypted, key, iv)
            val payload = dec.copyOfRange(20, dec.size)
            val ii = java.io.ByteArrayInputStream(payload)
            com.example.tlschema.TLStream.readInt(ii)
            ii.read(ByteArray(16)); ii.read(ByteArray(16))
            com.example.tlschema.TLStream.readLong(ii)
            val gB = BigInteger(1, com.example.tlschema.TLStream.readByteArray(ii))
            authKey = MTProtoDh.toFixed256(gB.modPow(a, MTProtoDh.P))

            val out = java.io.ByteArrayOutputStream()
            com.example.tlschema.TLStream.writeInt(out, MTProtoHandshakeTL.DH_GEN_OK)
            out.write(clientNonce)
            out.write(serverNonce)
            out.write(MTProtoDh.newNonceHash(newNonce, 1, authKey!!))
            return out.toByteArray()
        }

        fun expectedSalt(): Long {
            val salt = ByteArray(8)
            for (i in 0 until 8) {
                salt[i] = (newNonce[i].toInt() xor serverNonce[i].toInt()).toByte()
            }
            return MTProtoCrypto.readLongLE(salt, 0)
        }

        private fun tempKeyIv(): Pair<ByteArray, ByteArray> {
            fun sha1(vararg parts: ByteArray): ByteArray {
                val d = MessageDigest.getInstance("SHA-1")
                parts.forEach { d.update(it) }
                return d.digest()
            }
            val nsn = sha1(newNonce, serverNonce)
            val snn = sha1(serverNonce, newNonce)
            val nnn = sha1(newNonce, newNonce)
            return Pair(
                nsn + snn.copyOfRange(0, 12),
                snn.copyOfRange(12, 20) + nnn + newNonce.copyOfRange(0, 4)
            )
        }

        private fun strip(b: ByteArray) =
            if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }

    @Test
    fun performsHandshakeWhenNoKeyStored() = runBlocking {
        val server = FakeServer()
        val store = InMemoryAuthKeyStore()
        val provider = AuthKeyProvider(store, listOf(server.rsaKey))

        val session = provider.obtainSession(2) { server.handle(it) }

        assertArrayEquals(server.authKey, session.authKey)
        assertEquals(server.expectedSalt(), session.serverSalt)
        // Ключ должен осесть в хранилище, иначе следующий запуск
        // снова полезет в handshake.
        assertNotNull(store.load(2))
        assertEquals(1, server.handshakeCount)
    }

    @Test
    fun reusesStoredKeyWithoutHandshake() = runBlocking {
        val server = FakeServer()
        val store = InMemoryAuthKeyStore()
        val provider = AuthKeyProvider(store, listOf(server.rsaKey))

        val first = provider.obtainSession(2) { server.handle(it) }
        val second = provider.obtainSession(2) { server.handle(it) }

        assertArrayEquals(first.authKey, second.authKey)
        assertEquals("handshake must run only once", 1, server.handshakeCount)
    }

    @Test
    fun runsNewHandshakeWhenKeyExpired() = runBlocking {
        val server = FakeServer()
        val store = InMemoryAuthKeyStore()
        var now = System.currentTimeMillis()
        val provider = AuthKeyProvider(
            store, listOf(server.rsaKey),
            maxAgeMs = 1000L,
            clock = { now }
        )

        provider.obtainSession(2) { server.handle(it) }
        assertEquals(1, server.handshakeCount)

        now += 5000L                       // ключ протух
        provider.obtainSession(2) { server.handle(it) }
        assertEquals(2, server.handshakeCount)
    }

    /** Часы перевели назад — доверять записи нельзя. */
    @Test
    fun treatsNegativeAgeAsExpired() = runBlocking {
        val server = FakeServer()
        val store = InMemoryAuthKeyStore()
        var now = System.currentTimeMillis()
        val provider = AuthKeyProvider(store, listOf(server.rsaKey), clock = { now })

        provider.obtainSession(2) { server.handle(it) }
        now -= 60_000L                     // часы ушли назад
        provider.obtainSession(2) { server.handle(it) }

        assertEquals(2, server.handshakeCount)
    }

    @Test
    fun forceNewIgnoresStoredKey() = runBlocking {
        val server = FakeServer()
        val provider = AuthKeyProvider(InMemoryAuthKeyStore(), listOf(server.rsaKey))

        provider.obtainSession(2) { server.handle(it) }
        provider.obtainSession(2, forceNew = true) { server.handle(it) }

        assertEquals(2, server.handshakeCount)
    }

    @Test
    fun invalidateForcesHandshakeNextTime() = runBlocking {
        val server = FakeServer()
        val store = InMemoryAuthKeyStore()
        val provider = AuthKeyProvider(store, listOf(server.rsaKey))

        provider.obtainSession(2) { server.handle(it) }
        assertTrue(provider.hasValidKey(2))

        provider.invalidate(2)             // сервер ответил -404
        assertFalse(provider.hasValidKey(2))

        provider.obtainSession(2) { server.handle(it) }
        assertEquals(2, server.handshakeCount)
    }

    @Test
    fun logoutWipesAllKeys() = runBlocking {
        val server = FakeServer()
        val store = InMemoryAuthKeyStore()
        val provider = AuthKeyProvider(store, listOf(server.rsaKey))

        provider.obtainSession(2) { server.handle(it) }
        provider.logout()

        assertTrue(store.storedDcIds().isEmpty())
        assertFalse(provider.hasValidKey(2))
    }

    @Test
    fun saltUpdateSurvivesReload() = runBlocking {
        val server = FakeServer()
        val store = InMemoryAuthKeyStore()
        val provider = AuthKeyProvider(store, listOf(server.rsaKey))

        provider.obtainSession(2) { server.handle(it) }
        provider.updateSalt(2, 424242L)

        val session = provider.obtainSession(2) { server.handle(it) }
        assertEquals(424242L, session.serverSalt)
        assertEquals(1, server.handshakeCount)
    }

    /** Ключ из хранилища должен реально работать для шифрования. */
    @Test
    fun restoredSessionEncryptsAndDecrypts() = runBlocking {
        val server = FakeServer()
        val store = InMemoryAuthKeyStore()
        val provider = AuthKeyProvider(store, listOf(server.rsaKey))

        provider.obtainSession(2) { server.handle(it) }
        val restored = provider.obtainSession(2) { server.handle(it) }

        val body = "сообщение после перезапуска".toByteArray(Charsets.UTF_8)
        val aligned = body + ByteArray((4 - body.size % 4) % 4)
        val envelope = restored.encrypt(aligned)

        val decrypted = MTProtoCrypto.decrypt(restored.authKey, envelope, true)
        val len = MTProtoCrypto.readIntLE(decrypted, 28)
        assertArrayEquals(aligned, decrypted.copyOfRange(32, 32 + len))
    }

    /**
     * Параллельные запросы не должны запускать два handshake:
     * второй перезаписал бы ключ первого и оборвал соединение.
     */
    @Test
    fun concurrentRequestsRunSingleHandshake() = runBlocking {
        val server = FakeServer()
        val provider = AuthKeyProvider(InMemoryAuthKeyStore(), listOf(server.rsaKey))
        val calls = AtomicInteger()

        val results = coroutineScope {
            (1..8).map {
                async(Dispatchers.Default) {
                    provider.obtainSession(2) { req ->
                        calls.incrementAndGet()
                        synchronized(server) { server.handle(req) }
                    }
                }
            }.awaitAll()
        }

        assertEquals(1, server.handshakeCount)
        val first = results.first().authKey
        assertTrue(results.all { it.authKey.contentEquals(first) })
    }
}
