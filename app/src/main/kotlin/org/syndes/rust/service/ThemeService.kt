package org.syndes.rust.service

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

class ThemeService(private val settingsService: SettingsService) {

    fun applyColorTheme(context: Context) {
        val theme = settingsService.getColorThemeType()
        if (SettingsService.COLOR_THEME_DARK == theme) {
            applyDarkTheme(context)
        }
        if (SettingsService.COLOR_THEME_LIGHT == theme) {
            applyLightTheme(context)
        }
    }

    private fun applyDarkTheme(context: Context) {
        val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            uiManager.enableCarMode(0)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            uiManager.nightMode = UiModeManager.MODE_NIGHT_YES
        }
    }

    private fun applyLightTheme(context: Context) {
        val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            uiManager.enableCarMode(0)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        } else {
            uiManager.nightMode = UiModeManager.MODE_NIGHT_NO
        }
    }
}
