package org.syndes.rust

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * BatchReplaceActivity
 *
 * Layout requirements (suggested ids):
 * - EditText @+id/et_find
 * - EditText @+id/et_replace
 * - Button  @+id/btn_choose_folder
 * - Button  @+id/btn_start
 * - ProgressBar @+id/progress_bar
 * - TextView @+id/tv_status
 *
 * Behavior:
 * - Choose folder via SAF
 * - Replace occurrences in all files under folder
 * - Create backup file "<original>.bak" where possible
 * - Show progress and final report
 */
class BatchReplaceActivity : AppCompatActivity() {

    private lateinit var etFind: EditText
    private lateinit var etReplace: EditText
    private lateinit var btnChooseFolder: Button
    private lateinit var btnStart: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    private var treeUri: Uri? = null
    private var replaceJob: Job? = null

    private val openTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            treeUri = it
            tvStatus.text = "Папка выбрана: ${DocumentFile.fromTreeUri(this, it)?.name ?: it}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_replace)

        etFind = findViewById(R.id.et_find)
        etReplace = findViewById(R.id.et_replace)
        btnChooseFolder = findViewById(R.id.btn_choose_folder)
        btnStart = findViewById(R.id.btn_start)
        progressBar = findViewById(R.id.progress_bar)
        tvStatus = findViewById(R.id.tv_status)

        btnChooseFolder.setOnClickListener { openTreeLauncher.launch(null) }

        btnStart.setOnClickListener {
            val findText = etFind.text.toString()
            val replaceText = etReplace.text.toString()
            if (findText.isBlank()) {
                Toast.makeText(this, "Укажите что заменять", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (treeUri == null) {
                Toast.makeText(this, "Выберите папку", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Подтвердите замену")
                .setMessage("Будут изменены файлы в папке. Создаются .bak копии (если возможно). Продолжить?")
                .setPositiveButton("Да") { _, _ -> startReplace(findText, replaceText) }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        replaceJob?.cancel()
    }

    private fun startReplace(findText: String, replaceText: String) {
        replaceJob?.cancel()
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Запуск замены..."

        val pattern = Pattern.compile(Pattern.quote(findText), Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)

        replaceJob = lifecycleScope.launch(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(this@BatchReplaceActivity, treeUri!!)
            if (root == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BatchReplaceActivity, "Не удалось открыть папку", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                }
                return@launch
            }

            var filesScanned = 0
            var filesChanged = 0
            var replacementsTotal = 0

            suspend fun processFile(file: DocumentFile) {
                if (!file.isFile) return
                filesScanned++
                try {
                    val original = contentResolver.openInputStream(file.uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { br -> br.readText() }
                    } ?: return

                    val matcher = pattern.matcher(original)
                    if (!matcher.find()) return

                    // There is at least one replacement
                    val replaced = matcher.replaceAll(Matcher.quoteReplacement(replaceText))

                    // Count replacements (API < 34)
                    var replacementCount = 0
                    val countMatcher = pattern.matcher(original)
                    while (countMatcher.find()) {
                        replacementCount++
                    }

                    filesChanged++
                    replacementsTotal += replacementCount

                    // Create backup if possible
                    try {
                        val parent = file.parentFile
                        val bakName = (file.name ?: "file") + ".bak"
                        parent?.createFile("text/plain", bakName)?.also { bak ->
                            // write bak
                            contentResolver.openOutputStream(bak.uri)?.use { out ->
                                out.write(original.toByteArray(StandardCharsets.UTF_8))
                                out.flush()
                            }
                        }
                    } catch (_: Exception) {
                        // ignore backup failure
                    }

                    // Write replaced content back
                    contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                        out.write(replaced.toByteArray(StandardCharsets.UTF_8))
                        out.flush()
                    }

                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Файлы: $filesScanned, Изменено: $filesChanged, Замен: $replacementsTotal"
                    }

                } catch (_: Exception) {
                    // ignore individual file errors
                }
            }

            suspend fun recurse(folder: DocumentFile) {
                for (child in folder.listFiles()) {
                    ensureActive()
                    if (child.isDirectory) recurse(child) else processFile(child)
                }
            }

            try {
                recurse(root)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Готово. Пройдена: $filesScanned файлов, изменено: $filesChanged"
                    progressBar.visibility = View.GONE
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Отменено"
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
}
