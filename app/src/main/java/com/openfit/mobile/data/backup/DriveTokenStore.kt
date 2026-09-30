package com.openfit.mobile.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.driveTokenDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "openfit_drive_tokens")

/** Stores Drive OAuth tokens separately from the main settings and from
 * the Health API token store. Never included in the Drive backup itself. */
class DriveTokenStore(private val context: Context) {

    data class DriveTokens(
        val accessToken: String,
        val refreshToken: String?,
        val accountEmail: String?,
        val expiresAtEpochSeconds: Long,
    )

    suspend fun save(tokens: DriveTokens) {
        context.driveTokenDataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = tokens.accessToken
            prefs[KEY_REFRESH_TOKEN] = tokens.refreshToken ?: ""
            prefs[KEY_EMAIL] = tokens.accountEmail ?: ""
            prefs[KEY_EXPIRES_AT] = tokens.expiresAtEpochSeconds.toString()
        }
    }

    suspend fun load(): DriveTokens? {
        val prefs = context.driveTokenDataStore.data.firstOrNull() ?: return null
        val accessToken = prefs[KEY_ACCESS_TOKEN]?.takeIf { it.isNotBlank() } ?: return null
        return DriveTokens(
            accessToken = accessToken,
            refreshToken = prefs[KEY_REFRESH_TOKEN]?.takeIf { it.isNotBlank() },
            accountEmail = prefs[KEY_EMAIL]?.takeIf { it.isNotBlank() },
            expiresAtEpochSeconds = prefs[KEY_EXPIRES_AT]?.toLongOrNull() ?: 0L,
        )
    }

    suspend fun clear() {
        context.driveTokenDataStore.edit { it.clear() }
    }

    fun isExpired(tokens: DriveTokens): Boolean =
        tokens.expiresAtEpochSeconds < System.currentTimeMillis() / 1000 + 60

    private companion object {
        val KEY_ACCESS_TOKEN = stringPreferencesKey("drive_access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("drive_refresh_token")
        val KEY_EMAIL = stringPreferencesKey("drive_account_email")
        val KEY_EXPIRES_AT = stringPreferencesKey("drive_expires_at")
    }
}
