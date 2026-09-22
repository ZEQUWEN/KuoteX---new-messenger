package com.example.mtproto

import com.example.tlschema.TLStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * TL-объекты, участвующие в создании auth_key.
 *
 * Это отдельная «служебная» схема: она передаётся в незашифрованных
 * сообщениях (auth_key_id = 0), потому что ключа ещё нет.
 *
 * Референс: https://core.telegram.org/mtproto/auth_key
 */
object MTProtoHandshakeTL {

    // Идентификаторы конструкторов из схемы Telegram.
    const val REQ_PQ_MULTI = 0xbe7e8ef1.toInt()
    const val RES_PQ = 0x05162463
    const val P_Q_INNER_DATA_DC = 0xa9f55f95.toInt()
    const val REQ_DH_PARAMS = 0xd712e4be.toInt()
    const val SERVER_DH_PARAMS_OK = 0xd0e8075c.toInt()
    const val SERVER_DH_PARAMS_FAIL = 0x79cb045d
    const val SERVER_DH_INNER_DATA = 0xb5890dba.toInt()
    const val CLIENT_DH_INNER_DATA = 0x6643b654
    const val SET_CLIENT_DH_PARAMS = 0xf5045f1f.toInt()
    const val DH_GEN_OK = 0x3bcbf734
    const val DH_GEN_RETRY = 0x46dc1fb9
    const val DH_GEN_FAIL = 0xa69dae02.toInt()

    private fun le32(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte()
    )

    // ------------------------------------------------------------ req_pq_multi

    /** req_pq_multi#be7e8ef1 nonce:int128 = ResPQ */
    fun reqPqMulti(nonce: ByteArray): ByteArray {
        require(nonce.size == 16)
        return le32(REQ_PQ_MULTI) + nonce
    }

    data class ResPQ(
        val nonce: ByteArray,
        val serverNonce: ByteArray,
        val pq: Long,
        val fingerprints: List<Long>
    )

    /** resPQ#05162463 nonce server_nonce pq:bytes fingerprints:Vector<long> */
    fun parseResPq(data: ByteArray): ResPQ {
        val input = ByteArrayInputStream(data)
        val ctor = TLStream.readInt(input)
        if (ctor != RES_PQ)
            throw MTProtoCrypto.SecurityViolation(
                "expected resPQ, got 0x${Integer.toHexString(ctor)}"
            )

        val nonce = ByteArray(16).also { input.read(it) }
        val serverNonce = ByteArray(16).also { input.read(it) }
        val pqBytes = TLStream.readByteArray(input)
        if (pqBytes.size > 8)
            throw MTProtoCrypto.SecurityViolation("pq longer than 64 bits")

        var pq = 0L
        for (b in pqBytes) pq = (pq shl 8) or (b.toLong() and 0xFF) // big-endian

        val vectorCtor = TLStream.readInt(input)
        if (vectorCtor != 0x1cb5c415)
            throw MTProtoCrypto.SecurityViolation("expected Vector constructor")
        val count = TLStream.readInt(input)
        if (count < 0 || count > 64)
            throw MTProtoCrypto.SecurityViolation("bad fingerprint count")
        val fingerprints = (0 until count).map { TLStream.readLong(input) }

        return ResPQ(nonce, serverNonce, pq, fingerprints)
    }

    // ------------------------------------------------------------ p_q_inner_data

