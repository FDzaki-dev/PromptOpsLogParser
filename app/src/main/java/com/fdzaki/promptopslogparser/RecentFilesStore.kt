package com.fdzaki.promptopslogparser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecentFileEntry(
    val uri: String,
    val name: String,
    val timestamp: String
)

/**
 * Local-only list of recently opened log files (Batch 2), so the user can quickly reopen a
 * file without digging through the system file picker again. Stores the content URI string
 * (persisted read permission is taken separately in MainActivity via
 * takePersistableUriPermission) plus display name and timestamp. Purely local — SharedPreferences,
 * no network involved, same storage pattern as [CustomKeywordStore].
 */
object RecentFilesStore {

    private const val PREFS_NAME = "promptops_recent_files"
    private const val KEY_ENTRIES_JSON = "entries_json"
    private const val MAX_ENTRIES = 10

    fun getRecent(context: Context): List<RecentFileEntry> {
        val raw = prefs(context).getString(KEY_ENTRIES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RecentFileEntry(
                    uri = o.getString("uri"),
                    name = o.getString("name"),
                    timestamp = o.getString("timestamp")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Adds/moves [uri] to the top of the recent list, trimmed to [MAX_ENTRIES]. */
    fun addRecent(context: Context, uri: String, name: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val withoutDuplicate = getRecent(context).filterNot { it.uri == uri }
        val updated = (listOf(RecentFileEntry(uri, name, timestamp)) + withoutDuplicate)
            .take(MAX_ENTRIES)
        save(context, updated)
    }

    /** Removes a single entry, e.g. when its URI permission is no longer valid. */
    fun remove(context: Context, uri: String) {
        save(context, getRecent(context).filterNot { it.uri == uri })
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ENTRIES_JSON).apply()
    }

    private fun save(context: Context, entries: List<RecentFileEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("uri", e.uri)
                    put("name", e.name)
                    put("timestamp", e.timestamp)
                }
            )
        }
        prefs(context).edit().putString(KEY_ENTRIES_JSON, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
