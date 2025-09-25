package org.syndes.rust

import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * SettingsActivity
 *
 * Layout expected:
 * - Switch @+id/switch_disable_screenshots
 * - Button @+id/btn_toggle_theme
 *
 * Uses private SharedPreferences "rust_prefs" (MODE_PRIVATE).
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "rust_prefs"
        private const val KEY_DISABLE_SCREENSHOTS = "disable_screenshots"
        private const val KEY_DARK_THEME = "dark_theme"
    }

    private lateinit var switchDisableScreenshots: Switch
    private lateinit var btnToggleTheme: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        switchDisableScreenshots = findViewById(R.id.switch_disable_screenshots)
        btnToggleTheme = findViewById(R.id.btn_toggle_theme)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Установим текущее состояние без вызова listener
        val isDisabled = prefs.getBoolean(KEY_DISABLE_SCREENSHOTS, false)
        switchDisableScreenshots.isChecked = isDisabled

        // Теперь безопасно подписываемся на изменения
        switchDisableScreenshots.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DISABLE_SCREENSHOTS, isChecked).apply()
            Toast.makeText(
                this,
                if (isChecked) "Скриншоты запрещены" else "Скриншоты разрешены",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnToggleTheme.setOnClickListener {
            val cur = prefs.getBoolean(KEY_DARK_THEME, false)
            prefs.edit().putBoolean(KEY_DARK_THEME, !cur).apply()
            Toast.makeText(
                this,
                if (!cur) "Темная тема включена" else "Светлая тема включена",
                Toast.LENGTH_SHORT
            ).show()
            // При необходимости: применить тему прямо сейчас или пересоздать Activity:
            // recreate()
        }
    }
}
