package com.example.mtproto

import java.util.Arrays

/**
 * Хранилище auth_key для MTProto.
 *
 * Интерфейс отделён от Android намеренно: логика ротации, срока жизни
 * и очистки тестируется на JVM без эмулятора, а платформенная часть
 * (EncryptedSharedPreferences) подставляется отдельной реализацией.
 *
 * Что здесь хранится и почему это критично:
 *  - auth_key (256 байт) — главный секрет. Кто им завладел, тот читает
 *    и подделывает всю переписку. Обычный SharedPreferences не подходит:
 *    на устройстве с root файл читается открытым текстом.
 *  - server_salt — привязан к ключу, без него сервер отвергает сообщения.
 *  - время создания — MTProto рекомендует перевыпускать ключ раз в сутки-
 *    неделю, чтобы ограничить ущерб от возможной утечки.
 */
interface AuthKeyStore {

    /** Сохранённые данные одного дата-центра. */
    data class Entry(
        val dcId: Int,
        val authKey: ByteArray,
        val serverSalt: Long,
        val createdAtMs: Long
    ) {
        init {
            if (authKey.size != AUTH_KEY_SIZE)
                throw MTProtoCrypto.SecurityViolation("auth_key must be 256 bytes")
        }

        /** Идентификатор ключа — по нему сервер выбирает, чем расшифровывать. */
        val authKeyId: Long get() = MTProtoCrypto.authKeyId(authKey)

        fun ageMs(nowMs: Long = System.currentTimeMillis()): Long = nowMs - createdAtMs

        /** Затирает ключ в памяти. Вызывать, когда запись больше не нужна. */
        fun wipe() {
            Arrays.fill(authKey, 0)
        }

        // equals/hashCode переопределены: ByteArray сравнивается по ссылке,
        // из-за чего data class ведёт себя неожиданно.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Entry) return false
            return dcId == other.dcId &&
                serverSalt == other.serverSalt &&
                createdAtMs == other.createdAtMs &&
                authKey.contentEquals(other.authKey)
        }

        override fun hashCode(): Int {
            var result = dcId
            result = 31 * result + authKey.contentHashCode()
            result = 31 * result + serverSalt.hashCode()
            result = 31 * result + createdAtMs.hashCode()
            return result
        }
    }

    fun save(entry: Entry)
    fun load(dcId: Int): Entry?
    fun updateSalt(dcId: Int, serverSalt: Long)
    fun remove(dcId: Int)
    fun clear()
    fun storedDcIds(): Set<Int>

    companion object {
        const val AUTH_KEY_SIZE = 256

        /**
         * Рекомендуемый срок жизни ключа. По истечении нужен новый
         * handshake: это ограничивает окно, в котором украденный ключ
         * пригоден злоумышленнику.
         */
        const val DEFAULT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000   // 7 суток
    }
}

/**
 * Реализация в памяти — для тестов и режима «инкогнито», когда ключ
 * намеренно не должен пережить перезапуск приложения.
 */
class InMemoryAuthKeyStore : AuthKeyStore {

    private val entries = HashMap<Int, AuthKeyStore.Entry>()
    private val lock = Any()

    override fun save(entry: AuthKeyStore.Entry) {
        synchronized(lock) {
            // Копируем массив: вызывающий код может затереть свой экземпляр.
            entries[entry.dcId] = entry.copy(authKey = entry.authKey.copyOf())
        }
    }

    override fun load(dcId: Int): AuthKeyStore.Entry? = synchronized(lock) {
        entries[dcId]?.let { it.copy(authKey = it.authKey.copyOf()) }
    }

    override fun updateSalt(dcId: Int, serverSalt: Long) {
        synchronized(lock) {
            entries[dcId]?.let { entries[dcId] = it.copy(serverSalt = serverSalt) }
        }
    }

    override fun remove(dcId: Int) {
        synchronized(lock) {
            entries.remove(dcId)?.wipe()
        }
    }

    override fun clear() {
        synchronized(lock) {
            entries.values.forEach { it.wipe() }
            entries.clear()
        }
    }

    override fun storedDcIds(): Set<Int> = synchronized(lock) { entries.keys.toSet() }
}
