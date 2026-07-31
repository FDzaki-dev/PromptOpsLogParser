package com.fdzaki.promptopslogparser.ai

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AnalysisHistoryEntry(
    val timestamp: String,
    val sourceName: String,
    val engine: String, // "AI (Cloud)" or "Offline (Gratis)"
    val status: String,
    val errorCount: Int
)

/**
 * Local-only history of past analysis runs (both Offline and AI), so the user can compare
 * results across CI runs without re-analyzing every time. Stored as JSON-lines under the
 * app's private storage — never uploaded anywhere.
 */
object AnalysisHistoryStore {

    private const val DIR_NAME = "analysis_history"
    private const val FILE_NAME = "history.jsonl"
    private const val MAX_ENTRIES = 20

    private fun file(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return File(d, FILE_NAME)
    }

    fun add(context: Context, sourceName: String, engine: String, status: String, errorCount: Int) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val json = JSONObject().apply {
                put("timestamp", timestamp)
                put("sourceName", sourceName)
                put("engine", engine)
                put("status", status)
                put("errorCount", errorCount)
            }
            val f = file(context)
            val lines = (if (f.exists()) f.readLines() else emptyList()) + json.toString()
            f.writeText(lines.takeLast(MAX_ENTRIES).joinToString("\n"))
        } catch (e: Exception) {
            // History is a convenience feature; never let it break the analysis flow.
        }
    }

    fun getAll(context: Context): List<AnalysisHistoryEntry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            try {
                val o = JSONObject(line)
                AnalysisHistoryEntry(
                    timestamp = o.getString("timestamp"),
                    sourceName = o.getString("sourceName"),
                    engine = o.getString("engine"),
                    status = o.getString("status"),
                    errorCount = o.getInt("errorCount")
                )
            } catch (e: Exception) {
                null
            }
        }.asReversed()
    }

    fun clear(context: Context) {
        file(context).delete()
    }
}
