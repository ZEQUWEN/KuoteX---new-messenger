package com.example.mtproto

import com.example.crypto.AesIge
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays

/**
 * Полный процесс создания auth_key (MTProto 2.0).
 *
 * Класс — конечный автомат без сети: он принимает байты ответа сервера
 * и отдаёт байты следующего запроса. Благодаря этому весь handshake
 * тестируется детерминированно, без сокета и без дата-центра.
 *
 * Схема обмена:
 *   1. клиент -> req_pq_multi(nonce)
 *   2. сервер -> resPQ(pq, server_nonce, fingerprints)
 *   3. клиент -> req_DH_params(RSA(p_q_inner_data))   [факторизация pq]
 *   4. сервер -> server_DH_params_ok(AES-IGE(server_DH_inner_data))
 *   5. клиент -> set_client_DH_params(AES-IGE(client_DH_inner_data))
 *   6. сервер -> dh_gen_ok(new_nonce_hash1)
 *
 * Референс: https://core.telegram.org/mtproto/auth_key
 */
class MTProtoHandshake(
    private val dcId: Int,
    private val rsaKeys: List<MTProtoPq.RsaKey>,
    private val random: SecureRandom = SecureRandom()
) {

    enum class State { NEW, WAIT_RES_PQ, WAIT_DH_PARAMS, WAIT_DH_GEN, DONE, FAILED }

    var state: State = State.NEW
        private set

    /** Итог: заполняется после успешного завершения. */
    var authKey: ByteArray? = null
        private set
    var serverSalt: Long = 0L
        private set
    var timeOffsetSeconds: Int = 0
        private set

    private lateinit var nonce: ByteArray          // 16, наш
    private lateinit var serverNonce: ByteArray    // 16, сервера
    private lateinit var newNonce: ByteArray       // 32, наш, главный секрет этапа

    private var retryId = 0L
    private var secretB: BigInteger? = null
    private var dhPrime: BigInteger? = null
    private var dhG: BigInteger? = null

    /** g_a сохраняется на шаге 4 и переиспользуется на шаге 6. */
    private var gAValue: BigInteger? = null

    // ---------------------------------------------------------------- step 1

    /** Шаг 1: начало. Возвращает тело req_pq_multi. */
    fun start(): ByteArray {
        check(state == State.NEW) { "handshake already started" }
        nonce = ByteArray(16).also { random.nextBytes(it) }
        state = State.WAIT_RES_PQ
        return MTProtoHandshakeTL.reqPqMulti(nonce)
    }

    // ---------------------------------------------------------------- step 2-3

    /**
     * Шаг 2-3: принимает resPQ, факторизует pq, возвращает req_DH_params.
     */
    fun onResPq(body: ByteArray): ByteArray {
        check(state == State.WAIT_RES_PQ) { "unexpected resPQ in state $state" }
        val res = MTProtoHandshakeTL.parseResPq(body)

        // Сервер обязан вернуть наш nonce неизменным — иначе это чужой ответ.
        if (!MTProtoCrypto.constantTimeEquals(res.nonce, nonce))
            failWith("nonce mismatch in resPQ")

        serverNonce = res.serverNonce

        // Выбираем RSA-ключ, отпечаток которого знает сервер.
        val key = rsaKeys.firstOrNull { res.fingerprints.contains(it.fingerprint) }
            ?: failWith(
                "no known RSA key; server offered " +
                    res.fingerprints.joinToString { java.lang.Long.toHexString(it) }
            )
        val fingerprint = key.fingerprint

        // Доказательство работы: раскладываем pq.
        val (p, q) = MTProtoPq.factorize(res.pq)

        newNonce = ByteArray(32).also { random.nextBytes(it) }

        val inner = MTProtoHandshakeTL.pqInnerData(
            res.pq, p, q, nonce, serverNonce, newNonce, dcId
        )

        // RSA-обёртка: SHA1(inner) + inner + случайный паддинг до 255 байт.
        val sha = MessageDigest.getInstance("SHA-1").digest(inner)
        val dataWithHash = sha + inner
        if (dataWithHash.size > 255)
            failWith("p_q_inner_data too large")
        val padded = dataWithHash + ByteArray(255 - dataWithHash.size).also {
            random.nextBytes(it)
        }
        val encrypted = key.encryptRaw(padded)

        state = State.WAIT_DH_PARAMS
        return MTProtoHandshakeTL.reqDhParams(nonce, serverNonce, p, q, fingerprint, encrypted)
    }

    // ---------------------------------------------------------------- step 4-5

    /**
     * Шаг 4-5: расшифровывает server_DH_inner_data, проверяет группу,
     * возвращает set_client_DH_params.
     */
    fun onServerDhParams(body: ByteArray): ByteArray {
        check(state == State.WAIT_DH_PARAMS) { "unexpected server_DH_params in state $state" }
        val params = MTProtoHandshakeTL.parseServerDhParams(body)

        if (!MTProtoCrypto.constantTimeEquals(params.nonce, nonce))
            failWith("nonce mismatch in server_DH_params")
        if (!MTProtoCrypto.constantTimeEquals(params.serverNonce, serverNonce))
            failWith("server_nonce mismatch in server_DH_params")

        // Ключ и IV этого этапа выводятся из new_nonce и server_nonce.
        val (aesKey, aesIv) = tempKeyIv()
        if (params.encryptedAnswer.size % 16 != 0)
            failWith("encrypted_answer not aligned")
        val decrypted = AesIge.decrypt(params.encryptedAnswer, aesKey, aesIv)

        // Первые 20 байт — SHA1 от TL-объекта, дальше сам объект + паддинг.
        if (decrypted.size < 20) failWith("decrypted answer too short")
        val expectedHash = decrypted.copyOfRange(0, 20)
        val payload = decrypted.copyOfRange(20, decrypted.size)

        val innerData = MTProtoHandshakeTL.parseServerDhInnerData(payload)

        // Пересобираем объект, чтобы узнать его точную длину, и сверяем SHA1.
        // Без этой проверки сервер может подсунуть произвольные параметры.
        val innerLength = serverDhInnerLength(innerData)
        if (innerLength > payload.size) failWith("inner data length overflow")
        val actualHash = MessageDigest.getInstance("SHA-1")
            .digest(payload.copyOfRange(0, innerLength))
        if (!MTProtoCrypto.constantTimeEquals(expectedHash, actualHash))
            failWith("server_DH_inner_data hash mismatch")

        if (!MTProtoCrypto.constantTimeEquals(innerData.nonce, nonce))
            failWith("nonce mismatch in server_DH_inner_data")
        if (!MTProtoCrypto.constantTimeEquals(innerData.serverNonce, serverNonce))
            failWith("server_nonce mismatch in server_DH_inner_data")

        val p = BigInteger(1, innerData.dhPrime)
        val g = BigInteger.valueOf(innerData.g.toLong())

        // Главная проверка: группа сервера должна быть безопасной.
        if (!MTProtoDh.isSafeGroup(p, g))
            failWith("server offered unsafe DH group")

        val gA = BigInteger(1, innerData.gA)
        MTProtoDh.checkDhValue(gA, p)

        dhPrime = p
        dhG = g
        gAValue = gA

        // Запоминаем расхождение часов: msg_id зависит от времени сервера.
        timeOffsetSeconds = innerData.serverTime - (System.currentTimeMillis() / 1000L).toInt()

        val b = MTProtoDh.generateSecret(p)
        secretB = b
        val gB = g.modPow(b, p)
        MTProtoDh.checkDhValue(gB, p)

        val clientInner = MTProtoHandshakeTL.clientDhInnerData(
            nonce, serverNonce, retryId, gB.toByteArray().let { stripSign(it) }
        )
        val shaInner = MessageDigest.getInstance("SHA-1").digest(clientInner)
        val toEncrypt = shaInner + clientInner
        val padLen = (16 - toEncrypt.size % 16) % 16
        val paddedInner = toEncrypt + ByteArray(padLen).also { random.nextBytes(it) }
        val encrypted = AesIge.encrypt(paddedInner, aesKey, aesIv)

        Arrays.fill(aesKey, 0)
        Arrays.fill(aesIv, 0)

        state = State.WAIT_DH_GEN
        return MTProtoHandshakeTL.setClientDhParams(nonce, serverNonce, encrypted)
    }

    // ---------------------------------------------------------------- step 6

    /**
     * Шаг 6: проверяет dh_gen_ok и фиксирует auth_key.
     * @return true если ключ готов; false если сервер просит повтор.
     */
    fun onDhGen(body: ByteArray): Boolean {
        check(state == State.WAIT_DH_GEN) { "unexpected dh_gen in state $state" }
        val result = MTProtoHandshakeTL.parseDhGen(body)

        if (!MTProtoCrypto.constantTimeEquals(result.nonce, nonce))
            failWith("nonce mismatch in dh_gen")
        if (!MTProtoCrypto.constantTimeEquals(result.serverNonce, serverNonce))
            failWith("server_nonce mismatch in dh_gen")

        val b = secretB ?: failWith("missing local secret")
        val p = dhPrime ?: failWith("missing dh_prime")

        val gA = gAValue ?: failWith("missing g_a")
        val key = MTProtoDh.toFixed256(gA.modPow(b, p))

        when (result.status) {
            MTProtoHandshakeTL.DH_GEN_OK -> {
                val expected = MTProtoDh.newNonceHash(newNonce, 1, key)
                if (!MTProtoCrypto.constantTimeEquals(expected, result.newNonceHash))
                    failWith("new_nonce_hash1 mismatch: possible MITM")

                authKey = key
                serverSalt = computeServerSalt()
                secretB = null
                state = State.DONE
                return true
            }
            MTProtoHandshakeTL.DH_GEN_RETRY -> {
                val expected = MTProtoDh.newNonceHash(newNonce, 2, key)
                if (!MTProtoCrypto.constantTimeEquals(expected, result.newNonceHash))
                    failWith("new_nonce_hash2 mismatch")
                retryId = MTProtoCrypto.readLongLE(
                    MessageDigest.getInstance("SHA-1").digest(key), 0
                )
                state = State.WAIT_DH_PARAMS
                return false
            }
            else -> failWith("server returned dh_gen_fail")
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Временные ключ и IV этапа DH:
     *   key = SHA1(new_nonce+server_nonce) + substr(SHA1(server_nonce+new_nonce),0,12)
     *   iv  = substr(SHA1(server_nonce+new_nonce),12,8)
     *       + SHA1(new_nonce+new_nonce) + substr(new_nonce,0,4)
     */
    private fun tempKeyIv(): Pair<ByteArray, ByteArray> {
        fun sha1(vararg parts: ByteArray): ByteArray {
            val d = MessageDigest.getInstance("SHA-1")
            for (p in parts) d.update(p)
            return d.digest()
        }
        val nsn = sha1(newNonce, serverNonce)
        val snn = sha1(serverNonce, newNonce)
        val nnn = sha1(newNonce, newNonce)

        val key = nsn + snn.copyOfRange(0, 12)
        val iv = snn.copyOfRange(12, 20) + nnn + newNonce.copyOfRange(0, 4)
        return Pair(key, iv)
    }

    /** server_salt = первые 8 байт new_nonce XOR первые 8 байт server_nonce. */
    private fun computeServerSalt(): Long {
        val salt = ByteArray(8)
        for (i in 0 until 8) salt[i] = (newNonce[i].toInt() xor serverNonce[i].toInt()).toByte()
        return MTProtoCrypto.readLongLE(salt, 0)
    }

    /** Точная длина сериализованного server_DH_inner_data (для проверки SHA1). */
    private fun serverDhInnerLength(d: MTProtoHandshakeTL.ServerDhInnerData): Int {
        fun tlBytesLen(n: Int): Int =
            if (n <= 253) { val t = n + 1; t + ((4 - t % 4) % 4) }
            else { val t = n + 4; t + ((4 - t % 4) % 4) }
        return 4 + 16 + 16 + 4 + tlBytesLen(d.dhPrime.size) + tlBytesLen(d.gA.size) + 4
    }

    private fun stripSign(b: ByteArray): ByteArray =
        if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b

    private fun failWith(message: String): Nothing {
        state = State.FAILED
        throw MTProtoCrypto.SecurityViolation(message)
    }
}
