package com.openfit.mobile.data.health

import android.content.Context
import com.openfit.mobile.model.HealthSnapshotBundle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Persists the last successfully-synced [HealthSnapshotBundle] to a JSON
 * file in the app's internal storage so the UI can display stale-but-recent
 * data immediately on open while a fresh sync runs in the background.
 * The background summary worker saves here after each successful sync, so
 * opening the app after a notification gives instant data rather than a spinner. */
object BundleCache {
    private const val FILE_NAME = "health_bundle_cache.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(context: Context, bundle: HealthSnapshotBundle) {
        runCatching {
            File(context.filesDir, FILE_NAME).writeText(json.encodeToString(bundle))
        }
    }

    /** Returns the cached bundle only if its [HealthSnapshotBundle.selectedDate] matches
     * [forDate] (today by default), so stale data from previous days isn't shown. */
    fun load(context: Context, forDate: String): HealthSnapshotBundle? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val bundle = json.decodeFromString<HealthSnapshotBundle>(file.readText())
        if (bundle.selectedDate == forDate) bundle else null
    }.getOrNull()
}
