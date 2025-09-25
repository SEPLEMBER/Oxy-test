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
 * - Search all files recursively for the literal query (case-insensitive optional)
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

    private val openTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            treeUri = it
            tvStatus.text = "Папка выбрана: ${DocumentFile.fromTreeUri(this, it)?.name ?: it}"
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
        // cancel previous
        searchJob?.cancel()

        resultsAdapter.clear()
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Запуск поиска..."

        val pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)

        searchJob = lifecycleScope.launch(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(this@BatchSearchActivity, treeUri!!)
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
                // try to be safe: skip binaries by mime or extension? We'll attempt to read text; ignore exceptions
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
                                yield() // be cooperative, allow cancellation
                            }
                        }
                    }
                } catch (_: Exception) {
                    // ignore unreadable/binary files
                }
            }

            suspend fun recurse(folder: DocumentFile) {
                for (child in folder.listFiles()) {
                    ensureActive() // cancellation check
                    if (child.isDirectory) recurse(child)
                    else processFile(child)
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

    // Simple adapter classes
    data class SearchResultItem(val fileDisplayName: String, val fileUri: Uri, val lineNumber: Int, val lineText: String)

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
            val it = items[position]
            holder.tvFile.text = "${it.fileDisplayName} : ${it.lineNumber}"
            holder.tvLine.text = it.lineText
            holder.itemView.setOnClickListener {
                // Open selected file in MainActivity editor: launch MainActivity with ACTION_VIEW-like intent
                val intent = Intent(this@BatchSearchActivity, MainActivity::class.java)
                intent.putExtra("open_uri", it.fileUri.toString())
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
