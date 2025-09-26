package org.syndes.rust

import android.app.ListActivity
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.widget.*
import java.io.File
import java.util.*

@Suppress("DEPRECATION")
class FileDialog : ListActivity() {

    private var path: MutableList<String> = ArrayList()
    private lateinit var myPath: TextView
    private lateinit var mFileName: EditText
    private lateinit var mList: ArrayList<HashMap<String, Any>>
    private var parentPath: String? = null
    private var currentPath: String = ""
    private var rootPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED, intent)

        setContentView(R.layout.file_dialog_main)
        myPath = findViewById(R.id.path)
        mFileName = findViewById(R.id.fdEditTextFile)

        mFileName.setText(TPStrings.NEW_FILE_TXT)
        val selectionMode = intent.getIntExtra(
            TPStrings.SELECTION_MODE,
            SelectionMode.MODE_CREATE
        )

        val layoutCreate = findViewById<LinearLayout>(R.id.fdLinearLayoutCreate)

        if (selectionMode == SelectionMode.MODE_OPEN) {
            layoutCreate.visibility = View.GONE
            title = getString(R.string.Open_File)
        } else {
            layoutCreate.visibility = View.VISIBLE
            title = getString(R.string.Save_File)
        }

        val cancelButton = findViewById<Button>(R.id.fdButtonCancel)
        cancelButton.setOnClickListener { finish() }

        val createButton = findViewById<Button>(R.id.fdButtonCreate)
        createButton.setOnClickListener {
            if (mFileName.text.length > 0) {
                intent.putExtra(
                    TPStrings.RESULT_PATH,
                    currentPath + TPStrings.SLASH + mFileName.text
                )
                setResult(RESULT_OK, intent)
                finish()
            }
        }

        val settings: SharedPreferences = getSharedPreferences(TPStrings.FILE_DIALOG, 0)
        rootPath = Environment.getExternalStorageDirectory().path
        val startPath = settings.getString(TPStrings.START_PATH, rootPath) ?: rootPath
        currentPath = startPath

        readDir(startPath)
    }

    private fun readDir(dirPath: String) {
        currentPath = dirPath

        path = ArrayList()
        mList = ArrayList()

        var f = File(currentPath)
        var files = f.listFiles()
        if (files == null) { //in case we can not show this
            currentPath = rootPath
            f = File(currentPath)
            files = f.listFiles()
        }

        myPath.text = getString(R.string.Location, currentPath)

        parentPath = f.parent

        try {
            val parentFolder = File(parentPath)
            if (!parentFolder.canRead()) {
                parentPath = parentFolder.parent
            }
        } catch (e: Exception) {
            // ignore
        }

        val dirsMap = TreeMap<String, String>()
        val dirsPathMap = TreeMap<String, String>()

        val filesMap = TreeMap<String, String>()
        val filesPathMap = TreeMap<String, String>()

        if (parentPath != null) {
            dirsMap[TPStrings.FOLDER_UP] = TPStrings.FOLDER_UP
            dirsPathMap[TPStrings.FOLDER_UP] = parentPath!!
        }

        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    val dirName = file.name
                    dirsMap[dirName] = dirName
                    dirsPathMap[dirName] = file.path
                } else {
                    filesMap[file.name] = file.name
                    filesPathMap[file.name] = file.path
                }
            }
        }

        path.addAll(dirsPathMap.values)
        path.addAll(filesPathMap.values)

        val fileList = SimpleAdapter(
            this,
            mList,
            R.layout.file_dialog_row, arrayOf(
                TPStrings.ITEM_KEY,
                TPStrings.ITEM_IMAGE
            ),
            intArrayOf(
                R.id.fdrowtext,
                R.id.fdrowimage
            )
        )

        // Используем системные иконки, чтобы не требовать наличия drawable/ic_*
        for (dir in dirsMap.tailMap(TPStrings.EMPTY).values) {
            addItem(dir, android.R.drawable.ic_menu_gallery)
        }

        for (file in filesMap.tailMap(TPStrings.EMPTY).values) {
            addItem(file, android.R.drawable.ic_menu_agenda)
        }

        fileList.notifyDataSetChanged()

        listAdapter = fileList
    }

    private fun addItem(fileName: String, imageId: Int) {
        val item = HashMap<String, Any>()
        item[TPStrings.ITEM_KEY] = fileName
        item[TPStrings.ITEM_IMAGE] = imageId
        mList.add(item)
    }

    private fun saveStartPath(currentPath: String) {
        val settings = getSharedPreferences(TPStrings.FILE_DIALOG, 0)
        val editor = settings.edit()
        editor.putString(TPStrings.START_PATH, currentPath)
        editor.apply()
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val file = File(path[position])
        if (file.isDirectory) {
            readDir(path[position])
            saveStartPath(currentPath)
        } else {
            saveStartPath(currentPath)
            v.isSelected = true

            intent.putExtra(TPStrings.RESULT_PATH, file.path)
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (parentPath != null && currentPath != rootPath) {
                readDir(parentPath!!)
            } else {
                return super.onKeyDown(keyCode, event)
            }
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }
}