    /**
     * p_q_inner_data_dc#a9f55f95 pq p q nonce server_nonce new_nonce dc:int
     *
     * p и q кодируются как big-endian bytes без ведущих нулей.
     */
    fun pqInnerData(
        pq: Long, p: Long, q: Long,
        nonce: ByteArray, serverNonce: ByteArray, newNonce: ByteArray,
        dcId: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, P_Q_INNER_DATA_DC)
        TLStream.writeByteArray(out, toBigEndianBytes(pq))
        TLStream.writeByteArray(out, toBigEndianBytes(p))
        TLStream.writeByteArray(out, toBigEndianBytes(q))
        out.write(nonce)
        out.write(serverNonce)
        out.write(newNonce)
        TLStream.writeInt(out, dcId)
        return out.toByteArray()
    }

    /** Число в big-endian без ведущих нулей (как требует TL для pq/p/q). */
    fun toBigEndianBytes(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf(0)
        var started = false
        val out = ByteArrayOutputStream()
        for (i in 7 downTo 0) {
            val b = ((value ushr (8 * i)) and 0xFF).toInt()
            if (b != 0) started = true
            if (started) out.write(b)
        }
        return out.toByteArray()
    }

    /** req_DH_params#d712e4be nonce server_nonce p q fingerprint encrypted_data */
    fun reqDhParams(
        nonce: ByteArray, serverNonce: ByteArray,
        p: Long, q: Long, fingerprint: Long, encryptedData: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, REQ_DH_PARAMS)
        out.write(nonce)
        out.write(serverNonce)
        TLStream.writeByteArray(out, toBigEndianBytes(p))
        TLStream.writeByteArray(out, toBigEndianBytes(q))
        TLStream.writeLong(out, fingerprint)
        TLStream.writeByteArray(out, encryptedData)
        return out.toByteArray()
    }

    // ------------------------------------------------------------ server_DH_params

    data class ServerDhParamsOk(
        val nonce: ByteArray,
        val serverNonce: ByteArray,
        val encryptedAnswer: ByteArray
    )

    fun parseServerDhParams(data: ByteArray): ServerDhParamsOk {
        val input = ByteArrayInputStream(data)
        val ctor = TLStream.readInt(input)
        if (ctor == SERVER_DH_PARAMS_FAIL)
            throw MTProtoCrypto.SecurityViolation("server rejected DH params (server_DH_params_fail)")
        if (ctor != SERVER_DH_PARAMS_OK)
            throw MTProtoCrypto.SecurityViolation(
                "expected server_DH_params_ok, got 0x${Integer.toHexString(ctor)}"
            )
        val nonce = ByteArray(16).also { input.read(it) }
        val serverNonce = ByteArray(16).also { input.read(it) }
        val encrypted = TLStream.readByteArray(input)
        return ServerDhParamsOk(nonce, serverNonce, encrypted)
    }

    data class ServerDhInnerData(
        val nonce: ByteArray,
        val serverNonce: ByteArray,
        val g: Int,
        val dhPrime: ByteArray,
        val gA: ByteArray,
        val serverTime: Int
    )

    /** server_DH_inner_data#b5890dba nonce server_nonce g dh_prime g_a server_time */
    fun parseServerDhInnerData(data: ByteArray): ServerDhInnerData {
        val input = ByteArrayInputStream(data)
        val ctor = TLStream.readInt(input)
        if (ctor != SERVER_DH_INNER_DATA)
            throw MTProtoCrypto.SecurityViolation(
                "expected server_DH_inner_data, got 0x${Integer.toHexString(ctor)}"
            )
        val nonce = ByteArray(16).also { input.read(it) }
        val serverNonce = ByteArray(16).also { input.read(it) }
        val g = TLStream.readInt(input)
        val dhPrime = TLStream.readByteArray(input)
        val gA = TLStream.readByteArray(input)
        val serverTime = TLStream.readInt(input)
        return ServerDhInnerData(nonce, serverNonce, g, dhPrime, gA, serverTime)
    }

    /** client_DH_inner_data#6643b654 nonce server_nonce retry_id g_b */
    fun clientDhInnerData(
        nonce: ByteArray, serverNonce: ByteArray, retryId: Long, gB: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, CLIENT_DH_INNER_DATA)
        out.write(nonce)
        out.write(serverNonce)
        TLStream.writeLong(out, retryId)
        TLStream.writeByteArray(out, gB)
        return out.toByteArray()
    }

    /** set_client_DH_params#f5045f1f nonce server_nonce encrypted_data */
    fun setClientDhParams(
        nonce: ByteArray, serverNonce: ByteArray, encryptedData: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()
        TLStream.writeInt(out, SET_CLIENT_DH_PARAMS)
        out.write(nonce)
        out.write(serverNonce)
        TLStream.writeByteArray(out, encryptedData)
        return out.toByteArray()
    }

    // ------------------------------------------------------------ dh_gen

    data class DhGenResult(
        val status: Int,          // DH_GEN_OK / RETRY / FAIL
        val nonce: ByteArray,
        val serverNonce: ByteArray,
        val newNonceHash: ByteArray
    )

    fun parseDhGen(data: ByteArray): DhGenResult {
        val input = ByteArrayInputStream(data)
        val ctor = TLStream.readInt(input)
        if (ctor != DH_GEN_OK && ctor != DH_GEN_RETRY && ctor != DH_GEN_FAIL)
            throw MTProtoCrypto.SecurityViolation(
                "expected dh_gen_*, got 0x${Integer.toHexString(ctor)}"
            )
        val nonce = ByteArray(16).also { input.read(it) }
        val serverNonce = ByteArray(16).also { input.read(it) }
        val hash = ByteArray(16).also { input.read(it) }
        return DhGenResult(ctor, nonce, serverNonce, hash)
    }

    // ------------------------------------------------------------ envelope

    /**
     * Незашифрованное сообщение: auth_key_id(0) + msg_id + length + body.
     * Используется только во время handshake.
     */
    fun wrapUnencrypted(body: ByteArray, messageId: Long): ByteArray {
        val out = ByteArray(20 + body.size)
        MTProtoCrypto.writeLongLE(out, 0, 0L)          // auth_key_id = 0
        MTProtoCrypto.writeLongLE(out, 8, messageId)
        MTProtoCrypto.writeIntLE(out, 16, body.size)
        System.arraycopy(body, 0, out, 20, body.size)
        return out
    }

    fun unwrapUnencrypted(data: ByteArray): ByteArray {
        if (data.size < 20)
            throw MTProtoCrypto.SecurityViolation("unencrypted message too short")
        val authKeyId = MTProtoCrypto.readLongLE(data, 0)
        if (authKeyId != 0L)
            throw MTProtoCrypto.SecurityViolation("expected unencrypted message")
        val length = MTProtoCrypto.readIntLE(data, 16)
        if (length < 0 || 20 + length > data.size)
            throw MTProtoCrypto.SecurityViolation("bad unencrypted length")
        return data.copyOfRange(20, 20 + length)
    }
}
