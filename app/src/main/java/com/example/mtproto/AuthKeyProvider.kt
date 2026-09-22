package com.example.mtproto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Связывает хранилище ключей с процессом handshake.
 *
 * Отвечает на вопрос «дай рабочий auth_key для дата-центра N»:
 *  - есть сохранённый и не просрочен -> отдаёт его;
 *  - нет или просрочен -> проводит handshake и сохраняет результат.
 *
 * Mutex обязателен: без него запуск приложения с несколькими экранами
 * стартует два handshake параллельно, и второй перезапишет ключ первого,
 * оборвав уже установленное соединение.
 */
class AuthKeyProvider(
    private val store: AuthKeyStore,
    private val rsaKeys: List<MTProtoPq.RsaKey>,
    private val maxAgeMs: Long = AuthKeyStore.DEFAULT_MAX_AGE_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Выполняет обмен байтами с сервером. Реализуется поверх транспорта. */
    fun interface HandshakeTransport {
        /** Отправляет незашифрованный запрос и возвращает тело ответа. */
        suspend fun exchange(request: ByteArray): ByteArray
    }

    private val mutex = Mutex()

    /**
     * Возвращает готовую сессию для дата-центра, при необходимости
     * выполняя handshake.
     */
    suspend fun obtainSession(
        dcId: Int,
        forceNew: Boolean = false,
        transport: HandshakeTransport
    ): MTProtoSession = mutex.withLock {
        if (!forceNew) {
            val cached = store.load(dcId)
            if (cached != null && !isExpired(cached)) {
                return@withLock MTProtoSession(cached.authKey, isClient = true).apply {
                    serverSalt = cached.serverSalt
                }
            }
            // Просроченный ключ удаляем сразу, чтобы не использовать его случайно.
            if (cached != null) store.remove(dcId)
        }

        val entry = performHandshake(dcId, transport)
        store.save(entry)

        MTProtoSession(entry.authKey, isClient = true).apply {
            serverSalt = entry.serverSalt
        }
    }

    /** Сохранённый ключ есть и годен. */
    fun hasValidKey(dcId: Int): Boolean {
        val entry = store.load(dcId) ?: return false
        return !isExpired(entry)
    }

    /** Новая соль от сервера (bad_server_salt). Ключ при этом не меняется. */
    fun updateSalt(dcId: Int, serverSalt: Long) = store.updateSalt(dcId, serverSalt)

    /**
     * Сервер не знает наш ключ (транспортная ошибка -404).
     * Запись бесполезна — удаляем, следующий вызов сделает handshake.
     */
    fun invalidate(dcId: Int) = store.remove(dcId)

    /** Выход из аккаунта: все ключи должны исчезнуть. */
    fun logout() = store.clear()

    private fun isExpired(entry: AuthKeyStore.Entry): Boolean {
        val age = entry.ageMs(clock())
        // Отрицательный возраст = часы устройства перевели назад.
        // Доверять такой записи нельзя.
        return age < 0 || age > maxAgeMs
    }

    private suspend fun performHandshake(
        dcId: Int,
        transport: HandshakeTransport
    ): AuthKeyStore.Entry {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            // Новый экземпляр на каждую попытку: автомат одноразовый,
            // повторный start() на уже начатом handshake недопустим.
            val handshake = MTProtoHandshake(dcId, rsaKeys)
            val resPq = transport.exchange(handshake.start())
            val reqDh = handshake.onResPq(resPq)

            val serverDhParams = transport.exchange(reqDh)
            val setClientDh = handshake.onServerDhParams(serverDhParams)

            val dhGen = transport.exchange(setClientDh)
            if (handshake.onDhGen(dhGen)) {
                val authKey = handshake.authKey
                    ?: throw MTProtoCrypto.SecurityViolation("handshake finished without auth_key")
                return AuthKeyStore.Entry(
                    dcId = dcId,
                    authKey = authKey,
                    serverSalt = handshake.serverSalt,
                    createdAtMs = clock()
                )
            }
            // dh_gen_retry: сервер просит повторить с новым retry_id.
            attempt++
        }
        throw MTProtoCrypto.SecurityViolation("handshake failed after $MAX_RETRIES retries")
    }

    private companion object {
        const val MAX_RETRIES = 5
    }
}
