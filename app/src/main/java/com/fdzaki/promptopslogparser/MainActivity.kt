package com.fdzaki.promptopslogparser

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import com.fdzaki.promptopslogparser.ai.AiLogAnalyzer
import com.fdzaki.promptopslogparser.ai.AnalysisHistoryStore
import com.fdzaki.promptopslogparser.ai.ApiKeyStore
import com.fdzaki.promptopslogparser.ai.LocalLogAnalyzer
import com.fdzaki.promptopslogparser.databinding.ActivityMainBinding
import com.fdzaki.promptopslogparser.diagnostics.AppDiagnostics
import com.fdzaki.promptopslogparser.scanner.ExtractedLog
import com.fdzaki.promptopslogparser.scanner.LogPromptBuilder
import com.fdzaki.promptopslogparser.scanner.ZipLogExtractor
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * PromptOps LogParser
 *
 * Aplikasi untuk membuka file log mentah (.txt/.log/.zip berisi log/logcat GitHub Actions),
 * merapikannya per baris ke dalam RecyclerView, memfilter berdasarkan kata kunci,
 * menyorot baris "Error"/"Exception", dan opsional mengirim log ke Claude (Anthropic API)
 * untuk dianalisis menjadi ringkasan terstruktur.
 *
 * Parsing lokal 100% offline. Fitur "Analisis dengan AI" adalah satu-satunya bagian
 * yang butuh koneksi internet, dan hanya berjalan saat pengguna menekannya secara eksplisit.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = LogAdapter()

    private var allEntries: List<LogEntry> = emptyList()
    private var showErrorsOnly = false
    private var currentFilterText = ""

    /** Holds the raw text + metadata of whatever was last loaded, used to build the AI prompt. */
    private var currentExtractedLog: ExtractedLog? = null

    /** User-defined highlight keywords (Batch 3), loaded from [CustomKeywordStore]. */
    private var customKeywords: List<String> = emptyList()

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvLogLines.layoutManager = LinearLayoutManager(this)
        binding.rvLogLines.adapter = adapter

        binding.btnOpenFile.setOnClickListener {
            openDocumentLauncher.launch(
                arrayOf("text/plain", "application/zip", "application/octet-stream", "*/*")
            )
        }

        binding.cbErrorsOnly.setOnCheckedChangeListener { _, isChecked ->
            showErrorsOnly = isChecked
            applyFilterAndRender()
        }

        binding.etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentFilterText = s?.toString().orEmpty()
                applyFilterAndRender()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnAnalyzeAi.setOnClickListener {
            onAnalyzeAiClicked()
        }

        binding.btnAnalyzeLocal.setOnClickListener {
            onAnalyzeLocalClicked()
        }

        binding.btnHelp.setOnClickListener {
            showTroubleshootingDialog()
        }

        binding.btnCustomKeywords.setOnClickListener {
            showCustomKeywordsDialog()
        }

        binding.btnHistory.setOnClickListener {
            showHistoryDialog()
        }

        customKeywords = CustomKeywordStore.getKeywords(this)

        renderEmptyState(true)
    }

    // ---------------------------------------------------------------------
    // File loading (.txt / .log / .zip)
    // ---------------------------------------------------------------------

    private fun loadFile(uri: Uri) {
        val fileName = DocumentFile.fromSingleUri(this, uri)?.name ?: ""
        val isZip = fileName.endsWith(".zip", ignoreCase = true) ||
            contentResolver.getType(uri) == "application/zip"

        if (isZip) {
            loadFromZip(uri, fileName)
        } else {
            loadPlainTextFile(uri, fileName)
        }
    }

    private fun loadPlainTextFile(uri: Uri, fileName: String) {
        try {
            val entries = mutableListOf<LogEntry>()
            val contentBuilder = StringBuilder()
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var lineNumber = 0
                    reader.forEachLine { rawLine ->
                        lineNumber++
                        val cleaned = rawLine.trimEnd()
                        entries.add(
                            LogEntry(
                                lineNumber = lineNumber,
                                rawText = cleaned,
                                level = LogClassifier.classify(cleaned, customKeywords)
                            )
                        )
                        if (contentBuilder.length < 200_000) {
                            contentBuilder.append(cleaned).append('\n')
                        }
                    }
                }
            }
            allEntries = entries
            currentExtractedLog = if (entries.isNotEmpty()) {
                ExtractedLog(
                    entryName = fileName.ifBlank { "log.txt" },
                    content = contentBuilder.toString(),
                    lineCount = entries.size,
                    truncated = contentBuilder.length >= 200_000
                )
            } else null

            applyFilterAndRender()
            updateAnalyzeButtonState()
            updateTruncationWarning()

            if (entries.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_file_empty), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            AppDiagnostics.logEvent(this, "Gagal baca file teks: ${e.message}")
            Toast.makeText(this, getString(R.string.toast_read_file_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun loadFromZip(uri: Uri, fileName: String) {
        try {
            val extracted = contentResolver.openInputStream(uri)?.use { input ->
                ZipLogExtractor.extractFirstLog(input)
            }

            if (extracted == null) {
                Toast.makeText(
                    this,
                    getString(R.string.toast_zip_no_log_found, fileName),
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val rawLines = extracted.content.split('\n').let { lines ->
                // ZipLogExtractor appends '\n' after every line, which leaves one
                // trailing empty element after split() — drop it if present.
                if (lines.isNotEmpty() && lines.last().isEmpty()) lines.dropLast(1) else lines
            }
            val entries = rawLines.mapIndexed { index, line ->
                LogEntry(
                    lineNumber = index + 1,
                    rawText = line,
                    level = LogClassifier.classify(line, customKeywords)
                )
            }

            allEntries = entries
            currentExtractedLog = extracted
            applyFilterAndRender()
            updateAnalyzeButtonState()
            updateTruncationWarning()

            Toast.makeText(
                this,
                getString(R.string.toast_zip_loaded, extracted.entryName),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            AppDiagnostics.logEvent(this, "Gagal baca ZIP: ${e.message}")
            Toast.makeText(this, getString(R.string.toast_read_zip_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------------
    // Filter / render
    // ---------------------------------------------------------------------

    private fun applyFilterAndRender() {
        if (allEntries.isEmpty()) {
            renderEmptyState(true)
            return
        }

        var filtered = allEntries

        if (showErrorsOnly) {
            filtered = filtered.filter { it.level == LogLevel.ERROR || it.level == LogLevel.CUSTOM }
        }

        if (currentFilterText.isNotBlank()) {
            val query = currentFilterText.trim()
            filtered = filtered.filter { it.rawText.contains(query, ignoreCase = true) }
        }

        adapter.submitList(filtered)
        binding.tvLineCount.text = getString(
            R.string.line_count_format,
            filtered.size,
            allEntries.size
        )
        renderEmptyState(false)
    }

    /** Re-runs classification on already-loaded entries after custom keywords change,
     *  without needing to re-open/re-read the source file. */
    private fun reclassifyCurrentEntries() {
        if (allEntries.isEmpty()) return
        allEntries = allEntries.map { it.copy(level = LogClassifier.classify(it.rawText, customKeywords)) }
        applyFilterAndRender()
    }

    private fun renderEmptyState(isEmpty: Boolean) {
        binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvLogLines.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (isEmpty) {
            binding.tvLineCount.text = ""
            binding.tvTruncationWarning.visibility = View.GONE
        }
    }

    /**
     * Peringatan persisten (bukan Toast sekilas) saat file log besar terpotong,
     * supaya pengguna tidak salah menyimpulkan dari data yang tidak lengkap.
     *
     * Dua kasus dibedakan secara jujur:
     * - ZIP besar: entri yang DITAMPILKAN di RecyclerView juga ikut terpotong
     *   (karena ZipLogExtractor membatasi ukuran baca per-karakter).
     * - File teks biasa: semua baris tetap tampil penuh, hanya isi yang dikirim
     *   ke Analisis AI yang dipotong.
     */
    private fun updateTruncationWarning() {
        val extracted = currentExtractedLog
        if (extracted == null || !extracted.truncated) {
            binding.tvTruncationWarning.visibility = View.GONE
            return
        }
        val shown = allEntries.size
        val total = extracted.lineCount
        binding.tvTruncationWarning.text = if (shown < total) {
            getString(R.string.truncation_warning_display, shown, total)
        } else {
            getString(R.string.truncation_warning_ai_only, total)
        }
        binding.tvTruncationWarning.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------------
    // AI analysis (real network call to the Anthropic Messages API)
    // ---------------------------------------------------------------------

    private fun updateAnalyzeButtonState() {
        val hasLog = currentExtractedLog != null
        binding.btnAnalyzeAi.isEnabled = hasLog
        binding.btnAnalyzeLocal.isEnabled = hasLog
    }

    private fun onAnalyzeLocalClicked() {
        if (allEntries.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_open_file_first), Toast.LENGTH_SHORT).show()
            return
        }
        val sourceName = currentExtractedLog?.entryName ?: "unknown"
        val jsonResult = LocalLogAnalyzer.analyze(allEntries, sourceName)
        recordAnalysisHistory(sourceName, "Offline (Gratis)", jsonResult)
        showResultDialog(getString(R.string.local_result_title), jsonResult, showChangeKeyOption = false)
    }

    private fun onAnalyzeAiClicked() {
        val extracted = currentExtractedLog ?: run {
            Toast.makeText(this, getString(R.string.toast_open_file_first), Toast.LENGTH_SHORT).show()
            return
        }

        val savedKey = ApiKeyStore.getApiKey(this)
        if (savedKey.isNullOrBlank()) {
            showApiKeyDialog { newKey ->
                ApiKeyStore.saveApiKey(this, newKey)
                runAnalysis(extracted, newKey)
            }
        } else {
            runAnalysis(extracted, savedKey)
        }
    }

    private fun runAnalysis(extracted: ExtractedLog, apiKey: String) {
        setAnalyzingUi(true)
        val prompt = LogPromptBuilder.build(extracted)

        AiLogAnalyzer.analyze(prompt, apiKey) { result ->
            setAnalyzingUi(false)
            when (result) {
                is AiLogAnalyzer.AnalyzeResult.Success -> {
                    recordAnalysisHistory(extracted.entryName, "AI (Cloud)", result.jsonText)
                    showResultDialog(getString(R.string.ai_result_title), result.jsonText, showChangeKeyOption = true)
                }
                is AiLogAnalyzer.AnalyzeResult.Error -> {
                    AppDiagnostics.logEvent(this, "Analisis AI gagal: ${result.message}")
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    // If it looks like an auth problem, let the user re-enter the key.
                    if (result.message.contains("401") || result.message.contains("authentication", true)) {
                        ApiKeyStore.clearApiKey(this)
                    }
                }
            }
        }
    }

    /** Parses the shared JSON schema (execution_status / summary_metrics.error_count)
     *  from either analysis engine and appends it to [AnalysisHistoryStore]. */
    private fun recordAnalysisHistory(sourceName: String, engine: String, jsonText: String) {
        try {
            val json = JSONObject(jsonText)
            val extracted = json.getJSONObject("extracted_data")
            val status = extracted.optString("execution_status", "UNKNOWN")
            val errorCount = extracted.optJSONObject("summary_metrics")?.optInt("error_count", 0) ?: 0
            AnalysisHistoryStore.add(this, sourceName, engine, status, errorCount)
        } catch (e: Exception) {
            // If the JSON shape is unexpected (e.g. AI didn't follow schema), just skip history
            // rather than breaking the result dialog the user is about to see.
        }
    }

    private fun setAnalyzingUi(analyzing: Boolean) {
        binding.progressAi.visibility = if (analyzing) View.VISIBLE else View.GONE
        val hasLog = currentExtractedLog != null
        binding.btnAnalyzeAi.isEnabled = !analyzing && hasLog
        binding.btnAnalyzeLocal.isEnabled = !analyzing && hasLog
        binding.btnAnalyzeAi.text = getString(
            if (analyzing) R.string.analyzing_ai else R.string.analyze_ai
        )
    }

    private fun showApiKeyDialog(onSaved: (String) -> Unit) {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val messageView = TextView(this).apply {
            text = getString(R.string.api_key_dialog_message)
            setPadding(0, 0, 0, padding)
        }
        val input = EditText(this).apply {
            hint = getString(R.string.api_key_hint)
            isSingleLine = true
        }
        container.addView(messageView)
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle(R.string.api_key_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val key = input.text.toString().trim()
                dialog.dismiss()
                if (key.isNotBlank()) {
                    onSaved(key)
                } else {
                    Toast.makeText(this, getString(R.string.toast_api_key_empty), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showResultDialog(title: String, jsonText: String, showChangeKeyOption: Boolean) {
        val pretty = try {
            JSONObject(jsonText).toString(2)
        } catch (e: Exception) {
            jsonText // fall back to raw text if the model didn't return clean JSON
        }

        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            text = pretty
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(R.string.close) { dialog, _ -> dialog.dismiss() }
            .setNegativeButton(R.string.copy_to_clipboard) { dialog, _ ->
                copyToClipboard(title, pretty)
                dialog.dismiss()
            }

        if (showChangeKeyOption) {
            builder.setNeutralButton(R.string.change_api_key) { dialog, _ ->
                dialog.dismiss()
                ApiKeyStore.clearApiKey(this)
                Toast.makeText(this, getString(R.string.toast_api_key_cleared), Toast.LENGTH_LONG).show()
            }
        }
        builder.show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------------
    // Troubleshooting / Bantuan
    // ---------------------------------------------------------------------

    private fun showTroubleshootingDialog() {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding)
        }

        // -- Dynamic section: last crash (if any) --------------------------------
        val lastCrash = AppDiagnostics.getLastCrash(this)
        if (lastCrash != null) {
            addSectionHeader(container, getString(R.string.crash_detected_title), padding)
            addSectionBody(container, lastCrash.take(600))
            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            btnRow.addView(Button(this).apply {
                text = getString(R.string.copy_to_clipboard)
                setOnClickListener { copyToClipboard(getString(R.string.crash_detected_title), lastCrash) }
            })
            btnRow.addView(Button(this).apply {
                text = getString(R.string.clear)
                setOnClickListener {
                    AppDiagnostics.clearLastCrash(this@MainActivity)
                    Toast.makeText(this@MainActivity, getString(R.string.no_crash_detected), Toast.LENGTH_SHORT).show()
                }
            })
            container.addView(btnRow)
        }

        // -- Dynamic section: recent non-fatal events -----------------------------
        val recentEvents = AppDiagnostics.getRecentEvents(this)
        if (recentEvents.isNotEmpty()) {
            addSectionHeader(container, getString(R.string.recent_events_title), padding)
            addSectionBody(container, recentEvents.take(10).joinToString("\n"))
            container.addView(Button(this).apply {
                text = getString(R.string.clear)
                setOnClickListener {
                    AppDiagnostics.clearRecentEvents(this@MainActivity)
                    Toast.makeText(this@MainActivity, getString(R.string.events_cleared_toast), Toast.LENGTH_SHORT).show()
                }
            })
        }

        // -- Static FAQ ------------------------------------------------------------
        val faq = listOf(
            "File tidak mau terbuka / \"Gagal membaca file\"" to
                "Pastikan file berformat .txt, .log, atau .zip. Jika dari GitHub Actions, unduh " +
                "\"Download log archive\" (biasanya .zip) — aplikasi akan otomatis mencari file " +
                ".log/.txt pertama di dalamnya.",
            "ZIP dibuka tapi bilang \"Tidak ada file .log/.txt di dalam ZIP\"" to
                "Buka ZIP tersebut secara manual sekali untuk memastikan isinya benar log teks, " +
                "bukan hanya folder/binary. Saat ini aplikasi hanya membaca entri pertama yang " +
                "berekstensi .log atau .txt.",
            "Analisis Offline (Gratis) hasilnya kurang detail" to
                "Mode ini memakai aturan pencocokan pola (regex), bukan pemahaman bahasa natural. " +
                "Gunakan Analisis AI (Cloud) untuk ringkasan yang lebih kontekstual, dengan " +
                "konsekuensi biaya token API.",
            "Analisis AI gagal / muncul HTTP 401" to
                "Berarti API key salah atau kedaluwarsa. Aplikasi otomatis menghapus key yang " +
                "tersimpan — tekan tombol Analisis AI lagi untuk memasukkan key baru dari " +
                "console.anthropic.com.",
            "Analisis AI gagal \"Koneksi gagal\"" to
                "Periksa koneksi internet HP. Fitur ini satu-satunya bagian aplikasi yang butuh " +
                "jaringan; semua fitur lain (buka file, filter, highlight, Analisis Offline) " +
                "berjalan 100% tanpa internet.",
            "Build APK gagal di GitHub Actions" to
                "Cek tab Actions di repo untuk log detail. Penyebab umum: 4 GitHub Secrets belum " +
                "diset (jalankan secrets.txt lewat Command B), atau file release.keystore tidak " +
                "ikut ter-decode dengan benar.",
            "git push ditolak (\"rejected\")" to
                "Jalankan git pull --no-rebase origin main --no-edit lalu git push lagi. " +
                "Jangan pernah pakai --force kecuali Anda yakin ingin menimpa riwayat remote."
        )

        faq.forEach { (question, answer) ->
            val q = TextView(this).apply {
                text = question
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.accent, theme))
                setPadding(0, padding, 0, padding / 4)
            }
            val a = TextView(this).apply {
                text = answer
                setTextColor(resources.getColor(R.color.text_primary, theme))
                textSize = 13f
            }
            container.addView(q)
            container.addView(a)
        }

        val scrollView = ScrollView(this).apply { addView(container) }

        AlertDialog.Builder(this)
            .setTitle(R.string.help)
            .setView(scrollView)
            .setPositiveButton(R.string.close) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun addSectionHeader(container: LinearLayout, title: String, padding: Int) {
        container.addView(TextView(this).apply {
            text = title
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.warning_yellow, theme))
            textSize = 15f
            setPadding(0, padding, 0, padding / 4)
        })
    }

    private fun addSectionBody(container: LinearLayout, body: String) {
        container.addView(TextView(this).apply {
            text = body
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            textSize = 11f
            setTextIsSelectable(true)
        })
    }

    // ---------------------------------------------------------------------
    // Custom Keywords (Batch 3)
    // ---------------------------------------------------------------------

    private fun showCustomKeywordsDialog() {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val messageView = TextView(this).apply {
            text = getString(R.string.custom_keywords_dialog_message)
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            textSize = 13f
            setPadding(0, 0, 0, padding)
        }
        val input = EditText(this).apply {
            hint = getString(R.string.custom_keywords_hint)
            setText(customKeywords.joinToString(", "))
        }
        container.addView(messageView)
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle(R.string.custom_keywords_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val keywords = input.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                CustomKeywordStore.saveKeywords(this, keywords)
                customKeywords = keywords
                reclassifyCurrentEntries()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // ---------------------------------------------------------------------
    // Analysis History (Batch 3)
    // ---------------------------------------------------------------------

    private fun showHistoryDialog() {
        val entries = AnalysisHistoryStore.getAll(this)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding)
        }

        if (entries.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.history_empty)
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                textSize = 13f
            })
        } else {
            entries.forEach { entry ->
                val statusColor = when (entry.status) {
                    "FAILED" -> R.color.error_red
                    "WARNING" -> R.color.warning_yellow
                    "SUCCESS" -> R.color.accent
                    else -> R.color.text_secondary
                }
                container.addView(TextView(this).apply {
                    text = "${entry.timestamp} — ${entry.sourceName}"
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(resources.getColor(R.color.text_primary, theme))
                    textSize = 13f
                    setPadding(0, padding / 2, 0, 0)
                })
                container.addView(TextView(this).apply {
                    text = "${entry.engine} · ${entry.status} · ${entry.errorCount} error"
                    setTextColor(resources.getColor(statusColor, theme))
                    textSize = 12f
                })
            }
        }

        val scrollView = ScrollView(this).apply { addView(container) }

        AlertDialog.Builder(this)
            .setTitle(R.string.history_dialog_title)
            .setView(scrollView)
            .setPositiveButton(R.string.close) { dialog, _ -> dialog.dismiss() }
            .setNegativeButton(R.string.clear_history) { dialog, _ ->
                AnalysisHistoryStore.clear(this)
                Toast.makeText(this, getString(R.string.history_cleared_toast), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }
}
