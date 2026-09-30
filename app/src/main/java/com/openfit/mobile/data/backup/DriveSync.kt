package com.openfit.mobile.data.backup

import android.util.Log
import com.openfit.mobile.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "OpenFitDriveSync"
private const val BACKUP_FILENAME = "openfit-settings.json"
private const val SCHEMA_VERSION = 1
private const val DRIVE = "https://www.googleapis.com/drive/v3"
private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"

/**
 * The envelope written to Drive's application data folder.
 *
 * Wraps the settings payload with a timestamp so newest-wins comparison works
 * across devices without needing server-side metadata. The schema version lets
 * a future format change refuse stale data rather than silently corrupt it.
 */
@Serializable
data class SettingsEnvelope(
    val schema: Int = SCHEMA_VERSION,
    val updatedAt: String = "",
    val device: String = "android",
    val settings: AppSettings = AppSettings(),
)

/**
 * Google Drive backup for all user settings.
 *
 * The file lives in Drive's application data folder — hidden, per-app, and the
 * only thing the `drive.appdata` scope grants access to. This app cannot read
 * your documents, and the folder doesn't appear in Drive's UI.
 *
 * Getting the access token is the caller's job (see DriveAuth). Everything
 * here is plain REST over HttpURLConnection — no additional dependencies.
 */
object DriveSync {

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    /** Download and deserialise the backup envelope, or null if none exists yet. */
    suspend fun download(token: String): SettingsEnvelope? = withContext(Dispatchers.IO) {
        try {
            val id = findFile(token) ?: return@withContext null
            val body = get("$DRIVE/files/$id?alt=media", token) ?: return@withContext null
            json.decodeFromString<SettingsEnvelope>(body)
        } catch (e: Exception) {
            Log.w(TAG, "download failed", e)
            null
        }
    }

    /** Serialise and upload the envelope, creating the file on first call. */
    suspend fun upload(token: String, envelope: SettingsEnvelope): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val payload = json.encodeToString(envelope)
                val id = findFile(token)
                if (id != null) {
                    send(
                        "$UPLOAD/files/$id?uploadType=media",
                        "PATCH", token, "application/json", payload,
                    )
                } else {
                    // First upload: multipart so we can put it in appDataFolder.
                    val boundary = "openfit-${System.nanoTime()}"
                    val metadata =
                        """{"name":"$BACKUP_FILENAME","parents":["appDataFolder"]}"""
                    val multipart = buildString {
                        append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
                        append(metadata).append("\r\n")
                        append("--$boundary\r\nContent-Type: application/json\r\n\r\n")
                        append(payload).append("\r\n")
                        append("--$boundary--")
                    }
                    send(
                        "$UPLOAD/files?uploadType=multipart",
                        "POST", token,
                        "multipart/related; boundary=$boundary", multipart,
                    )
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "upload failed", e)
                false
            }
        }

    /** The backup file's Drive id, or null if it doesn't exist yet. */
    private fun findFile(token: String): String? {
        val q = URLEncoder.encode("name = '$BACKUP_FILENAME' and trashed = false", "UTF-8")
        val body =
            get("$DRIVE/files?spaces=appDataFolder&q=$q&fields=files(id)&pageSize=1", token)
                ?: return null
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    }

    /** Parse an ISO-8601 instant to epoch-millis; returns 0 for blank / unparseable. */
    fun parseIso(s: String?): Long = try {
        if (s.isNullOrBlank()) 0L else java.time.Instant.parse(s).toEpochMilli()
    } catch (_: Exception) {
        0L
    }

    // ── HTTP helpers ───────────────────────────────────────────────────────

    private fun get(url: String, token: String): String? {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        return try {
            if (c.responseCode !in 200..299) {
                Log.w(TAG, "GET $url -> ${c.responseCode}")
                null
            } else {
                c.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            c.disconnect()
        }
    }

    private fun send(
        url: String,
        method: String,
        token: String,
        contentType: String,
        body: String,
    ) {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            // HttpURLConnection does not support PATCH; tunnel it the usual way.
            if (method == "PATCH") {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                requestMethod = method
            }
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", contentType)
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode !in 200..299) {
                throw IllegalStateException("$method $url -> ${c.responseCode}")
            }
        } finally {
            c.disconnect()
        }
    }
}
