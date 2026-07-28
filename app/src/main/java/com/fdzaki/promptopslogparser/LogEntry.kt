package com.fdzaki.promptopslogparser

/**
 * Represents a single parsed line from a raw log file.
 */
data class LogEntry(
    val lineNumber: Int,
    val rawText: String,
    val level: LogLevel
)

enum class LogLevel {
    ERROR,
    WARNING,
    NORMAL
}

object LogClassifier {

    private val errorRegex = Regex("(error|exception|fatal|crash)", RegexOption.IGNORE_CASE)
    private val warningRegex = Regex("(warn|warning|deprecated)", RegexOption.IGNORE_CASE)

    fun classify(line: String): LogLevel {
        return when {
            errorRegex.containsMatchIn(line) -> LogLevel.ERROR
            warningRegex.containsMatchIn(line) -> LogLevel.WARNING
            else -> LogLevel.NORMAL
        }
    }
}
