package com.example.data.ecosystem

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Защита платёжных операций на клиенте.
 *
 * Важно понимать границы: клиент **не может** обеспечить безопасность
 * платежей. Любую проверку здесь обходят, отправив запрос в обход
 * приложения. Настоящая защита — на сервере (payments/ledger.py).
 *
 * Этот класс решает две прикладные задачи:
 *
 * 1. **Ключ идемпотентности.** Генерируется один раз при нажатии кнопки
 *    и переиспользуется при повторах и ретраях. Именно по нему сервер
 *    понимает, что это та же операция, а не новая.
 *
 * 2. **Блокировка повторного нажатия.** Пока запрос в полёте, кнопка
 *    не срабатывает снова. Это удобство для пользователя, а не защита.
 */
class PaymentGuard {

    /** Ключи идемпотентности: живут, пока исход операции неизвестен. */
    private val inFlight = ConcurrentHashMap<String, String>()

    /** Операции, выполняющиеся прямо сейчас (защита от двойного нажатия). */
    private val running = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Ключ идемпотентности для операции.
     *
     * @param operationKey стабильный идентификатор намерения,
     *   например "gift:rose:user42" — один и тот же при повторных нажатиях.
     * @return ключ, который нужно отправить серверу
     */
    fun keyFor(operationKey: String): String =
        inFlight.computeIfAbsent(operationKey) {
            "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(12)}"
        }

    /** Операция выполняется прямо сейчас — повторное нажатие игнорируем. */
    fun isInFlight(operationKey: String): Boolean = running.contains(operationKey)

    /**
     * Освобождает операцию после окончательного ответа сервера.
     *
     * Вызывать **только** при определённом исходе (успех или явная
     * ошибка). При таймауте ключ нужно сохранить: повтор обязан уйти
     * с тем же ключом, иначе сервер сочтёт его новой операцией
     * и спишет деньги второй раз.
     */
    fun release(operationKey: String) {
        inFlight.remove(operationKey)
    }

    /**
     * Выполняет платёжную операцию с защитой от двойного нажатия.
     *
     * @return null, если операция уже выполняется
     */
    suspend fun <T> guarded(
        operationKey: String,
        block: suspend (idempotencyKey: String) -> Result<T>
    ): Result<T>? {
        // Проверка и захват должны быть одной атомарной операцией.
        // Раздельные isInFlight() + keyFor() дают гонку: два потока
        // успевают пройти проверку до того, как первый займёт слот.
        val key = keyFor(operationKey)
        if (!running.add(operationKey)) return null

        return try {
            val result = block(key)
            // Ключ идемпотентности освобождаем только при определённом
            // исходе: успех или окончательный отказ сервера.
            if (result.isSuccess || result.exceptionOrNull() is PaymentRejected) {
                release(operationKey)
            }
            result
        } catch (e: Exception) {
            // Сеть отвалилась — исход неизвестен. Ключ идемпотентности
            // сохраняем, чтобы повтор ушёл как та же операция.
            Result.failure(e)
        } finally {
            // Слот освобождаем всегда, иначе после единственного сбоя
            // кнопка останется заблокированной навсегда.
            running.remove(operationKey)
        }
    }

    companion object {
        /** Стабильный ключ намерения для покупки подарка. */
        fun giftKey(giftId: String, recipientId: String): String =
            "gift:$giftId:$recipientId"

        fun topUpKey(packId: String): String = "topup:$packId"

        fun vipKey(planId: String): String = "vip:$planId"
    }
}

/**
 * Сервер отклонил операцию окончательно: недостаточно средств,
 * товар распродан, неверная цена. Повторять бессмысленно.
 */
class PaymentRejected(
    val code: Int,
    message: String
) : Exception(message)

/**
 * Исход операции неизвестен: таймаут или обрыв связи.
 *
 * Повторять можно и нужно — но **обязательно с тем же ключом
 * идемпотентности**, иначе сервер спишет деньги дважды.
 */
class PaymentOutcomeUnknown(
    val idempotencyKey: String,
    message: String
) : Exception(message)
