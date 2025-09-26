package org.syndes.rust.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import org.syndes.rust.R
import org.syndes.rust.ServiceLocator
import org.syndes.rust.TPStrings
import org.syndes.rust.service.AlternativeUrlsService
import org.syndes.rust.service.SettingsService
import java.nio.charset.Charset
import java.util.*

@Suppress("DEPRECATION")
class SettingsActivity : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var mVersion: Preference? = null

    private lateinit var settingsService: SettingsService
    private lateinit var alternativeUrlsService: AlternativeUrlsService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsService = ServiceLocator.getInstance().getSettingsService(applicationContext)
        alternativeUrlsService = ServiceLocator.getInstance().getAlternativeUrlsService()
        settingsService.applyLocale(baseContext)

        addPreferencesFromResource(R.xml.preferences)

        if (hideLegacyPicker()) {
            val legacyPicker = findPreference(SettingsService.SETTING_LEGASY_FILE_PICKER)
            legacyPicker?.isEnabled = false
        }

        mVersion = findPreference(TPStrings.VERSION_NAME)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            mVersion?.summary = pInfo.versionName
        } catch (e: NameNotFoundException) {
            e.printStackTrace()
        }

        val encoding = findPreference(SettingsService.SETTING_FILE_ENCODING) as? ListPreference

        val entries = ArrayList<CharSequence>()
        val entryValues = ArrayList<CharSequence>()

        val avmap: Map<String, Charset> = Charset.availableCharsets()
        for ((_, cs) in avmap) {
            entries.add(cs.name())
            entryValues.add(cs.displayName())
        }

        encoding?.entries = entries.toTypedArray()
        encoding?.entryValues = entryValues.toTypedArray()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val value = sharedPreferences.getString(SettingsService.SETTING_COLOR_THEME_TYPE, SettingsService.COLOR_THEME_EMPTY)
        val colorPreference = findPreference(SettingsService.SETTING_FONT_COLOR)
        colorPreference?.isEnabled = SettingsService.COLOR_THEME_CUSTOM == value
        val backgroundColorPreference = findPreference(SettingsService.SETTING_BG_COLOR)
        backgroundColorPreference?.isEnabled = SettingsService.COLOR_THEME_CUSTOM == value

        initAlternativeLocations()
    }

    private fun initAlternativeLocations() {
        val resetAlternativeLocations = findPreference("reset_alternative_file_paths")
        val useAlternativeLocations = findPreference("use_alternative_file_access")

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            resetAlternativeLocations?.isEnabled = false
            useAlternativeLocations?.isEnabled = false
        }

        resetAlternativeLocations?.isEnabled = settingsService.isAlternativeFileAccess()
        resetAlternativeLocations?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            resetAlternativeLocations()
            false
        }

        useAlternativeLocations?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            resetAlternativeLocations?.isEnabled = settingsService.isAlternativeFileAccess()
            true
        }
    }

    private fun resetAlternativeLocations() {
        AlertDialog.Builder(this)
            .setTitle(R.string.AlternativeFileAccessTitle)
            .setMessage(R.string.ResetAlternativeFileLocations)
            .setPositiveButton(R.string.Yes) { _, _ ->
                alternativeUrlsService.clearAlternativeUrls(applicationContext)
            }
            .create()
            .show()
    }

    private fun hideLegacyPicker(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return true
        }
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.P
    }

    override fun onResume() {
        super.onResume()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)
        settingsService.reloadSettings(baseContext)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (SettingsService.SETTING_LANGUAGE == key ||
            SettingsService.SETTING_FONT == key ||
            SettingsService.SETTING_BG_COLOR == key ||
            SettingsService.SETTING_FONT_COLOR == key ||
            SettingsService.SETTING_FONT_SIZE == key ||
            SettingsService.SETTING_USE_SIMPLE_SCROLLING == key
        ) {
            val lang = sharedPreferences.getString(SettingsService.SETTING_LANGUAGE, TPStrings.EN) ?: TPStrings.EN
            setLocale(lang)
            SettingsService.setLanguageChangedFlag()
        }
        if (SettingsService.SETTING_COLOR_THEME_TYPE == key) {
            val value = sharedPreferences.getString(SettingsService.SETTING_COLOR_THEME_TYPE, SettingsService.COLOR_THEME_EMPTY)
            val colorPreference = findPreference(SettingsService.SETTING_FONT_COLOR)
            colorPreference?.isEnabled = SettingsService.COLOR_THEME_CUSTOM == value
            val backgroundColorPreference = findPreference(SettingsService.SETTING_BG_COLOR)
            backgroundColorPreference?.isEnabled = SettingsService.COLOR_THEME_CUSTOM == value
        }
        settingsService.reloadSettings(applicationContext)
    }

    @Suppress("DEPRECATION")
    fun setLocale(lang: String) {
        val locale2 = Locale(lang)
        Locale.setDefault(locale2)
        val config2 = Configuration()
        // legacy code kept to preserve original behavior
        config2.locale = locale2
        baseContext.resources.updateConfiguration(config2, null)
        showPreferences()
    }

    protected fun showPreferences() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }
}
