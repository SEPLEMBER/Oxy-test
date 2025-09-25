package org.syndes.rust

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * MainActivity: central editor UI + SAF launchers + find/replace within current file.
 * Buttons/menu items for batch activities are wired as intents (placeholders).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var editorView: EditText
    private lateinit var statusView: TextView
    private lateinit var progressOverlay: FrameLayout
    private lateinit var progressBar: ProgressBar

    // Current opened file uri (if any)
    private var currentUri: Uri? = null

    // Matches found in current document (start, end)
    private var matches: List<IntRange> = emptyList()
    private var currentMatchIndex: Int = -1

    // Debounce job for status updates
    private var statusJob: Job? = null

    // SAF launchers
    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            takePersistablePermission(it, read = true, write = false)
            openUriIntoEditor(it)
        }
    }

    private val openMultipleLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        // заглушка — использовать при желании
        Toast.makeText(this, "Выбрано ${uris.size} файлов (заглушка)", Toast.LENGTH_SHORT).show()
    }

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let {
            saveTextToUri(it, editorView.text.toString())
            currentUri = it
        }
    }

    private val openFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        treeUri?.let {
            takePersistablePermission(it, read = true, write = true)
            // передать в BatchReplaceActivity и т.д.
            Toast.makeText(this, "Папка выбрана (заглушка): $it", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)

        editorView = findViewById(R.id.edittext_main)
        statusView = findViewById(R.id.status_bar)
        progressOverlay = findViewById(R.id.progress_overlay)
        progressBar = findViewById(R.id.progress_bar)

        setupButtons()
        setupEditorListeners()
    }

    // Inflate menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    // Handle menu actions (currently stubbed / delegate to same handlers as buttons)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open_file -> { onOpenFileClick(); true }
            R.id.action_open_multiple -> { onOpenMultipleClick(); true }
            R.id.action_save_as -> { onSaveAsClick(); true }
            R.id.action_open_folder -> { onOpenFolderClick(); true }
            R.id.action_search_replace -> { showFindReplaceDialog(); true }
            R.id.action_compare -> { onCompareClick(); true }
            R.id.action_settings -> { onSettingsClick(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_open_file).setOnClickListener { onOpenFileClick() }
        findViewById<Button>(R.id.btn_save_as).setOnClickListener { onSaveAsClick() }
        findViewById<Button>(R.id.btn_open_folder).setOnClickListener { onOpenFolderClick() }
        findViewById<Button>(R.id.btn_search_replace).setOnClickListener { showFindReplaceDialog() }
        findViewById<Button>(R.id.btn_compare).setOnClickListener { onCompareClick() }
        findViewById<Button>(R.id.btn_batch_search).setOnClickListener { onBatchSearchClick() }
        findViewById<Button>(R.id.btn_batch_replace).setOnClickListener { onBatchReplaceClick() }
        findViewById<Button>(R.id.btn_settings).setOnClickListener { onSettingsClick() }
    }

    // -----------------------
    // Actions (buttons/menu)
    // -----------------------
    private fun onOpenFileClick() {
        // MIME types any; you may restrict to text/*
        openFileLauncher.launch(arrayOf("*/*"))
    }

    private fun onOpenMultipleClick() {
        openMultipleLauncher.launch(arrayOf("*/*"))
    }

    private fun onSaveAsClick() {
        createDocumentLauncher.launch("untitled.txt")
    }

    private fun onOpenFolderClick() {
        openFolderLauncher.launch(null)
    }

    private fun onCompareClick() {
        // start CompareActivity (placeholder)
        val it = Intent(this, CompareActivity::class.java)
        startActivity(it)
    }

    private fun onBatchSearchClick() {
        val it = Intent(this, BatchSearchActivity::class.java)
        startActivity(it)
    }

    private fun onBatchReplaceClick() {
        val it = Intent(this, BatchReplaceActivity::class.java)
        startActivity(it)
    }

    private fun onSettingsClick() {
        val it = Intent(this, SettingsActivity::class.java)
        startActivity(it)
    }

    // -----------------------
    // SAF helpers: read/write
    // -----------------------
    private fun openUriIntoEditor(uri: Uri) {
        showProgress(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val text = contentResolver.openInputStream(uri)?.use { stream ->
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { isr ->
                        BufferedReader(isr).use { br -> br.readText() }
                    }
                } ?: ""
                withContext(Dispatchers.Main) {
                    editorView.setText(text)
                    currentUri = uri
                    Toast.makeText(this@MainActivity, "Файл открыт", Toast.LENGTH_SHORT).show()
                    computeStatusDebounced()
                    clearSearchMatches()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { showProgress(false) }
            }
        }
    }

    private fun saveTextToUri(uri: Uri, text: String) {
        showProgress(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(StandardCharsets.UTF_8))
                    out.flush()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Сохранено", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка записи: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { showProgress(false) }
            }
        }
    }

    // Persistable permission for SAF Uris
    private fun takePersistablePermission(uri: Uri, read: Boolean, write: Boolean) {
        val flags = (if (read) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (ex: SecurityException) {
            Toast.makeText(this, "Не удалось получить долгий доступ: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -----------------------
    // Find / Replace in current file
    // -----------------------
    private fun showFindReplaceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_find_replace, null)
        val etFind = dialogView.findViewById<EditText>(R.id.et_find)
        val etReplace = dialogView.findViewById<EditText>(R.id.et_replace)
        val cbCaseInsensitive = dialogView.findViewById<CheckBox>(R.id.cb_case_insensitive)
        cbCaseInsensitive.isChecked = true // default

        val dialog = AlertDialog.Builder(this)
            .setTitle("Найти / Заменить (в файле)")
            .setView(dialogView)
            .setPositiveButton("Найти") { _, _ ->
                val q = etFind.text.toString()
                val ci = cbCaseInsensitive.isChecked
                doFindInCurrentDocument(q, ci)
            }
            .setNeutralButton("Заменить все") { _, _ ->
                val q = etFind.text.toString()
                val r = etReplace.text.toString()
                val ci = cbCaseInsensitive.isChecked
                doReplaceAllInCurrentDocument(q, r, ci)
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()
    }

    private fun doFindInCurrentDocument(query: String, caseInsensitive: Boolean) {
        if (query.isEmpty()) {
            Toast.makeText(this, "Запрос пуст", Toast.LENGTH_SHORT).show()
            return
        }
        val text = editorView.text.toString()
        lifecycleScope.launch(Dispatchers.Default) {
            val pattern = buildLiteralPattern(query, caseInsensitive)
            val matcher = pattern.matcher(text)
            val found = mutableListOf<IntRange>()
            while (matcher.find()) {
                found.add(matcher.start() until matcher.end())
            }
            withContext(Dispatchers.Main) {
                matches = found
                if (matches.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Совпадений не найдено", Toast.LENGTH_SHORT).show()
                    currentMatchIndex = -1
                } else {
                    currentMatchIndex = 0
                    highlightMatchAt(currentMatchIndex)
                    Toast.makeText(this@MainActivity, "Найдено ${matches.size} совпадений", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun doReplaceAllInCurrentDocument(query: String, replacement: String, caseInsensitive: Boolean) {
        if (query.isEmpty()) {
            Toast.makeText(this, "Запрос пуст", Toast.LENGTH_SHORT).show()
            return
        }
        val originalText = editorView.text.toString()
        showProgress(true)
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val pattern = buildLiteralPattern(query, caseInsensitive)
                val matcher = pattern.matcher(originalText)
                val replaced = matcher.replaceAll(quoteReplacementSafe(replacement))
                withContext(Dispatchers.Main) {
                    editorView.setText(replaced)
                    computeStatusDebounced()
                    clearSearchMatches()
                    Toast.makeText(this@MainActivity, "Заменено", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка при замене: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { showProgress(false) }
            }
        }
    }

    // Highlight and select match at index
    private fun highlightMatchAt(index: Int) {
        if (index < 0 || index >= matches.size) return
        val range = matches[index]
        editorView.requestFocus()
        editorView.setSelection(range.first, range.last + 1) // setSelection end is exclusive
        // Also scroll to make selection visible
        editorView.post {
            val layout = editorView.layout
            if (layout != null) {
                val line = layout.getLineForOffset(range.first)
                val y = layout.getLineTop(line)
                editorView.scrollTo(0, y)
            }
        }
        updateStatusWithMatchIndex()
    }

    private fun updateStatusWithMatchIndex() {
        val base = buildStatusText()
        if (matches.isNotEmpty() && currentMatchIndex >= 0) {
            statusView.text = "$base — совпадение ${currentMatchIndex + 1}/${matches.size}"
        } else {
            statusView.text = base
        }
    }

    // Helper to clear search state
    private fun clearSearchMatches() {
        matches = emptyList()
        currentMatchIndex = -1
        updateStatusWithMatchIndex()
    }

    // Jump to next match (callable from UI later if desired)
    private fun gotoNextMatch() {
        if (matches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + 1) % matches.size
        highlightMatchAt(currentMatchIndex)
    }

    private fun gotoPrevMatch() {
        if (matches.isEmpty()) return
        currentMatchIndex = if (currentMatchIndex - 1 < 0) matches.size - 1 else currentMatchIndex - 1
        highlightMatchAt(currentMatchIndex)
    }

    // Pattern builder (literal, case-insensitive if requested)
    private fun buildLiteralPattern(query: String, caseInsensitive: Boolean): Pattern {
        val flags = if (caseInsensitive) (Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) else 0
        return Pattern.compile(Pattern.quote(query), flags)
    }

    private fun quoteReplacementSafe(repl: String) = java.util.regex.Matcher.quoteReplacement(repl)

    // -----------------------
    // Editor listeners and status
    // -----------------------
    private fun setupEditorListeners() {
        editorView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                computeStatusDebounced()
                clearSearchMatches() // edits invalidate matches
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        // initial status
        computeStatusDebounced()
    }

    private fun computeStatusDebounced() {
        statusJob?.cancel()
        statusJob = lifecycleScope.launch {
            delay(200) // debounce
            val text = editorView.text.toString()
            val words = countWords(text)
            val chars = text.length
            val lines = text.lines().size
            statusView.text = "Строк: $lines, Слов: $words, Символов: $chars"
        }
    }

    private fun buildStatusText(): String {
        val text = editorView.text.toString()
        val words = countWords(text)
        val chars = text.length
        val lines = text.lines().size
        return "Строк: $lines, Слов: $words, Символов: $chars"
    }

    private fun countWords(text: String): Int {
        // простая токенизация на базe whitespace
        return text.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    // -----------------------
    // UI helpers
    // -----------------------
    private fun showProgress(show: Boolean) {
        progressOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        statusJob?.cancel()
    }
}
