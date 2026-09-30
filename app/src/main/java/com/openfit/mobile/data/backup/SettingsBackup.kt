package com.openfit.mobile.data.backup

import com.openfit.mobile.data.settings.AppSettings
import com.openfit.mobile.data.settings.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Serialises and deserialises all user-configurable settings to/from a
 * JSON string.  The caller decides where to write or read the string —
 * typically via the Android Storage Access Framework file picker so the
 * user can save to Google Drive, Downloads, or anywhere else without any
 * OAuth setup on our part. */
object SettingsBackup {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    suspend fun export(repository: SettingsRepository): String {
        val settings = repository.settingsFlow.firstOrNull() ?: AppSettings()
        return json.encodeToString(settings)
    }

    suspend fun import(jsonStr: String, repository: SettingsRepository): Result<Unit> = runCatching {
        val restored = json.decodeFromString<AppSettings>(jsonStr)
        repository.applyFullSettings(restored)
    }
}
