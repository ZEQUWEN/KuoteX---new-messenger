package com.example.data.ecosystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class PaymentGuardTest {

    @Test
    fun sameOperationGetsSameKey() {
        val guard = PaymentGuard()
        val key = PaymentGuard.giftKey("rose", "user42")

        // Повторные нажатия обязаны дать один ключ, иначе сервер
        // сочтёт их разными операциями и спишет дважды.
        assertEquals(guard.keyFor(key), guard.keyFor(key))
    }

    @Test
    fun differentOperationsGetDifferentKeys() {
        val guard = PaymentGuard()
        assertNotEquals(
            guard.keyFor(PaymentGuard.giftKey("rose", "user1")),
            guard.keyFor(PaymentGuard.giftKey("rose", "user2"))
        )
    }

    @Test
    fun keyIsRegeneratedAfterRelease() {
        val guard = PaymentGuard()
        val op = PaymentGuard.giftKey("rose", "user42")

        val first = guard.keyFor(op)
        guard.release(op)
        val second = guard.keyFor(op)

        // После завершённой покупки следующая — новая операция.
        assertNotEquals(first, second)
    }

    @Test
    fun secondTapIsIgnoredWhileInFlight() = runBlocking {
        val guard = PaymentGuard()
        val op = PaymentGuard.giftKey("rose", "user42")
        val calls = AtomicInteger()

        val results = coroutineScope {
            (1..10).map {
                async(Dispatchers.Default) {
                    guard.guarded<String>(op) { _ ->
                        calls.incrementAndGet()
                        delay(50)
                        Result.success("ok")
                    }
                }
            }.awaitAll()
        }

        // Ровно одна операция ушла на сервер, остальные отсеяны.
        assertEquals(1, calls.get())
        assertEquals(1, results.count { it != null })
        assertEquals(9, results.count { it == null })
    }

    @Test
    fun keyIsKeptAfterNetworkFailure() = runBlocking {
        val guard = PaymentGuard()
        val op = PaymentGuard.topUpKey("stars_500")

        val keyDuringFirstAttempt = guard.keyFor(op)
        guard.guarded<String>(op) { throw IOException("timeout") }

        // Исход неизвестен: ключ обязан сохраниться, чтобы повтор
        // ушёл как та же операция, а не как новая покупка.
        assertEquals(keyDuringFirstAttempt, guard.keyFor(op))
    }

    @Test
    fun keyIsReleasedAfterRejection() = runBlocking {
        val guard = PaymentGuard()
        val op = PaymentGuard.giftKey("diamond", "user1")
        val firstKey = guard.keyFor(op)

        guard.guarded<String>(op) {
            Result.failure(PaymentRejected(402, "insufficient funds"))
        }

        // Отказ окончательный — следующая попытка это новая операция.
        assertFalse(guard.isInFlight(op))
        assertNotEquals(firstKey, guard.keyFor(op))
    }

    @Test
    fun successfulOperationReleasesKey() = runBlocking {
        val guard = PaymentGuard()
        val op = PaymentGuard.vipKey("vip_30d")

        val result = guard.guarded(op) { Result.success("done") }

        assertNotNull(result)
        assertTrue(result!!.isSuccess)
        assertFalse(guard.isInFlight(op))
    }

    @Test
    fun retryAfterTimeoutReusesKey() = runBlocking {
        val guard = PaymentGuard()
        val op = PaymentGuard.topUpKey("stars_1000")
        val usedKeys = mutableListOf<String>()

        // Первая попытка: таймаут.
        guard.guarded<String>(op) { key ->
            usedKeys.add(key)
            throw IOException("timeout")
        }
        // Повтор после восстановления сети.
        guard.guarded<String>(op) { key ->
            usedKeys.add(key)
            Result.success("ok")
        }

        assertEquals(2, usedKeys.size)
        // Оба запроса ушли с одним ключом — сервер начислит один раз.
        assertEquals(usedKeys[0], usedKeys[1])
    }

    @Test
    fun operationKeysAreStableAcrossInstances() {
        // Ключ намерения не должен зависеть от времени или случайности.
        assertEquals(
            PaymentGuard.giftKey("rose", "u1"),
            PaymentGuard.giftKey("rose", "u1")
        )
        assertEquals("gift:rose:u1", PaymentGuard.giftKey("rose", "u1"))
        assertEquals("topup:stars_500", PaymentGuard.topUpKey("stars_500"))
        assertEquals("vip:vip_30d", PaymentGuard.vipKey("vip_30d"))
    }
}
