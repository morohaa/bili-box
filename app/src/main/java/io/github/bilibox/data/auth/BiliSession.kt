package io.github.bilibox.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal data class BiliSession(
    val username: String,
    val avatarUrl: String,
    val cookie: String,
    val level: Int = 0,
    val coins: Double = 0.0,
    val currentExp: Int = 0,
    val nextExp: Int = 0,
)

internal object BiliSessionStore {
    private const val alias = "bilibox_bili_session_key"
    private const val prefsName = "bili_session_secure"
    private const val dataKey = "encrypted_session"

    fun load(context: Context): BiliSession? = runCatching {
        val encoded = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).getString(dataKey, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        BiliSession(
            username = json.getString("username"),
            avatarUrl = json.optString("avatar"),
            cookie = json.getString("cookie"),
            level = json.optInt("level"),
            coins = json.optDouble("coins"),
            currentExp = json.optInt("current_exp"),
            nextExp = json.optInt("next_exp"),
        )
    }.getOrNull()

    fun save(context: Context, session: BiliSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(JSONObject().put("username", session.username)
            .put("avatar", session.avatarUrl).put("cookie", session.cookie)
            .put("level", session.level).put("coins", session.coins)
            .put("current_exp", session.currentExp).put("next_exp", session.nextExp)
            .toString().toByteArray(Charsets.UTF_8))
        val stored = cipher.iv + encrypted
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
            .putString(dataKey, Base64.encodeToString(stored, Base64.NO_WRAP)).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }
}
