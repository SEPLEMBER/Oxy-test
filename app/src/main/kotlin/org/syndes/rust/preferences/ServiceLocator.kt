package org.syndes.rust

import android.content.Context
import org.syndes.rust.service.*

class ServiceLocator private constructor() {

    private var settingsService: SettingsService? = null
    private var recentFilesService: RecentFilesService? = null
    private var alternativeUrlsService: AlternativeUrlsService? = null
    private var themeService: ThemeService? = null
    private var wakeLockService: WakeLockService? = null

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        @JvmStatic
        fun getInstance(): ServiceLocator {
            return instance ?: synchronized(this) {
                instance ?: ServiceLocator().also { instance = it }
            }
        }
    }

    fun getSettingsService(context: Context): SettingsService {
        if (settingsService == null) {
            settingsService = SettingsService()
            settingsService!!.loadSettings(context)
        }
        return settingsService!!
    }

    fun getRecentFilesService(): RecentFilesService {
        if (recentFilesService == null) {
            recentFilesService = RecentFilesService()
        }
        return recentFilesService!!
    }

    fun getAlternativeUrlsService(): AlternativeUrlsService {
        if (alternativeUrlsService == null) {
            alternativeUrlsService = AlternativeUrlsService()
        }
        return alternativeUrlsService!!
    }

    fun getThemeService(context: Context): ThemeService {
        if (themeService == null) {
            themeService = ThemeService(getSettingsService(context))
        }
        return themeService!!
    }

    fun getWakeLockService(): WakeLockService {
        if (wakeLockService == null) {
            wakeLockService = WakeLockService()
        }
        return wakeLockService!!
    }
}
