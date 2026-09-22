package com.example.mtproto

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Diffie–Hellman для получения auth_key (2048 бит) по правилам MTProto.
 *
 * Заменяет KeyPairGenerator("DH") из MTProtoManager: JCA сама выбирает
 * группу и НЕ проверяет присланные сервером параметры, из-за чего
 * вредоносный сервер может навязать слабую группу и восстановить ключ.
 *
 * Референс: https://core.telegram.org/mtproto/security_guidelines
 */
object MTProtoDh {

    private val random = SecureRandom()

    /** Безопасное простое, используемое MTProto (g = 3). */
    val P: BigInteger = BigInteger(
        "C71CAEB9C6B1C9048E6C522F70F13F73980D40238E3E21C14934D037563D930F" +
        "48198A0AA7C14058229493D22530F4DBFA336F6E0AC925139543AED44CCE7C37" +
        "20FD51F69458705AC68CD4FE6B6B13ABDC9746512969328454F18FAF8C595F64" +
        "2477FE96BB2A941D5BCD1D4AC8CC49880708FA9B378E3C4F3A9060BEE67CF9A4" +
        "A4A695811051907E162753B56B0F6B410DBA74D8A84B2A14B3144E0EF1284754" +
        "FD17ED950D5965B4B9DD46582DB1178D169C6BC465B0D6FF9CA3928FEF5B9AE4" +
        "E418FC15E83EBEA0F87FA9FF5EED70050DED2849F47BF959D956850CE929851F" +
        "0D8115F635B105EE2E4E15D04B2454BF6F4FADF034B10403119CD8E3B92FCC5B",
        16
    )

    val G: BigInteger = BigInteger.valueOf(3L)

    /** Нижняя граница безопасного диапазона: 2^(2048-64). */
    private val MIN_BOUND: BigInteger = BigInteger.ONE.shiftLeft(2048 - 64)

    /**
     * Проверка, что группа (p, g) пригодна:
     * p — 2048-битное безопасное простое, q = (p-1)/2 тоже простое,
     * g порождает подгруппу порядка q.
     *
     * Кэшируется: проверка простоты на Celeron занимает заметное время,
     * а параметры сервера меняются крайне редко.
     */
    private val verifiedGroups = HashSet<Pair<BigInteger, BigInteger>>()

    fun isSafeGroup(p: BigInteger, g: BigInteger): Boolean {
        val cacheKey = Pair(p, g)
        synchronized(verifiedGroups) {
            if (verifiedGroups.contains(cacheKey)) return true
        }
        if (p.bitLength() != 2048) return false
        if (g < BigInteger.TWO || g >= p - BigInteger.ONE) return false
        if (!p.isProbablePrime(64)) return false
        val q = p.subtract(BigInteger.ONE).shiftRight(1)
        if (!q.isProbablePrime(64)) return false
        // g должен лежать в подгруппе простого порядка q, иначе утекают биты ключа
        if (g.modPow(q, p) != BigInteger.ONE) return false
        synchronized(verifiedGroups) { verifiedGroups.add(cacheKey) }
        return true
    }

    /**
     * Проверка публичного значения DH.
     * Отсекает 0, 1, p-1 и значения у самых границ, дающие
     * малый порядок и предсказуемый общий секрет.
     */
    fun checkDhValue(value: BigInteger, p: BigInteger) {
        if (value <= BigInteger.ONE || value >= p.subtract(BigInteger.ONE))
            throw MTProtoCrypto.SecurityViolation("DH value out of range")
        if (value < MIN_BOUND || value > p.subtract(MIN_BOUND))
            throw MTProtoCrypto.SecurityViolation("DH value too close to boundary")
    }

    /** Генерация секретной экспоненты b (2048 бит). */
    fun generateSecret(p: BigInteger = P): BigInteger {
        while (true) {
            val b = BigInteger(2048, random)
            if (b > BigInteger.ONE && b < p.subtract(BigInteger.ONE)) return b
        }
    }

    data class DhResult(
        val authKey: ByteArray,      // 256 байт
        val authKeyAuxHash: ByteArray, // 8 байт, для подтверждения серверу
        val gB: BigInteger           // отправляется серверу
    )

    /**
     * Вычисляет auth_key из g_a сервера и локального секрета b.
     *
     * @param verifyGroup отключать только в тестах — в бою обязательна.
     */
    fun computeAuthKey(
        gA: BigInteger,
        b: BigInteger,
        p: BigInteger = P,
        g: BigInteger = G,
        verifyGroup: Boolean = true
    ): DhResult {
        if (verifyGroup && !isSafeGroup(p, g))
            throw MTProtoCrypto.SecurityViolation("server DH group is not a safe prime group")

        checkDhValue(gA, p)
        val gB = g.modPow(b, p)
        checkDhValue(gB, p)

        val shared = gA.modPow(b, p)
        val authKey = toFixed256(shared)
        val aux = MessageDigest.getInstance("SHA-1").digest(authKey).copyOfRange(0, 8)
        return DhResult(authKey, aux, gB)
    }

    /** BigInteger -> ровно 256 байт big-endian (без знакового байта). */
    fun toFixed256(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val out = ByteArray(256)
        when {
            raw.size == 256 -> System.arraycopy(raw, 0, out, 0, 256)
            raw.size < 256 -> System.arraycopy(raw, 0, out, 256 - raw.size, raw.size)
            else -> System.arraycopy(raw, raw.size - 256, out, 0, 256) // срезаем знаковый 0x00
        }
        return out
    }

    /** new_nonce_hash для завершения handshake (dh_gen_ok/retry/fail). */
    fun newNonceHash(newNonce: ByteArray, num: Int, authKey: ByteArray): ByteArray {
        val aux = MessageDigest.getInstance("SHA-1").digest(authKey).copyOfRange(0, 8)
        val d = MessageDigest.getInstance("SHA-1")
        d.update(newNonce)
        d.update(byteArrayOf(num.toByte()))
        d.update(aux)
        return d.digest().copyOfRange(4, 20)
    }
}
