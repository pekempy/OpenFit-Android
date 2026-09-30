package com.openfit.mobile.data.oauth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted on-device storage for the Google OAuth token set. Uses
 * Android Keystore-backed encryption (androidx.security.crypto) rather than
 * plain SharedPreferences - tokens never leave this device and are excluded
 * from cloud backup (see data_extraction_rules.xml). */
class TokenStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "openfit_oauth_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(tokens: StoredTokens) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putLong(KEY_EXPIRES_AT, tokens.expiresAtEpochMillis)
            .putString(KEY_ID_TOKEN, tokens.idToken)
            .putString(KEY_ACCOUNT_EMAIL, tokens.accountEmail)
            .apply()
    }

    fun load(): StoredTokens? {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        return StoredTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMillis = expiresAt,
            idToken = prefs.getString(KEY_ID_TOKEN, null),
            accountEmail = prefs.getString(KEY_ACCOUNT_EMAIL, null),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_ID_TOKEN = "id_token"
        const val KEY_ACCOUNT_EMAIL = "account_email"
    }
}

data class StoredTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMillis: Long,
    val idToken: String?,
    val accountEmail: String?,
) {
    fun isExpired(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        nowEpochMillis >= expiresAtEpochMillis - EXPIRY_SKEW_MILLIS

    private companion object {
        const val EXPIRY_SKEW_MILLIS = 60_000L
    }
}
