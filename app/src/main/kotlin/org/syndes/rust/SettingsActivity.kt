package org.syndes.rust

import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.syndes.rust.data.SecurePrefsManager

/**
 * SettingsActivity
 *
 * Layout expected:
 * - Switch @+id/switch_disable_screenshots
 * - Button @+id/btn_toggle_theme (or other UI)
 *
 * This activity uses SecurePrefsManager (init in Application).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var switchDisableScreenshots: Switch
    private lateinit var btnToggleTheme: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        switchDisableScreenshots = findViewById(R.id.switch_disable_screenshots)
        btnToggleTheme = findViewById(R.id.btn_toggle_theme)

        // initialize UI from SecurePrefsManager
        switchDisableScreenshots.isChecked = SecurePrefsManager.isDisableScreenshots()

        switchDisableScreenshots.setOnCheckedChangeListener { _, isChecked ->
            SecurePrefsManager.setDisableScreenshots(isChecked)
            Toast.makeText(this, if (isChecked) "Скриншоты запрещены" else "Скриншоты разрешены", Toast.LENGTH_SHORT).show()
        }

        btnToggleTheme.setOnClickListener {
            // simple toggle demo: store in same prefs under "dark_theme"
            val prefs = getSharedPreferences("rust_prefs", MODE_PRIVATE)
            val cur = prefs.getBoolean("dark_theme", false)
            prefs.edit().putBoolean("dark_theme", !cur).apply()
            Toast.makeText(this, if (!cur) "Темная тема включена" else "Светлая тема включена", Toast.LENGTH_SHORT).show()
            // You may want to recreate activities or apply theme immediately
        }
    }
}
