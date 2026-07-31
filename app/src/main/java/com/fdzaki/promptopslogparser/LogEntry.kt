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
    CUSTOM,
    NORMAL
}

object LogClassifier {

    private val errorRegex = Regex("(error|exception|fatal|crash)", RegexOption.IGNORE_CASE)
    private val warningRegex = Regex("(warn|warning|deprecated)", RegexOption.IGNORE_CASE)

    /**
     * @param customKeywords user-defined keywords (see [CustomKeywordStore]) that take
     *   priority over the built-in error/warning rules, so the user's own project-specific
     *   terms (e.g. a module name, a custom exception class) get their own distinct highlight.
     */
    fun classify(line: String, customKeywords: List<String> = emptyList()): LogLevel {
        if (customKeywords.isNotEmpty()) {
            for (keyword in customKeywords) {
                if (keyword.isNotBlank() && line.contains(keyword, ignoreCase = true)) {
                    return LogLevel.CUSTOM
                }
            }
        }
        return when {
            errorRegex.containsMatchIn(line) -> LogLevel.ERROR
            warningRegex.containsMatchIn(line) -> LogLevel.WARNING
            else -> LogLevel.NORMAL
        }
    }
}
