package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.mtproto.AuthKeyStore
import com.example.mtproto.MTProtoCrypto

/**
 * Хранилище auth_key поверх EncryptedSharedPreferences.
 *
 * Файл шифруется ключом из Android Keystore — аппаратного хранилища,
 * из которого сам ключ извлечь нельзя даже с root. Обычный
 * SharedPreferences здесь неприменим: это простой XML, и на устройстве
 * с root он читается открытым текстом вместе с auth_key.
 *
 * Хранится в отдельном файле `mtproto_keys`, а не в общем `secure_prefs`
 * из SecureStorageHelper: у ключей MTProto другой жизненный цикл
 * (сбрасываются при logout и при ротации), и мешать их с токенами
 * и настройками — значит рисковать снести лишнее.
 */
class EncryptedAuthKeyStore(context: Context) : AuthKeyStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun save(entry: AuthKeyStore.Entry) {
        prefs.edit()
            .putString(keyOf(entry.dcId), Base64.encodeToString(entry.authKey, Base64.NO_WRAP))
            .putLong(saltOf(entry.dcId), entry.serverSalt)
            .putLong(createdOf(entry.dcId), entry.createdAtMs)
            .apply()
    }

    override fun load(dcId: Int): AuthKeyStore.Entry? {
        val encoded = prefs.getString(keyOf(dcId), null) ?: return null
        val raw = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            // Запись повреждена — лучше удалить и переустановить соединение,
            // чем падать при каждом запуске.
            remove(dcId)
            return null
        }
        if (raw.size != AuthKeyStore.AUTH_KEY_SIZE) {
            remove(dcId)
            return null
        }
        return AuthKeyStore.Entry(
            dcId = dcId,
            authKey = raw,
            serverSalt = prefs.getLong(saltOf(dcId), 0L),
            createdAtMs = prefs.getLong(createdOf(dcId), 0L)
        )
    }

    override fun updateSalt(dcId: Int, serverSalt: Long) {
        // Соль меняется часто (bad_server_salt), ключ при этом тот же.
        if (!prefs.contains(keyOf(dcId))) return
        prefs.edit().putLong(saltOf(dcId), serverSalt).apply()
    }

    override fun remove(dcId: Int) {
        prefs.edit()
            .remove(keyOf(dcId))
            .remove(saltOf(dcId))
            .remove(createdOf(dcId))
            .apply()
    }

    /** Полная очистка — при выходе из аккаунта. */
    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun storedDcIds(): Set<Int> =
        prefs.all.keys
            .filter { it.startsWith(PREFIX_KEY) }
            .mapNotNull { it.removePrefix(PREFIX_KEY).toIntOrNull() }
            .toSet()

    private fun keyOf(dcId: Int) = "$PREFIX_KEY$dcId"
    private fun saltOf(dcId: Int) = "$PREFIX_SALT$dcId"
    private fun createdOf(dcId: Int) = "$PREFIX_CREATED$dcId"

    companion object {
        private const val FILE_NAME = "mtproto_keys"
        private const val PREFIX_KEY = "auth_key_dc"
        private const val PREFIX_SALT = "server_salt_dc"
        private const val PREFIX_CREATED = "created_at_dc"
    }
}
