package com.example.mtproto

import java.math.BigInteger
import java.security.SecureRandom

/**
 * Факторизация pq и RSA-слой для handshake.
 *
 * Сервер присылает pq = p*q, где p и q — простые около 2^31.
 * Клиент обязан их разложить. Это доказательство работы: защита
 * от того, чтобы кто угодно бесплатно инициировал тысячи handshake
 * и клал сервер (DoS).
 *
 * Референс: https://core.telegram.org/mtproto/auth_key
 */
object MTProtoPq {

    private val random = SecureRandom()

    /**
     * Алгоритм Полларда «ро» (вариант Брента).
     * Раскладывает 64-битное pq за единицы миллисекунд.
     *
     * @return пара (p, q), где p < q
     */
    fun factorize(pq: Long): Pair<Long, Long> {
        if (pq <= 0) throw MTProtoCrypto.SecurityViolation("pq must be positive")
        if (pq % 2 == 0L) return normalize(2L, pq / 2)

        var attempt = 0
        while (attempt < 64) {
            val divisor = brent(pq)
            if (divisor != pq && divisor > 1L) {
                return normalize(divisor, pq / divisor)
            }
            attempt++
        }
        throw MTProtoCrypto.SecurityViolation("failed to factorize pq=$pq")
    }

    private fun normalize(a: Long, b: Long): Pair<Long, Long> =
        if (a < b) Pair(a, b) else Pair(b, a)

    private fun brent(n: Long): Long {
        if (n % 2 == 0L) return 2L
        var y = (random.nextLong() and Long.MAX_VALUE) % (n - 1) + 1
        val c = (random.nextLong() and Long.MAX_VALUE) % (n - 1) + 1
        val m = 128L
        var g = 1L
        var r = 1L
        var q = 1L
        var x = 0L
        var ys = 0L

        while (g == 1L) {
            x = y
            var i = 0L
            while (i < r) { y = addMod(mulMod(y, y, n), c, n); i++ }
            var k = 0L
            while (k < r && g == 1L) {
                ys = y
                val bound = minOf(m, r - k)
                var j = 0L
                while (j < bound) {
                    y = addMod(mulMod(y, y, n), c, n)
                    q = mulMod(q, Math.abs(x - y), n)
                    j++
                }
                g = gcd(q, n)
                k += m
            }
            r *= 2
        }

        if (g == n) {
            g = 1L
            while (g == 1L) {
                ys = addMod(mulMod(ys, ys, n), c, n)
                g = gcd(Math.abs(x - ys), n)
            }
        }
        return g
    }

    /**
     * Умножение по модулю без переполнения.
     * pq укладывается в 63 бита, но x*y — нет, поэтому идём через BigInteger
     * только когда это действительно нужно.
     */
    private fun mulMod(a: Long, b: Long, m: Long): Long {
        if (a == 0L || b == 0L) return 0L
        // Быстрый путь: произведение гарантированно не переполнится.
        if (a < Int.MAX_VALUE && b < Int.MAX_VALUE) return (a * b) % m
        return BigInteger.valueOf(a)
            .multiply(BigInteger.valueOf(b))
            .mod(BigInteger.valueOf(m))
            .toLong()
    }

    private fun addMod(a: Long, b: Long, m: Long): Long {
        val s = a + b
        return if (s < 0 || s >= m) Math.floorMod(s, m) else s
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = Math.abs(a)
        var y = Math.abs(b)
        while (y != 0L) { val t = x % y; x = y; y = t }
        return x
    }

    // ------------------------------------------------------------ RSA

    /**
     * Открытый ключ сервера для шифрования p_q_inner_data.
     *
     * ВАЖНО: ключи должны быть зашиты в приложение. Если брать их из сети,
     * подменивший их сервер проведёт MITM и получит твой auth_key.
     */
    data class RsaKey(val n: BigInteger, val e: BigInteger) {

        /**
         * Отпечаток = младшие 8 байт SHA1(TL-сериализация(n, e)).
         * По нему сервер понимает, каким ключом клиент шифровал.
         */
        val fingerprint: Long by lazy {
            val out = java.io.ByteArrayOutputStream()
            com.example.tlschema.TLStream.writeByteArray(out, trimLeadingZero(n.toByteArray()))
            com.example.tlschema.TLStream.writeByteArray(out, trimLeadingZero(e.toByteArray()))
            val sha = java.security.MessageDigest.getInstance("SHA-1").digest(out.toByteArray())
            var v = 0L
            for (i in 7 downTo 0) v = (v shl 8) or (sha[12 + i].toLong() and 0xFF)
            v
        }

        private fun trimLeadingZero(b: ByteArray): ByteArray =
            if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b

        /** «Сырое» RSA без паддинга — так задано в спецификации MTProto. */
        fun encryptRaw(data: ByteArray): ByteArray {
            if (data.size > 255)
                throw MTProtoCrypto.SecurityViolation("RSA payload too large")
            val m = BigInteger(1, data)
            if (m >= n)
                throw MTProtoCrypto.SecurityViolation("RSA payload >= modulus")
            return MTProtoDh.toFixed256(m.modPow(e, n))
        }
    }
}
