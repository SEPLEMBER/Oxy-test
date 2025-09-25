package org.syndes.rust

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * BatchSearchActivity
 *
 * Layout requirements (suggested ids):
 * - EditText @+id/et_query
 * - Button  @+id/btn_choose_folder
 * - Button  @+id/btn_start
 * - ProgressBar @+id/progress_bar
 * - TextView @+id/tv_status
 * - RecyclerView @+id/rv_results
 *
 * Behavior:
 * - Choose folder via SAF (OpenDocumentTree)
 * - Search all files recursively for the literal query (case-insensitive, unicode-aware)
 * - Shows results as list of (file displayName, lineNumber, lineText)
 * - Supports cancellation
 */
class BatchSearchActivity : AppCompatActivity() {

    private lateinit var etQuery: EditText
    private lateinit var btnChooseFolder: Button
    private lateinit var btnStart: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var rvResults: RecyclerView
    private val resultsAdapter = SearchResultsAdapter()

    private var treeUri: Uri? = null
    private var searchJob: Job? = null

    // Launcher for folder picking (SAF)
    private val openTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                // request persistable read+write permissions
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(it, flags)
            } catch (_: SecurityException) {
                // ignore - still proceed without persistable permission
            }
            treeUri = it
            val df = DocumentFile.fromTreeUri(this, it)
            tvStatus.text = "Папка выбрана: ${df?.name ?: it}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_search)

        etQuery = findViewById(R.id.et_query)
        btnChooseFolder = findViewById(R.id.btn_choose_folder)
        btnStart = findViewById(R.id.btn_start)
        progressBar = findViewById(R.id.progress_bar)
        tvStatus = findViewById(R.id.tv_status)
        rvResults = findViewById(R.id.rv_results)

        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = resultsAdapter

        btnChooseFolder.setOnClickListener { openTreeLauncher.launch(null) }

        btnStart.setOnClickListener {
            val q = etQuery.text.toString()
            if (q.isBlank()) {
                Toast.makeText(this, "Введите запрос", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (treeUri == null) {
                Toast.makeText(this, "Выберите папку", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startSearch(q)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }

    private fun startSearch(query: String) {
        // cancel previous search if running
        searchJob?.cancel()

        resultsAdapter.clear()
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Запуск поиска..."

        // literal pattern, case-insensitive + unicode-aware
        val pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)

        searchJob = lifecycleScope.launch(Dispatchers.IO) {
            val root = treeUri?.let { DocumentFile.fromTreeUri(this@BatchSearchActivity, it) }
            if (root == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BatchSearchActivity, "Не удалось открыть папку", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                }
                return@launch
            }

            var filesScanned = 0
            var matchesFound = 0

            suspend fun processFile(file: DocumentFile) {
                if (!file.isFile) return
                filesScanned++
                try {
                    contentResolver.openInputStream(file.uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { br ->
                            var line: String?
                            var lineNumber = 0
                            while (br.readLine().also { line = it } != null) {
                                lineNumber++
                                if (line == null) continue
                                val matcher = pattern.matcher(line!!)
                                if (matcher.find()) {
                                    matchesFound++
                                    val item = SearchResultItem(
                                        fileDisplayName = file.name ?: file.uri.toString(),
                                        fileUri = file.uri,
                                        lineNumber = lineNumber,
                                        lineText = line!!.trim()
                                    )
                                    withContext(Dispatchers.Main) {
                                        resultsAdapter.add(item)
                                        tvStatus.text = "Файлы: $filesScanned, Совпадений: $matchesFound"
                                    }
                                }
                                yield() // cooperative cancellation point
                            }
                        }
                    }
                } catch (_: Exception) {
                    // ignore unreadable/binary files
                }
            }

            suspend fun recurse(folder: DocumentFile) {
                val children = folder.listFiles()
                for (child in children) {
                    ensureActive() // cancellation check
                    if (child.isDirectory) {
                        recurse(child)
                    } else {
                        processFile(child)
                    }
                }
            }

            try {
                recurse(root)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Готово. Файлы: $filesScanned, Совпадений: $matchesFound"
                    progressBar.visibility = View.GONE
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Отменено. Найдено: $matchesFound"
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Ошибка: ${e.message}"
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    // Data class for a search result
    data class SearchResultItem(
        val fileDisplayName: String,
        val fileUri: Uri,
        val lineNumber: Int,
        val lineText: String
    )

    // Adapter for RecyclerView
    inner class SearchResultsAdapter : RecyclerView.Adapter<SearchResultsAdapter.VH>() {
        private val items = mutableListOf<SearchResultItem>()

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvFile: TextView = view.findViewById(android.R.id.text1)
            val tvLine: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvFile.text = "${item.fileDisplayName} : ${item.lineNumber}"
            holder.tvLine.text = item.lineText
            holder.itemView.setOnClickListener {
                // Open selected file in MainActivity editor:
                // pass the Uri as a string extra "open_uri"
                val intent = Intent(this@BatchSearchActivity, MainActivity::class.java).apply {
                    putExtra("open_uri", item.fileUri.toString())
                    // optional flags if you want to control activity reuse:
                    // flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            }
        }

        fun add(item: SearchResultItem) {
            items.add(item)
            notifyItemInserted(items.size - 1)
        }

        fun clear() {
            items.clear()
            notifyDataSetChanged()
        }
    }
}
