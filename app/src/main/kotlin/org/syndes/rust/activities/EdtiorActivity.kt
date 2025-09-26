package org.syndes.rust.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintJob
import android.print.PrintManager
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.UnderlineSpan
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import org.syndes.rust.FileDialog
import org.syndes.rust.R
import org.syndes.rust.SelectionMode
import org.syndes.rust.ServiceLocator
import org.syndes.rust.TPStrings
import org.syndes.rust.service.AlternativeUrlsService
import org.syndes.rust.service.RecentFilesService
import org.syndes.rust.service.SettingsService
import org.syndes.rust.service.ThemeService
import org.syndes.rust.utils.EditTextUndoRedo
import org.syndes.rust.utils.FileNameHelper
import org.syndes.rust.utils.System
import org.syndes.rust.utils.TextConverter
import java.io.*
import java.nio.charset.Charset
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class EditorActivity : AppCompatActivity() {

    companion object {
        private const val STATE_FILENAME = "filename"
        private const val STATE_CHANGED = "changed"
        private const val STATE_CURSOR_POSITION = "cursor-position"

        private const val REQUEST_OPEN = 1
        private const val REQUEST_SAVE = 2
        private const val REQUEST_SETTINGS = 3

        private const val ACTION_CREATE_FILE = 4
        private const val ACTION_OPEN_FILE = 5

        private const val DO_NOTHING = 0
        private const val DO_OPEN = 1
        private const val DO_NEW = 2
        private const val DO_EXIT = 3
        private const val DO_OPEN_RECENT = 4
        private const val DO_SHOW_SETTINGS = 5

        private const val LOG_TAG = "TextEditor"

        // selection position kept as static-like field (was static in Java)
        var selectionStart: Int = 0

        /**
         * Checks if the app has permission to write to device storage
         */
        fun verifyPermissions(activity: Activity) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return
            }
            val permission = activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val PERMISSIONS_STORAGE = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.WAKE_LOCK
            )

            if (permission != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(PERMISSIONS_STORAGE, 1)
            }
        }
    }

    private val mimeTypes = arrayOf(
        "*/*",
        "text/*",
        "plain/*",
        "text/javascript",
        "application/ecmascript",
        "application/javascript"
    )

    private lateinit var mText: EditText
    private var scrollView: ScrollView? = null
    private var linearLayout: LinearLayout? = null

    private var urlFilename: String = TPStrings.EMPTY
    var lastTriedSystemUri: Uri? = null

    private var changed = false
    private var exitDialogShown = false

    private var next_action = DO_NOTHING
    private var next_action_filename = ""

    private lateinit var settingsService: SettingsService
    private lateinit var recentFilesService: RecentFilesService
    private lateinit var alternativeUrlsService: AlternativeUrlsService

    private var queryTextListener: QueryTextListener? = null
    private lateinit var textWatcher: TextWatcher

    private lateinit var editTextUndoRedo: EditTextUndoRedo

    private var mWebView: WebView? = null

    // ---------------- lifecycle ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsService = ServiceLocator.getInstance().getSettingsService(applicationContext)
        recentFilesService = ServiceLocator.getInstance().getRecentFilesService()
        alternativeUrlsService = ServiceLocator.getInstance().getAlternativeUrlsService()

        if (simpleScrolling()) {
            setContentView(R.layout.main_simple_scrolling)
        } else {
            setContentView(R.layout.main)
        }

        mText = findViewById(R.id.editText1)
        mText.setBackgroundResource(android.R.color.transparent)
        editTextUndoRedo = EditTextUndoRedo(mText, this)

        if (simpleScrolling()) {
            linearLayout = findViewById(R.id.linear_layout)
        } else {
            scrollView = findViewById(R.id.vscroll)
        }

        applyPreferences()

        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        } else {
            verifyPermissions(this)

            val i = intent
            if (TPStrings.ACTION_VIEW == i.action) {
                val u = i.data
                if (u != null) {
                    openFileByUri(u)
                }
            } else {
                if (isFilenameEmpty()) {
                    if (settingsService.isOpenLastFile()) {
                        openLastFile()
                    }
                }
            }
        }

        setTextWatcher()
        updateTitle()
        mText.requestFocus()
        settingsService.applyLocale(baseContext)
    }

    private fun simpleScrolling(): Boolean = settingsService.isUseSimpleScrolling()

    private fun openFileByUri(u: Uri) {
        if (useAndroidManager()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (settingsService.isAlternativeFileAccess() &&
                    alternativeUrlsService.hasAlternativeUrl(u, applicationContext)
                ) {
                    openNamedFile(alternativeUrlsService.getAlternativeUrl(u, applicationContext))
                } else {
                    openNamedFile(u)
                }
            }
        } else {
            openNamedFileLegacy(u.path ?: "")
        }
    }

    private fun setTextWatcher() {
        textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (changed) return
                changed = true
                updateTitle()
            }
        }
    }

    // ---------------- key handling ----------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_S -> {
                    saveFile()
                    return true
                }
                KeyEvent.KEYCODE_Z -> {
                    editUndo()
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    editRedo()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        val t = mText.text.toString().lowercase(Locale.getDefault())
        mText.addTextChangedListener(textWatcher)
        if (selectionStart < t.length) {
            mText.setSelection(selectionStart, selectionStart)
        }

        if (SettingsService.isLanguageWasChanged()) {
            val intent = intent
            finish()
            startActivity(intent)
        }

        if (settingsService.useWakeLock()) {
            ServiceLocator.getInstance().getWakeLockService().acquireLock(applicationContext)
        }
    }

    override fun onPause() {
        if (settingsService.isAutosavingActive() && !isFilenameEmpty() && isChanged()) {
            saveFileIfNamed()
        }

        mText.removeTextChangedListener(textWatcher)
        selectionStart = mText.selectionStart
        if (settingsService.useWakeLock()) {
            ServiceLocator.getInstance().getWakeLockService().releaseLock()
        }
        super.onPause()
    }

    private fun isChanged(): Boolean = changed

    private fun restoreState(state: Bundle) {
        urlFilename = state.getString(STATE_FILENAME, TPStrings.EMPTY)
        changed = state.getBoolean(STATE_CHANGED)
        selectionStart = state.getInt(STATE_CURSOR_POSITION)
    }

    override fun onSaveInstanceState(@NonNull outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_FILENAME, urlFilename)
        outState.putBoolean(STATE_CHANGED, changed)
        outState.putInt(STATE_CURSOR_POSITION, mText.selectionStart)
    }

    override fun onBackPressed() {
        if (this.changed && !exitDialogShown) {
            AlertDialog.Builder(this)
                .setTitle(R.string.You_have_made_some_changes)
                .setMessage(R.string.Are_you_sure_to_quit)
                .setNegativeButton(R.string.Yes) { _, _ ->
                    super.onBackPressed()
                    exitDialogShown = false
                }
                .setPositiveButton(R.string.No) { _, _ ->
                    exitDialogShown = false
                }
                .setOnCancelListener { super.onBackPressed() }
                .create()
                .show()
            exitDialogShown = true
        } else {
            super.onBackPressed()
        }
    }

    private fun formatString(stringId: Int, parameter: String): String {
        return this.resources.getString(stringId, parameter)
    }

    private fun useAndroidManager(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return false
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            return true
        }

        return !settingsService.isLegacyFilePicker()
    }

    // ---------------- file / title helpers ----------------

    private fun openLastFile() {
        if (settingsService.getLastFilename() != TPStrings.EMPTY) {
            if (useAndroidManager()) {
                val uri = Uri.parse(settingsService.getLastFilename())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { // duplicated in useAndroidManager
                    this.openNamedFile(uri)
                }
            } else {
                this.openNamedFileLegacy(settingsService.getLastFilename())
            }
            showToast(formatString(R.string.opened_last_edited_file, settingsService.getLastFilename()))
        }
    }

    private fun updateTitle() {
        this.title = getEditingTitle()
    }

    private fun getEditingTitle(): String {
        var title: String
        if (isFilenameEmpty()) {
            title = TPStrings.NEW_FILE_TXT
        } else {
            val uri = Uri.parse(getFilename())
            title = FileNameHelper.getFilenameByUri(applicationContext, uri)
        }
        if (changed) {
            title = title + TPStrings.STAR
        }
        return title
    }

    private fun getFilename(): String {
        return urlFilename
    }

    private fun isFilenameEmpty(): Boolean {
        return urlFilename == TPStrings.EMPTY
    }

    // ---------------- preferences / UI ----------------

    private fun applyPreferences() {
        applyFontFace()
        applyFontSize()
        applyColors()
    }

    private fun applyFontFace() {
        mText.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_NORMAL or
                InputType.TYPE_CLASS_TEXT

        val font = settingsService.getFont()

        if (font == TPStrings.FONT_SERIF)
            mText.typeface = Typeface.SERIF
        else if (font == TPStrings.FONT_SANS_SERIF)
            mText.typeface = Typeface.SANS_SERIF
        else
            mText.typeface = Typeface.MONOSPACE
    }

    private fun applyFontSize() {
        when (settingsService.getFontSize()) {
            SettingsService.SETTING_EXTRA_SMALL -> mText.textSize = 12.0f
            SettingsService.SETTING_SMALL -> mText.textSize = 16.0f
            SettingsService.SETTING_LARGE -> mText.textSize = 24.0f
            SettingsService.SETTING_HUGE -> mText.textSize = 28.0f
            else -> mText.textSize = 20.0f
        }
    }

    private fun applyColors() {
        mText.highlightColor = settingsService.getTextSelectionColor()
        if (settingsService.isThemeForced()) {
            val themeService = ServiceLocator.getInstance().getThemeService(this)
            themeService.applyColorTheme(this)
        }
        if (settingsService.isCustomTheme()) {
            if (simpleScrolling()) {
                linearLayout?.setBackgroundColor(settingsService.getBgColor())
            } else {
                scrollView?.setBackgroundColor(settingsService.getBgColor())
            }
            mText.setTextColor(settingsService.getFontColor())
        }
    }

    // ---------------- search ----------------

    private fun getQueryTextListener(): QueryTextListener {
        if (queryTextListener == null) {
            queryTextListener = QueryTextListener()
        }
        return queryTextListener!!
    }

    private fun initSearch(searchItem: MenuItem) {
        val searchView = searchItem.actionView as? SearchView
        if (searchView != null) {
            searchView.isSubmitButtonEnabled = true
            searchView.isIconified = false
            searchView.imeOptions = EditorInfo.IME_ACTION_GO
            searchView.setOnQueryTextListener(getQueryTextListener())
            searchItem.setOnActionExpandListener(getQueryTextListener())
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {

        val searchItem = menu.findItem(R.id.menu_document_search)
        if (searchItem.isActionViewExpanded) {
            searchItem.collapseActionView()
        }

        val undoMenu = menu.findItem(R.id.menu_edit_undo)
        undoMenu.isEnabled = editTextUndoRedo.getCanUndo()

        val redoMenu = menu.findItem(R.id.menu_edit_redo)
        redoMenu.isEnabled = editTextUndoRedo.getCanRedo()

        updateRecentFiles(menu)

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val printMenu = menu.findItem(R.id.menu_document_print)
            printMenu.isVisible = false
        }

        return true
    }

    private fun updateRecentFiles(menu: Menu) {
        val recentFilesMenuItem = menu.findItem(R.id.menu_document_open_last)
        if (!settingsService.isShowLastEditedFiles()) {
            recentFilesMenuItem.isVisible = false
            return
        }
        val recentFiles = recentFilesService.getLastFiles(1, this.applicationContext)
        val recentFilesMenuItem1 = menu.findItem(R.id.menu_document_open_last1)
        val recentFilesMenuItem2 = menu.findItem(R.id.menu_document_open_last2)
        val recentFilesMenuItem3 = menu.findItem(R.id.menu_document_open_last3)
        val recentFilesMenuItem4 = menu.findItem(R.id.menu_document_open_last4)
        val recentFilesMenuItem5 = menu.findItem(R.id.menu_document_open_last5)

        when (recentFiles.size) {
            0 -> {
                recentFilesMenuItem.isVisible = false
                recentFilesMenuItem1.isVisible = false
                recentFilesMenuItem2.isVisible = false
                recentFilesMenuItem3.isVisible = false
                recentFilesMenuItem4.isVisible = false
                recentFilesMenuItem5.isVisible = false
            }
            1 -> {
                recentFilesMenuItem.isVisible = true
                recentFilesMenuItem1.isVisible = true
                recentFilesMenuItem1.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[0])
                recentFilesMenuItem2.isVisible = false
                recentFilesMenuItem3.isVisible = false
                recentFilesMenuItem4.isVisible = false
                recentFilesMenuItem5.isVisible = false
            }
            2 -> {
                recentFilesMenuItem.isVisible = true
                recentFilesMenuItem1.isVisible = true
                recentFilesMenuItem1.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[0])
                recentFilesMenuItem2.isVisible = true
                recentFilesMenuItem2.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[1])
                recentFilesMenuItem3.isVisible = false
                recentFilesMenuItem4.isVisible = false
                recentFilesMenuItem5.isVisible = false
            }
            3 -> {
                recentFilesMenuItem.isVisible = true
                recentFilesMenuItem1.isVisible = true
                recentFilesMenuItem1.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[0])
                recentFilesMenuItem2.isVisible = true
                recentFilesMenuItem2.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[1])
                recentFilesMenuItem3.isVisible = true
                recentFilesMenuItem3.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[2])
                recentFilesMenuItem4.isVisible = false
                recentFilesMenuItem5.isVisible = false
            }
            4 -> {
                recentFilesMenuItem.isVisible = true
                recentFilesMenuItem1.isVisible = true
                recentFilesMenuItem1.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[0])
                recentFilesMenuItem2.isVisible = true
                recentFilesMenuItem2.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[1])
                recentFilesMenuItem3.isVisible = true
                recentFilesMenuItem3.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[2])
                recentFilesMenuItem4.isVisible = true
                recentFilesMenuItem4.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[3])
                recentFilesMenuItem5.isVisible = false
            }
            else -> {
                recentFilesMenuItem.isVisible = true
                recentFilesMenuItem1.isVisible = true
                recentFilesMenuItem1.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[0])
                recentFilesMenuItem2.isVisible = true
                recentFilesMenuItem2.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[1])
                recentFilesMenuItem3.isVisible = true
                recentFilesMenuItem3.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[2])
                recentFilesMenuItem4.isVisible = true
                recentFilesMenuItem4.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[3])
                recentFilesMenuItem5.isVisible = true
                recentFilesMenuItem5.title = FileNameHelper.getFilenameByUri(applicationContext, recentFiles[4])
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        if (menu is MenuBuilder) {
            (menu as MenuBuilder).setOptionalIconsVisible(true)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.menu_document_open -> openFile()
            R.id.menu_document_open_other -> openFile()
            R.id.menu_document_search -> initSearch(item)
            R.id.menu_document_open_last1 -> openRecentFile(0)
            R.id.menu_document_open_last2 -> openRecentFile(1)
            R.id.menu_document_open_last3 -> openRecentFile(2)
            R.id.menu_document_open_last4 -> openRecentFile(3)
            R.id.menu_document_open_last5 -> openRecentFile(4)
            R.id.menu_document_new -> newFile()
            R.id.menu_document_save -> saveFile()
            R.id.menu_document_save_as -> saveAs()
            R.id.menu_edit_undo -> editUndo()
            R.id.menu_edit_redo -> editRedo()
            R.id.menu_document_share -> shareText()
            R.id.menu_document_print -> printText()
            R.id.menu_document_settings -> showSettings()
            R.id.menu_exit -> exitApplication()
        }

        return super.onOptionsItemSelected(item)
    }

    // ---------------- printing / sharing ----------------

    private fun printText() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val webView = WebView(this)
            webView.webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    createWebPrintJob(view)
                    mWebView = null
                }
            }

            val htmlDocument = "<html><body><pre style='padding:1.5cm 1cm 1.5cm 2cm'>" +
                    mText.text +
                    "</pre></body></html>"
            webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null)

            mWebView = webView
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun createWebPrintJob(webView: WebView) {

        // Get a PrintManager instance
        val printManager = this.getSystemService(Context.PRINT_SERVICE) as? PrintManager

        val jobName = getString(R.string.app_name) + " Document"

        // Get a print adapter instance
        val printAdapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter(jobName)

        // Create a print job with name and adapter instance
        val printJob: PrintJob = printManager!!.print(jobName, printAdapter,
            PrintAttributes.Builder().build())

        // Save the job object for later status checking
        val printJobs: MutableList<PrintJob> = ArrayList()
        printJobs.add(printJob)
    }

    private fun shareText() {
        val textToShare = this.mText.text.toString()
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, textToShare)
        sendIntent.type = "text/plain"

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    // ---------------- settings / file actions ----------------

    private fun showSettings() {
        if (changed) {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.File_not_saved)
                .setMessage(R.string.Save_current_file)
                .setPositiveButton(R.string.Yes) { _, _ ->
                    // Stop the activity
                    next_action = DO_SHOW_SETTINGS
                    saveFile()
                }
                .setNegativeButton(R.string.No) { _, _ ->
                    showSettingsActivity()
                }.show()
        } else {
            showSettingsActivity()
        }
    }

    private fun showSettingsActivity() {
        val intent = Intent(this.baseContext,
            SettingsActivity::class.java)
        this.startActivityForResult(intent, REQUEST_SETTINGS)
    }

    private fun openRecentFile(i: Int) {
        val lastFiles = recentFilesService.getLastFiles(1, this.getApplicationContext())
        if (i >= lastFiles.size) {
            return
        }
        val filename = lastFiles[i]
        if (changed) {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.File_not_saved)
                .setMessage(R.string.Save_current_file)
                .setPositiveButton(R.string.Yes) { _, _ ->
                    // Stop the activity
                    next_action = DO_OPEN_RECENT
                    next_action_filename = filename
                    EditorActivity.this.saveFile()
                }
                .setNegativeButton(R.string.No) { _, _ ->
                    openFileByName(filename)
                }.show()
        } else {
            openFileByName(filename)
        }
    }

    private fun openFileByName(filename: String) {
        if (useAndroidManager()) {
            val uri = Uri.parse(filename)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { //duplicated in useAndroidManager
                this.openNamedFile(uri)
            }
        } else {
            this.openNamedFileLegacy(filename)
        }
    }

    fun newFile() {
        if (changed) {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.File_not_saved)
                .setMessage(R.string.Save_current_file)
                .setPositiveButton(R.string.Yes) { _, _ ->
                    // Stop the activity
                    next_action = DO_NEW
                    EditorActivity.this.saveFile()
                }
                .setNegativeButton(R.string.No) { _, _ ->
                    clearFile()
                }.show()
        } else {
            clearFile()
        }
    }

    fun clearFile() {
        mText.setText(TPStrings.EMPTY)
        setFilename(TPStrings.EMPTY)
        initEditor()
        updateTitle()
    }

    private fun setFilename(value: String) {
        this.urlFilename = value
        storeLastFileName(value)
    }

    private fun storeLastFileName(value: String) {
        if (isFilenameEmpty()) {
            return
        }
        if (!settingsService.isShowLastEditedFiles()) {
            return
        }
        recentFilesService.addRecentFile(value, getApplicationContext())
    }

    protected fun initEditor() {
        changed = false
        editTextUndoRedo.clearHistory()
        queryTextListener = null
    }

    protected fun editRedo() {
        editTextUndoRedo.redo()
    }

    protected fun editUndo() {
        editTextUndoRedo.undo()
    }

    protected fun saveAs() {
        if (useAndroidManager()) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            intent.putExtra(Intent.EXTRA_TITLE, TPStrings.NEW_FILE_TXT)
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
            startActivityForResult(intent, ACTION_CREATE_FILE)
        } else {
            val intent = Intent(this.baseContext, FileDialog::class.java)
            this.startActivityForResult(intent, REQUEST_SAVE)
        }
    }

    protected fun openFile() {
        if (changed) {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.File_not_saved)
                .setMessage(R.string.Save_current_file)
                .setPositiveButton(R.string.Yes) { _, _ ->
                    // Stop the activity
                    next_action = DO_OPEN
                    saveFile()
                }
                .setNegativeButton(R.string.No) { _, _ ->
                    openNewFile()
                }.show()
        } else {
            openNewFile()
        }
    }

    protected fun exitApplication() {
        if (changed) {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.File_not_saved)
                .setMessage(R.string.Save_current_file)
                .setPositiveButton(R.string.Yes) { _, _ ->
                    // Stop the activity
                    next_action = DO_EXIT
                    EditorActivity.this.saveFile()
                }
                .setNegativeButton(R.string.No) { _, _ ->
                    System.exitFromApp(EditorActivity.this)
                }.show()
        } else {
            System.exitFromApp(EditorActivity.this)
        }
    }

    protected fun selectFileUsingAndroidSystemPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        intent.putExtra(Intent.EXTRA_TITLE, TPStrings.NEW_FILE_TXT)
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
        startActivityForResult(intent, ACTION_OPEN_FILE)
    }

    protected fun openNewFile() {
        if (useAndroidManager()) {
            selectFileUsingAndroidSystemPicker()
        } else {
            val intent = Intent(this.baseContext, FileDialog::class.java)
            intent.putExtra(TPStrings.SELECTION_MODE, SelectionMode.MODE_OPEN)
            this.startActivityForResult(intent, REQUEST_OPEN)
        }
    }

    protected fun saveFile() {
        if (isFilenameEmpty()) {
            saveAs()
        } else {
            saveFileIfNamed()
        }
    }

    protected fun saveFileIfNamed() {
        if (useAndroidManager()) {
            saveNamedFile()
        } else {
            saveNamedFileLegacy()
        }
    }

    protected fun saveFileWithConfirmation() {
        if (this.fileAlreadyExists()) {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.File_already_exists)
                .setMessage(R.string.Existing_file_will_be_overwritten)
                .setPositiveButton(R.string.Yes) { _, _ ->
                    // Stop the activity
                    next_action = DO_OPEN
                    EditorActivity.this.saveFile()
                }.setNegativeButton(R.string.No) { _, _ ->
                    //do nothing!!
                }.show()
        } else {
            saveFileIfNamed()
        }
    }

    protected fun fileAlreadyExists(): Boolean {
        val f = File(getFilename())
        return f.exists()
    }

    // ---------------- saving / loading ----------------

    protected fun saveNamedFileLegacy() {
        try {
            val f = File(getFilename())
            if (!f.exists()) {
                if (!f.createNewFile()) {
                    showToast(R.string.Can_not_write_file)
                    return
                }
            }

            FileOutputStream(f).use { fos ->
                var s = this.mText.text.toString()
                s = applyEndings(s)

                fos.write(s.toByteArray(Charset.forName(settingsService.getFileEncoding())))
            }

            showToast(R.string.File_Written)
            initEditor()
            updateTitle()

            when (next_action) {
                DO_OPEN -> {
                    next_action = DO_NOTHING
                    openNewFile()
                }
                DO_NEW -> {
                    next_action = DO_NOTHING
                    clearFile()
                }
                DO_SHOW_SETTINGS -> {
                    next_action = DO_NOTHING
                    showSettingsActivity()
                }
                DO_OPEN_RECENT -> {
                    next_action = DO_NOTHING
                    openFileByName(next_action_filename)
                }
                DO_EXIT -> exitApplication()
            }
        } catch (e: FileNotFoundException) {
            this.showToast(R.string.File_not_found)
        } catch (e: IOException) {
            this.showToast(R.string.Can_not_write_file)
        } catch (e: Exception) {
            this.showToast(R.string.Can_not_write_file)
        }
    }

    @Throws(IOException::class)
    protected fun saveFile(uri: Uri) {
        val contentResolver: ContentResolver = contentResolver
        val outputStream = contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException()

        try {
            var s = this.mText.text.toString()

            s = applyEndings(s)

            outputStream.write(s.toByteArray(Charset.forName(settingsService.getFileEncoding())))
        } finally {
            outputStream.close()
        }
    }

    protected fun saveNamedFile() {
        try {
            val uri = Uri.parse(getFilename())
            saveFile(uri)

            showToast(R.string.File_Written)
            initEditor()
            updateTitle()

            when (next_action) {
                DO_OPEN -> {
                    next_action = DO_NOTHING
                    openNewFile()
                }
                DO_NEW -> {
                    next_action = DO_NOTHING
                    clearFile()
                }
                DO_SHOW_SETTINGS -> {
                    next_action = DO_NOTHING
                    showSettingsActivity()
                }
                DO_OPEN_RECENT -> {
                    next_action = DO_NOTHING
                    openFileByName(next_action_filename)
                }
                DO_EXIT -> exitApplication()
            }
        } catch (e: FileNotFoundException) {
            this.showToast(R.string.File_not_found)
        } catch (e: IOException) {
            this.showToast(R.string.Can_not_write_file)
        } catch (e: Exception) {
            this.showToast(R.string.Can_not_write_file)
        }
    }

    protected fun openNamedFileLegacy(filename: String) {
        try {
            val f = File(filename)
            val fis = FileInputStream(f)

            val size = f.length()
            val dis = DataInputStream(fis)
            val b = ByteArray(size.toInt())
            val length = dis.read(b, 0, size.toInt())

            dis.close()
            fis.close()

            var ttt = String(b, 0, length, Charset.forName(settingsService.getFileEncoding()))

            ttt = toUnixEndings(ttt)

            mText.setText(ttt)
            editTextUndoRedo.clearHistory()

            showToast(this.getBaseContext().resources.getString(R.string.File_opened_, filename))
            initEditor()
            this.setFilename(filename)
            if (!settingsService.getLastFilename().equals(filename)) {
                settingsService.setLastFilename(filename, this.getApplicationContext())
            }
            selectionStart = 0
            updateTitle()
        } catch (e: FileNotFoundException) {
            this.showToast(R.string.File_not_found)
        } catch (e: IOException) {
            this.showToast(R.string.Can_not_read_file)
        } catch (e: Exception) {
            this.showToast(R.string.Can_not_read_file)
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    protected fun openNamedFile(uri: Uri) {
        try {
            val contentResolver = contentResolver

            val inputStream = contentResolver.openInputStream(uri) ?: throw IOException()
            val size = inputStream.available()
            val dis = DataInputStream(inputStream)
            val b = ByteArray(size)
            val length = dis.read(b, 0, size)

            var ttt = String(b, 0, length, Charset.forName(settingsService.getFileEncoding()))
            ttt = toUnixEndings(ttt)

            inputStream.close()
            dis.close()

            mText.setText(ttt)
            editTextUndoRedo.clearHistory()

            showToast(this.getBaseContext().resources.getString(R.string.File_opened_, getFilename()))
            initEditor()
            setFilename(uri.toString())
            if (!settingsService.getLastFilename().equals(getFilename())) {
                settingsService.setLastFilename(getFilename(), this.getApplicationContext())
            }
            selectionStart = 0
            if (lastTriedSystemUri != null) {
                alternativeUrlsService.addAlternativeUrl(lastTriedSystemUri!!, uri, getApplicationContext())
                lastTriedSystemUri = null
            }
            updateTitle()
            detectReadOnlyAccess(uri)
        } catch (e: FileNotFoundException) {
            if (isAccessDeniedException(e)) {
                showAlternativeFileDialog(uri)
            } else {
                this.showToast(R.string.File_not_found)
            }
        } catch (e: Exception) {
            this.showToast(R.string.Can_not_read_file)
        }
    }

    private fun detectReadOnlyAccess(uri: Uri) {
        var isReadOnly = false

        try {
            val pfdWrite = contentResolver.openFileDescriptor(uri, "rw")
            if (pfdWrite == null) {
                isReadOnly = true
            } else {
                pfdWrite.close()
            }
        } catch (e: Exception) {
            isReadOnly = true
        }

        if (isReadOnly) {
            Handler().postDelayed({ showReadOnlyDialog() }, 1000)
        }

    }

    fun showReadOnlyDialog() {
        val context = this
        // Message text with a clickable link
        val linkText = getString(R.string.readOnlyDialogClickHere)
        val fullMessage = getString(R.string.readOnlyDialogMessage) + " " + linkText
        val spannableMessage = SpannableString(fullMessage)

        // Set the clickable part
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: android.view.View) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://texteditor.maxistar.me/faq/"))
                context.startActivity(browserIntent)
            }
        }
        val start = fullMessage.length - linkText.length
        val end = fullMessage.length
        spannableMessage.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableMessage.setSpan(UnderlineSpan(), start, end, 0)

        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.readOnlyDialogTitle)
        builder.setMessage(spannableMessage)
        builder.setCancelable(true)
        builder.setPositiveButton(R.string.readOnlyDialogButtonOpenAgain) { dialog, _ ->
            openFile()
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.readOnlyDialogButtonContinue) { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
        (dialog.findViewById<TextView>(android.R.id.message))?.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showAlternativeFileDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.AlternativeFileAccessTitle)
            .setMessage(R.string.SelectAlternativeLocationForFile)
            .setNegativeButton(R.string.Yes) { _, _ ->
                lastTriedSystemUri = uri
                selectFileUsingAndroidSystemPicker()
            }
            .setPositiveButton(R.string.No) { _, _ ->
                lastTriedSystemUri = null
            }
            .setOnCancelListener {
                lastTriedSystemUri = null
                EditorActivity.super.onBackPressed()
            }
            .create()
            .show()
    }

    private fun isAccessDeniedException(e: FileNotFoundException): Boolean {
        if (!settingsService.isAlternativeFileAccess()) {
            return false
        }
        val message = e.message ?: return false
        return (message.contains("EACCES"))
    }

    /**
     * @param value String to fix
     * @return Fixed String
     */
    fun applyEndings(value: String): String {
        val to = settingsService.getDelimiters()
        return TextConverter.getInstance().applyEndings(value, to)
    }

    /**
     * @param value Value
     * @return String
     */
    fun toUnixEndings(value: String): String {
        val from = settingsService.getDelimiters()
        if (TPStrings.DEFAULT == from) {
            return value //this way we spare memory but will be unable to fix delimiters
        }

        //we should anyway fix any line delimiters
        //replace \r\n first, then \r into \n this way we will get pure unix ending used in android
        return TextConverter.getInstance().applyEndings(value, TextConverter.UNIX)
    }

    fun getSearchSelectionColor(): Int {
        return settingsService.getSearchSelectionColor()
    }

    // ---------------- activity results ----------------

    @SuppressLint("WrongConstant")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        if (requestCode == REQUEST_SAVE) {
            if (resultCode == Activity.RESULT_OK) {
                setFilename(
                    data?.getStringExtra(TPStrings.RESULT_PATH) ?: TPStrings.EMPTY
                )
                this.saveFileWithConfirmation()
            } else if (resultCode == Activity.RESULT_CANCELED) {
                showToast(R.string.Operation_Canceled)
            }
        } else if (requestCode == REQUEST_OPEN) {
            if (resultCode == Activity.RESULT_OK) {
                this.openNamedFileLegacy(data?.getStringExtra(TPStrings.RESULT_PATH) ?: TPStrings.EMPTY)
            } else if (resultCode == Activity.RESULT_CANCELED) {
                showToast(R.string.Operation_Canceled)
            }
        } else if (requestCode == REQUEST_SETTINGS) {
            applyPreferences()
        } else if (requestCode == ACTION_OPEN_FILE
            && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            var uri: Uri?
            if (data != null) {
                uri = data.data
                if (uri != null) {
                    // Check for the freshest data.
                    persistUriPermissions(data)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        openNamedFile(uri)
                    }
                }
            }
        } else if (requestCode == ACTION_CREATE_FILE) {
            if (data != null) {
                persistUriPermissions(data)
                val uri = data.data
                if (uri != null) {
                    setFilename(uri.toString())
                    this.saveFileWithConfirmation()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @SuppressLint("WrongConstant")
    private fun persistUriPermissions(data: Intent) {
        // Check for the freshest data.
        val uri = data.data ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val takeFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
    }

    protected fun showToast(toast_str: Int) {
        val context = applicationContext
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, toast_str, duration)
        toast.show()
    }

    protected fun showToast(toast_str: String) {
        val context = applicationContext
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, toast_str, duration)
        toast.show()
    }

    // ---------------- inner classes ----------------

    private inner class QueryTextListener : SearchView.OnQueryTextListener, MenuItem.OnActionExpandListener {
        private val span = BackgroundColorSpan(getSearchSelectionColor())
        private val editable: Editable
        private var matcher: Matcher? = null
        private var index: Int = 0
        private val height: Int

        init {
            // Use regex search and spannable for highlighting
            height = if (simpleScrolling()) {
                linearLayout?.height ?: 0
            } else {
                scrollView?.height ?: 0
            }
            editable = mText.editableText
        }

        override fun onQueryTextChange(newText: String): Boolean {
            // Reset the index and clear highlighting
            if (newText.length == 0) {
                index = 0
                editable.removeSpan(span)
                return false
            }

            // Check pattern
            try {
                val escapedTextToFind = Pattern.quote(newText)
                val pattern = Pattern.compile(escapedTextToFind, Pattern.MULTILINE or Pattern.CASE_INSENSITIVE)
                matcher = pattern.matcher(editable)
            } catch (e: Exception) {
                return false
            }

            // Find text
            val m = matcher
            if (m != null && m.find(index)) {
                // Check layout
                if (mText.layout == null) {
                    return false
                }
                doSearch()
            } else {
                index = 0
            }
            return true
        }

        override fun onQueryTextSubmit(query: String): Boolean {
            // Find next text
            val m = matcher
            if (m != null) {
                if (m.find()) {
                    // Check layout
                    if (mText.layout == null) {
                        return false
                    }
                    doSearch()
                } else {
                    Toast.makeText(
                        this@EditorActivity,
                        formatString(R.string.s_not_found, query),
                        Toast.LENGTH_SHORT
                    ).show()
                    m.reset()
                    index = 0
                    editable.removeSpan(span)
                }
            }

            return true
        }

        private fun doSearch() {
            val m = matcher ?: return

            // Get index
            index = m.start()

            // Get text position
            val line = mText.layout.getLineForOffset(index)
            val pos = mText.layout.getLineBaseline(line)

            // Scroll to it
            if (simpleScrolling()) {
                mText.scrollTo(0, pos - height / 2)
            } else {
                scrollView?.smoothScrollTo(0, pos - height / 2)
            }
            // Highlight it
            editable.setSpan(
                span,
                m.start(),
                m.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
            return true
        }

        override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
            editable.removeSpan(span)
            mText.requestFocus()
            queryTextListener = null
            return true
        }
    }
}
