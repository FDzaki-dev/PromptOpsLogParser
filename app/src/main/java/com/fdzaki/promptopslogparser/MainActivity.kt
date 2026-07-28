package com.fdzaki.promptopslogparser

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.fdzaki.promptopslogparser.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * PromptOps LogParser
 *
 * Aplikasi offline untuk membuka file log mentah (.txt/.log/logcat GitHub Actions),
 * merapikannya per baris ke dalam RecyclerView, memfilter berdasarkan kata kunci,
 * dan menyorot baris yang mengandung "Error"/"Exception" dengan warna merah.
 *
 * Semua pemrosesan dilakukan sepenuhnya di perangkat (tidak ada koneksi jaringan).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = LogAdapter()

    private var allEntries: List<LogEntry> = emptyList()
    private var showErrorsOnly = false
    private var currentFilterText = ""

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadLogFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvLogLines.layoutManager = LinearLayoutManager(this)
        binding.rvLogLines.adapter = adapter

        binding.btnOpenFile.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
        }

        binding.cbErrorsOnly.setOnCheckedChangeListener { _, isChecked ->
            showErrorsOnly = isChecked
            applyFilterAndRender()
        }

        binding.etFilter.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentFilterText = s?.toString().orEmpty()
                applyFilterAndRender()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        renderEmptyState(true)
    }

    private fun loadLogFile(uri: Uri) {
        try {
            val entries = mutableListOf<LogEntry>()
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var lineNumber = 0
                    reader.forEachLine { rawLine ->
                        lineNumber++
                        // Rapikan: buang whitespace berlebih di akhir baris
                        val cleaned = rawLine.trimEnd()
                        entries.add(
                            LogEntry(
                                lineNumber = lineNumber,
                                rawText = cleaned,
                                level = LogClassifier.classify(cleaned)
                            )
                        )
                    }
                }
            }
            allEntries = entries
            applyFilterAndRender()
            if (entries.isEmpty()) {
                Toast.makeText(this, "File kosong atau tidak dapat dibaca.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyFilterAndRender() {
        if (allEntries.isEmpty()) {
            renderEmptyState(true)
            return
        }

        var filtered = allEntries

        if (showErrorsOnly) {
            filtered = filtered.filter { it.level == LogLevel.ERROR }
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

    private fun renderEmptyState(isEmpty: Boolean) {
        binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvLogLines.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (isEmpty) {
            binding.tvLineCount.text = ""
        }
    }
}
