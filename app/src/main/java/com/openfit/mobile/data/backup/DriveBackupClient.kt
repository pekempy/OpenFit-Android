package com.openfit.mobile.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
private const val DRIVE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
private const val APP_DATA_SPACE = "appDataFolder"
private const val BACKUP_FILENAME = "openfit_settings.json"
private const val BOUNDARY = "openfit_backup_boundary"

/** Thin wrapper around the Drive v3 REST API targeting the App Data folder.
 * Requests authorisation via a Bearer token from [DriveAuthManager]. */
class DriveBackupClient {

    /** Upload (create or overwrite) the settings backup. */
    suspend fun upload(token: String, json: String): String = withContext(Dispatchers.IO) {
        val existingId = findBackupFileId(token)
        if (existingId != null) {
            updateFile(token, existingId, json)
            existingId
        } else {
            createFile(token, json)
        }
    }

    /** Download and return the settings backup JSON, or null if none exists. */
    suspend fun download(token: String): String? = withContext(Dispatchers.IO) {
        val fileId = findBackupFileId(token) ?: return@withContext null
        val conn = URL("$DRIVE_API/files/$fileId?alt=media").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connect()
        if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else null
    }

    /** ISO timestamp of the last backup, or null. */
    suspend fun lastModifiedTime(token: String): String? = withContext(Dispatchers.IO) {
        val fileId = findBackupFileId(token) ?: return@withContext null
        val conn = URL("$DRIVE_API/files/$fileId?fields=modifiedTime").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connect()
        if (conn.responseCode != 200) return@withContext null
        val body = conn.inputStream.bufferedReader().readText()
        JSONObject(body).optString("modifiedTime").takeIf { it.isNotBlank() }
    }

    private fun findBackupFileId(token: String): String? {
        val query = "name='$BACKUP_FILENAME' and trashed=false"
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = URL("$DRIVE_API/files?spaces=$APP_DATA_SPACE&q=$encoded&fields=files(id)")
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            connect()
            if (responseCode != 200) return null
            val body = inputStream.bufferedReader().readText()
            return JSONObject(body).optJSONArray("files")?.optJSONObject(0)?.optString("id")
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun createFile(token: String, json: String): String {
        val metadata = """{"name":"$BACKUP_FILENAME","parents":["$APP_DATA_SPACE"]}"""
        val body = buildMultipart(metadata, json)
        val url = URL("$DRIVE_UPLOAD_API/files?uploadType=multipart&fields=id")
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$BOUNDARY")
            outputStream.write(body.toByteArray(Charsets.UTF_8))
            connect()
            val responseBody = if (responseCode in 200..299) inputStream.bufferedReader().readText()
            else throw DriveBackupException("Create failed: $responseCode ${errorStream?.bufferedReader()?.readText()}")
            return JSONObject(responseBody).getString("id")
        }
    }

    private fun updateFile(token: String, fileId: String, json: String) {
        val url = URL("$DRIVE_UPLOAD_API/files/$fileId?uploadType=media")
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            outputStream.write(json.toByteArray(Charsets.UTF_8))
            connect()
            if (responseCode !in 200..299) {
                throw DriveBackupException("Update failed: $responseCode ${errorStream?.bufferedReader()?.readText()}")
            }
        }
    }

    private fun buildMultipart(metadata: String, content: String): String = buildString {
        append("--$BOUNDARY\r\n")
        append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        append(metadata)
        append("\r\n--$BOUNDARY\r\n")
        append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        append(content)
        append("\r\n--$BOUNDARY--")
    }
}

class DriveBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
