package com.fdzaki.promptopslogparser.scanner

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Result of extracting the first readable log/text entry from a ZIP stream.
 */
data class ExtractedLog(
    val entryName: String,
    val content: String,
    val lineCount: Int,
    val truncated: Boolean
)

/**
 * Decompresses a ZIP archive directly from an [InputStream] (no extraction to disk)
 * and returns the raw text content of the first entry matching .log/.txt.
 *
 * Defensive by design: caps read size to avoid OOM on malformed/huge archives,
 * and never throws — callers get null on any failure.
 */
object ZipLogExtractor {

    // Total budget stays 200 KB (same as before) so memory footprint and AI prompt cost
    // don't regress — but it's now split HEAD + TAIL instead of head-only. Root cause in
    // CI/build logs (GitHub Actions, Gradle, npm) almost always sits near the END of the
    // file (final "Caused by" / "BUILD FAILED" summary), so head-only truncation was
    // silently dropping exactly the lines the user needed to see.
    private const val HEAD_CHARS = 100_000
    private const val TAIL_CHARS = 100_000
    private const val SKIPPED_MARKER_PREFIX =
        "\n... [%d baris dilewati karena file besar — bagian akhir log di bawah ini " +
            "biasanya berisi error/exception utama] ...\n\n"
    private val LOG_EXTENSIONS = setOf("log", "txt")

    /**
     * @param zipStream raw input stream of the ZIP archive (e.g. FileInputStream, not pre-extracted)
     * @return the first matching log/text entry's content, or null if none found or on error
     */
    fun extractFirstLog(zipStream: InputStream): ExtractedLog? {
        return try {
            ZipInputStream(zipStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

                    if (!entry.isDirectory && ext in LOG_EXTENSIONS) {
                        return readEntryCapped(zis)?.let { (text, lines, truncated) ->
                            ExtractedLog(
                                entryName = name,
                                content = text,
                                lineCount = lines,
                                truncated = truncated
                            )
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                null
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            // Thrown by ZipInputStream on corrupted/malformed archives
            null
        }
    }

    /**
     * Reads the current ZIP entry's stream, keeping the first [HEAD_CHARS] and the last
     * [TAIL_CHARS] of text (total budget unchanged from before), so the analyzer always sees
     * both the start of the log AND the final failure/exception block, instead of losing the
     * tail entirely on large files.
     */
    private fun readEntryCapped(input: InputStream): Triple<String, Int, Boolean>? {
        return try {
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            val head = StringBuilder()
            val tailLines = ArrayDeque<String>()
            var headLineCount = 0
            var tailChars = 0
            var lineCount = 0
            var truncated = false
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                lineCount++
                val current = line!!
                if (head.length < HEAD_CHARS) {
                    head.append(current).append('\n')
                    headLineCount++
                } else {
                    truncated = true
                    tailLines.addLast(current)
                    tailChars += current.length + 1
                    // Keep the tail buffer bounded so we never hold more than TAIL_CHARS in
                    // memory, no matter how huge the file is (drop oldest tail lines first).
                    while (tailChars > TAIL_CHARS && tailLines.size > 1) {
                        tailChars -= (tailLines.removeFirst().length + 1)
                    }
                }
            }

            val content = if (truncated) {
                val skippedLineCount = (lineCount - headLineCount - tailLines.size).coerceAtLeast(0)
                buildString {
                    append(head)
                    append(SKIPPED_MARKER_PREFIX.format(skippedLineCount))
                    tailLines.forEach { append(it).append('\n') }
                }
            } else {
                head.toString()
            }

            Triple(content, lineCount, truncated)
        } catch (e: IOException) {
            null
        }
    }
}
