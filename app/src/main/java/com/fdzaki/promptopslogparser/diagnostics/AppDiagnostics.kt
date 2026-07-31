package com.fdzaki.promptopslogparser.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Self-diagnostics storage. This is what lets PromptOpsLogParser "read its own logs" —
 * crashes and non-fatal errors are written to plain text files under the app's private
 * storage, and surfaced back to the user inside the in-app Bantuan/Troubleshooting dialog.
 */
object AppDiagnostics {

    private const val DIR_NAME = "diagnostics"
    private const val CRASH_FILE = "last_crash.txt"
    private const val EVENTS_FILE = "recent_events.log"
    private const val MAX_EVENT_LINES = 30

    private fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    // -- Crash (fatal, uncaught) -------------------------------------------------

    fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val content = "Waktu: ${timestamp()}\nThread: ${thread.name}\n\n$sw"
        File(dir(context), CRASH_FILE).writeText(content)
    }

    fun getLastCrash(context: Context): String? {
        val f = File(dir(context), CRASH_FILE)
        return if (f.exists()) f.readText() else null
    }

    fun clearLastCrash(context: Context) {
        File(dir(context), CRASH_FILE).delete()
    }

    // -- Non-fatal runtime events (caught exceptions, API errors, etc.) ---------

    fun logEvent(context: Context, message: String) {
        try {
            val f = File(dir(context), EVENTS_FILE)
            val line = "[${timestamp()}] $message"
            val existing = if (f.exists()) f.readLines() else emptyList()
            val updated = (existing + line).takeLast(MAX_EVENT_LINES)
            f.writeText(updated.joinToString("\n"))
        } catch (e: Exception) {
            // Diagnostics logging must never itself crash the app.
        }
    }

    fun getRecentEvents(context: Context): List<String> {
        val f = File(dir(context), EVENTS_FILE)
        return if (f.exists()) f.readLines().asReversed() else emptyList()
    }

    fun clearRecentEvents(context: Context) {
        File(dir(context), EVENTS_FILE).delete()
    }
}
