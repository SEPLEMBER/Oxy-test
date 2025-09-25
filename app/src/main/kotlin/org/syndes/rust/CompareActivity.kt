package org.syndes.rust

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * CompareActivity
 *
 * Layout requirements:
 * - Button @+id/btn_choose_a
 * - Button @+id/btn_choose_b
 * - Button @+id/btn_compare
 * - ProgressBar @+id/progress_bar
 * - TextView @+id/tv_left (can be in ScrollView)
 * - TextView @+id/tv_right
 *
 * Behavior:
 * - Choose two files (OpenDocument)
 * - Compute simple line-based diff using LCS
 * - Display both texts side-by-side with differing lines highlighted
 */
class CompareActivity : AppCompatActivity() {

    private lateinit var btnChooseA: Button
    private lateinit var btnChooseB: Button
    private lateinit var btnCompare: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLeft: TextView
    private lateinit var tvRight: TextView
    private lateinit var tvStatus: TextView

    private var uriA: Uri? = null
    private var uriB: Uri? = null

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // We'll set the selection via a flag set by which button was last clicked
    }

    private var lastPickerTarget: Int = 0 // 1 = A, 2 = B
    private val pickLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            if (lastPickerTarget == 1) {
                uriA = it
                btnChooseA.text = "A: ${it.lastPathSegment ?: "file"}"
            } else {
                uriB = it
                btnChooseB.text = "B: ${it.lastPathSegment ?: "file"}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compare)

        btnChooseA = findViewById(R.id.btn_choose_a)
        btnChooseB = findViewById(R.id.btn_choose_b)
        btnCompare = findViewById(R.id.btn_compare)
        progressBar = findViewById(R.id.progress_bar)
        tvLeft = findViewById(R.id.tv_left)
        tvRight = findViewById(R.id.tv_right)
        tvStatus = findViewById(R.id.tv_status)

        btnChooseA.setOnClickListener {
            lastPickerTarget = 1
            pickLauncher.launch(arrayOf("*/*"))
        }
        btnChooseB.setOnClickListener {
            lastPickerTarget = 2
            pickLauncher.launch(arrayOf("*/*"))
        }

        btnCompare.setOnClickListener {
            if (uriA == null || uriB == null) {
                Toast.makeText(this, "Выберите оба файла", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            doCompare(uriA!!, uriB!!)
        }
    }

    private fun doCompare(a: Uri, b: Uri) {
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Сравнение..."
        tvLeft.text = ""
        tvRight.text = ""

        lifecycleScope.launch {
            try {
                val textA = withContext(Dispatchers.IO) { readUriText(a) }
                val textB = withContext(Dispatchers.IO) { readUriText(b) }

                val linesA = textA.lines()
                val linesB = textB.lines()

                val diffs = withContext(Dispatchers.Default) { diffLines(linesA, linesB) }

                withContext(Dispatchers.Main) {
                    val sbA = StringBuilder()
                    val sbB = StringBuilder()
                    for (i in diffs.indices) {
                        val d = diffs[i]
                        if (d.first != null && d.second != null && d.first == d.second) {
                            // equal line
                            sbA.append(d.first).append("\n")
                            sbB.append(d.second).append("\n")
                        } else {
                            // differing
                            if (d.first != null) sbA.append("[–] ").append(d.first).append("\n") else sbA.append("\n")
                            if (d.second != null) sbB.append("[+] ").append(d.second).append("\n") else sbB.append("\n")
                        }
                    }
                    tvLeft.text = sbA.toString()
                    tvRight.text = sbB.toString()
                    tvStatus.text = "Готово. Строк A=${linesA.size}, B=${linesB.size}"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CompareActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun readUriText(uri: Uri): String {
        return contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { br -> br.readText() }
        } ?: ""
    }

    /**
     * Simple line-based diff: produce list of pairs (lineA?, lineB?) aligned via LCS.
     * Returns list where each item corresponds to a "row" in comparison:
     * Pair(lineFromA or null, lineFromB or null)
     */
    private fun diffLines(a: List<String>, b: List<String>): List<Pair<String?, String?>> {
        val n = a.size
        val m = b.size
        // LCS dynamic programming
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) 1 + dp[i + 1][j + 1] else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val res = mutableListOf<Pair<String?, String?>>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            if (a[i] == b[j]) {
                res.add(Pair(a[i], b[j]))
                i++; j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                res.add(Pair(a[i], null))
                i++
            } else {
                res.add(Pair(null, b[j]))
                j++
            }
        }
        while (i < n) { res.add(Pair(a[i], null)); i++ }
        while (j < m) { res.add(Pair(null, b[j])); j++ }
        return res
    }
}
