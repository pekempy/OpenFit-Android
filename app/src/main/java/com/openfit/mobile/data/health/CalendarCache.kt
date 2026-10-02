package com.openfit.mobile.data.health

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Persists calendar day indicators (coloured dots) to disk so the History
 * tab shows data immediately on cold start without hitting Health Connect.
 * Only stores a boolean triple per day — not the full DailySnapshot —
 * keeping the file tiny (< 10 KB for a full year). */
object CalendarCache {
    private const val FILE = "calendar_indicators.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class DayEntry(
        val hasSteps: Boolean = false,
        val hasSleep: Boolean = false,
        val hasHR: Boolean = false,
    )

    @Serializable
    private data class CacheFile(
        /** yyyy-MM-dd → indicator flags */
        val days: Map<String, DayEntry> = emptyMap(),
        /** yyyy-MM strings that are fully loaded and don't need re-fetching */
        val fullMonths: List<String> = emptyList(),
        val savedAtMs: Long = 0L,
    )

    data class Snapshot(
        val days: Map<String, DayEntry>,
        val fullMonths: Set<String>,
        val savedAtMs: Long,
    )

    fun save(
        context: Context,
        days: Map<String, DayEntry>,
        fullMonths: Collection<String>,
    ) = runCatching {
        val cf = CacheFile(days, fullMonths.toList(), System.currentTimeMillis())
        File(context.filesDir, FILE).writeText(json.encodeToString(cf))
    }

    fun load(context: Context): Snapshot? = runCatching {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        val cf = json.decodeFromString<CacheFile>(f.readText())
        Snapshot(cf.days, cf.fullMonths.toSet(), cf.savedAtMs)
    }.getOrNull()
}
