package com.fdzaki.promptopslogparser.ai

import com.fdzaki.promptopslogparser.LogEntry
import com.fdzaki.promptopslogparser.LogLevel
import org.json.JSONArray
import org.json.JSONObject

object LocalLogAnalyzer {

    private val timestampRegex = Regex(
        """\b(\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}|\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\b"""
    )
    private const val MAX_CRITICAL_EVENTS = 25
    private const val MAX_MESSAGE_LENGTH = 200

    fun analyze(entries: List<LogEntry>, sourceName: String): String {
        val logType = detectLogType(entries)
        val status = detectExecutionStatus(entries)
        val timestamps = extractTimestampRange(entries)
        val environment = detectEnvironment(entries)
        val errorEntries = entries.filter { it.level == LogLevel.ERROR }
        val errorCount = errorEntries.size

        val result = JSONObject().apply {
            put("app_name", "PromptOpsLogParser")
            put("log_intelligence", JSONObject().apply {
                put("detected_log_type", logType)
                put("log_format", "plain-text")
            })
            put("extracted_data", JSONObject().apply {
                put("timestamp_range", JSONObject().apply {
                    put("start", timestamps?.first ?: JSONObject.NULL)
                    put("end", timestamps?.second ?: JSONObject.NULL)
                })
                put("environment_or_source", environment ?: sourceName)
                put("execution_status", status)
                put("critical_events", JSONArray().apply {
                    errorEntries.take(MAX_CRITICAL_EVENTS).forEach { entry ->
                        put(JSONObject().apply {
                            put("line_number", entry.lineNumber)
                            put("level", "ERROR")
                            put("message", entry.rawText.take(MAX_MESSAGE_LENGTH))
                        })
                    }
                })
                put("summary_metrics", JSONObject().apply {
                    put("total_lines_analyzed", entries.size)
                    put("error_count", errorCount)
                })
            })
            put("analysis_engine", "local-offline-v1")
        }
        return result.toString(2)
    }

    private fun detectLogType(entries: List<LogEntry>): String {
        val sample = entries.take(500).joinToString("\n") { it.rawText }
        return when {
            sample.contains("##[error]") || sample.contains("##[group]") ||
                sample.contains("Run actions/") ->
                "GitHub Actions Workflow Log"
            sample.contains("FATAL EXCEPTION") || sample.contains("AndroidRuntime") ->
                "Android Logcat"
            sample.contains("BUILD SUCCESSFUL") || sample.contains("BUILD FAILED") ||
                Regex("""> Task :\S+""").containsMatchIn(sample) ->
                "Gradle Build Log"
            sample.contains("npm ERR!") || sample.contains("npm WARN") ->
                "NPM Log"
            else -> "Generic Text Log"
        }
    }

    private fun detectExecutionStatus(entries: List<LogEntry>): String {
        val sample = entries.take(2000).joinToString("\n") { it.rawText }
        val hasErrors = entries.any { it.level == LogLevel.ERROR }
        val hasWarnings = entries.any { it.level == LogLevel.WARNING }
        return when {
            sample.contains("BUILD FAILED") || sample.contains("FATAL EXCEPTION") -> "FAILED"
            sample.contains("BUILD SUCCESSFUL") && !hasErrors -> "SUCCESS"
            hasErrors -> "FAILED"
            hasWarnings -> "WARNING"
            entries.isEmpty() -> "UNKNOWN"
            else -> "SUCCESS"
        }
    }

    private fun extractTimestampRange(entries: List<LogEntry>): Pair<String, String>? {
        val matches = entries.mapNotNull { timestampRegex.find(it.rawText)?.value }
        if (matches.isEmpty()) return null
        return matches.first() to matches.last()
    }

    private fun detectEnvironment(entries: List<LogEntry>): String? {
        val envLine = entries.firstOrNull {
            it.rawText.contains("Runner Image", ignoreCase = true) ||
                it.rawText.contains("Device:", ignoreCase = true) ||
                it.rawText.contains("Build:", ignoreCase = true)
        }
        return envLine?.rawText?.take(120)
    }
}
