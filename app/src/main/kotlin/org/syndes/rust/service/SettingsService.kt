package org.syndes.rust.service

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.preference.PreferenceManager
import org.syndes.rust.TPStrings
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class SettingsService {

    companion object {
        const val SETTING_FONT = "font"
        const val SETTING_LAST_FILENAME = "last_filename"
        const val SETTING_AUTO_SAVE_CURRENT_FILE = "auto_save_current_file"

        const val SETTING_COLOR_THEME_TYPE = "color_theme_type"
        const val SETTING_OPEN_LAST_FILE = "open_last_file"
        const val SETTING_DELIMITERS = "delimeters"
        const val SETTING_FILE_ENCODING = "encoding"
        const val SETTING_FONT_SIZE = "fontsize"
        const val SETTING_BG_COLOR = "bgcolor"
        const val SETTING_FONT_COLOR = "fontcolor"

        const val SETTING_SEARCH_SELECTION_COLOR = "search_selection_color"
        const val SETTING_TEXT_SELECTION_COLOR = "text_selection_color"

        const val SETTING_LANGUAGE = "language"
        const val SETTING_LEGASY_FILE_PICKER = "use_legacy_file_picker"
        const val SETTING_ALTERNATIVE_FILE_ACCESS = "use_alternative_file_access"
        const val SETTING_SHOW_LAST_EDITED_FILES = "show_last_edited_files"

        private const val SETTING_USE_WAKE_LOCK = "use_wake_lock"
        const val SETTING_USE_SIMPLE_SCROLLING = "use_simple_scrolling"

        const val SETTING_MEDIUM = "Medium"
        const val SETTING_EXTRA_SMALL = "Extra Small"
        const val SETTING_SMALL = "Small"
        const val SETTING_LARGE = "Large"
        const val SETTING_HUGE = "Huge"

        const val DEFAULT_BACKGROUND_COLOR = 0xFFDDDDDD.toInt()
        const val DEFAULT_TEXT_COLOR = 0xFF000000.toInt()
        const val DEFAULT_SEARCH_SELECTION_COLOR = 0xFFFFFF00.toInt()
        const val DEFAULT_TEXT_SELECTION_COLOR = 0xFF83A5AE.toInt()

        const val COLOR_THEME_EMPTY = ""
        const val COLOR_THEME_AUTO = "auto"
        const val COLOR_THEME_LIGHT = "light"
        const val COLOR_THEME_DARK = "dark"
        const val COLOR_THEME_CUSTOM = "custom"

        private var languageWasChanged = false

        @JvmStatic
        fun setLanguageChangedFlag() {
            languageWasChanged = true
        }

        @JvmStatic
        fun isLanguageWasChanged(): Boolean {
            val value = languageWasChanged
            languageWasChanged = false
            return value
        }
    }

    private var open_last_file = true
    private var show_last_edited_files = true
    private var legacy_file_picker = false
    private var alternative_file_access = true
    private var auto_save_current_file = false

    private var file_encoding: String = ""
    private var last_filename: String = ""
    private var delimiters: String = TPStrings.EMPTY
    private var font: String = TPStrings.FONT_SANS_SERIF
    private var font_size: String = SETTING_MEDIUM

    private var language: String = TPStrings.EMPTY

    private var colorThemeType: String = COLOR_THEME_AUTO

    private var bgcolor: Int = DEFAULT_BACKGROUND_COLOR
    private var fontcolor: Int = DEFAULT_TEXT_COLOR
    private var searchSelectionColor: Int = DEFAULT_SEARCH_SELECTION_COLOR
    private var textSelectionColor: Int = DEFAULT_TEXT_SELECTION_COLOR

    private var useWakeLock = false
    private var useSimpleScrolling = false

    constructor()

    fun loadSettings(context: Context) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        open_last_file = sharedPref.getBoolean(SETTING_OPEN_LAST_FILE, false)
        show_last_edited_files = sharedPref.getBoolean(SETTING_SHOW_LAST_EDITED_FILES, true)
        legacy_file_picker = sharedPref.getBoolean(SETTING_LEGASY_FILE_PICKER, false)
        useWakeLock = sharedPref.getBoolean(SETTING_USE_WAKE_LOCK, false)
        useSimpleScrolling = sharedPref.getBoolean(SETTING_USE_SIMPLE_SCROLLING, false)
        alternative_file_access = sharedPref.getBoolean(SETTING_ALTERNATIVE_FILE_ACCESS, true)
        last_filename = sharedPref.getString(SETTING_LAST_FILENAME, TPStrings.EMPTY) ?: TPStrings.EMPTY
        file_encoding = sharedPref.getString(SETTING_FILE_ENCODING, TPStrings.UTF_8) ?: TPStrings.UTF_8
        delimiters = sharedPref.getString(SETTING_DELIMITERS, TPStrings.EMPTY) ?: TPStrings.EMPTY
        font = sharedPref.getString(SETTING_FONT, TPStrings.FONT_SANS_SERIF) ?: TPStrings.FONT_SANS_SERIF
        font_size = sharedPref.getString(SETTING_FONT_SIZE, SETTING_MEDIUM) ?: SETTING_MEDIUM
        bgcolor = sharedPref.getInt(SETTING_BG_COLOR, DEFAULT_BACKGROUND_COLOR)
        fontcolor = sharedPref.getInt(SETTING_FONT_COLOR, DEFAULT_TEXT_COLOR)
        searchSelectionColor = sharedPref.getInt(SETTING_SEARCH_SELECTION_COLOR, DEFAULT_SEARCH_SELECTION_COLOR)
        textSelectionColor = sharedPref.getInt(SETTING_TEXT_SELECTION_COLOR, DEFAULT_TEXT_SELECTION_COLOR)
        language = sharedPref.getString(SETTING_LANGUAGE, TPStrings.EMPTY) ?: TPStrings.EMPTY
        auto_save_current_file = sharedPref.getBoolean(SETTING_AUTO_SAVE_CURRENT_FILE, false)
        colorThemeType = sharedPref.getString(SETTING_COLOR_THEME_TYPE, COLOR_THEME_EMPTY) ?: COLOR_THEME_EMPTY
    }

    fun reloadSettings(context: Context) {
        loadSettings(context)
    }

    private fun setSettingValue(name: String, value: String, context: Context) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putString(name, value)
        editor.apply()
    }

    private fun setSettingValue(name: String, value: Int, context: Context) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putInt(name, value)
        editor.apply()
    }

    private fun setSettingValue(name: String, value: Boolean, context: Context) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putBoolean(name, value)
        editor.apply()
    }

    fun isOpenLastFile(): Boolean = open_last_file
    fun isShowLastEditedFiles(): Boolean = show_last_edited_files
    fun isLegacyFilePicker(): Boolean = legacy_file_picker
    fun isAutosavingActive(): Boolean = auto_save_current_file
    fun getFileEncoding(): String = file_encoding
    fun getDelimiters(): String = delimiters
    fun getFontSize(): String = font_size
    fun getBgColor(): Int = bgcolor
    fun getSearchSelectionColor(): Int = searchSelectionColor
    fun getTextSelectionColor(): Int = textSelectionColor
    fun getFontColor(): Int = fontcolor
    fun getLastFilename(): String = last_filename
    fun getFont(): String = font
    fun getLanguage(): String = language

    fun setLegacyFilePicker(value: Boolean) {
        legacy_file_picker = value
    }

    fun setLegacyFilePicker(value: Boolean, context: Context) {
        setSettingValue(SETTING_LEGASY_FILE_PICKER, value, context)
        legacy_file_picker = value
    }

    fun setFontSize(fontSize: String, context: Context) {
        setSettingValue(SETTING_FONT_SIZE, fontSize, context)
        font_size = fontSize
    }

    fun setBgColor(bgcolor: Int, context: Context) {
        setSettingValue(SETTING_BG_COLOR, bgcolor, context)
        this.bgcolor = bgcolor
    }

    fun setFontColor(fontcolor: Int, context: Context) {
        setSettingValue(SETTING_FONT_COLOR, fontcolor, context)
        this.fontcolor = fontcolor
    }

    fun setTextSelectionColor(color: Int, context: Context) {
        setSettingValue(SETTING_TEXT_SELECTION_COLOR, color, context)
        this.textSelectionColor = color
    }

    fun setSearchSelectionColor(color: Int, context: Context) {
        setSettingValue(SETTING_SEARCH_SELECTION_COLOR, color, context)
        this.searchSelectionColor = color
    }

    fun setLastFilename(value: String, context: Context) {
        setSettingValue(SETTING_LAST_FILENAME, value, context)
        last_filename = value
    }

    fun setFont(value: String, context: Context) {
        setSettingValue(SETTING_FONT, value, context)
        font = value
    }

    fun applyLocale(context: Context) {
        val lang = getLanguage()
        if (lang == "") return
        val locale2 = Locale(lang)
        Locale.setDefault(locale2)
        val config2 = Configuration()
        @Suppress("DEPRECATION")
        config2.locale = locale2
        context.resources.updateConfiguration(config2, null)
    }

    fun isAlternativeFileAccess(): Boolean = alternative_file_access
    fun isThemeForced(): Boolean = COLOR_THEME_DARK == colorThemeType || COLOR_THEME_LIGHT == colorThemeType

    fun isCustomTheme(): Boolean {
        if (fontcolor != DEFAULT_TEXT_COLOR || bgcolor != DEFAULT_BACKGROUND_COLOR) return true
        return COLOR_THEME_CUSTOM == colorThemeType
    }

    fun getColorThemeType(): String = colorThemeType
    fun useWakeLock(): Boolean = useWakeLock
    fun isUseSimpleScrolling(): Boolean = useSimpleScrolling
}
