package org.syndes.rust

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

/**
 * MainActivity — простой хост: Toolbar + большое поле ввода.
 * Все действия в меню пока заглушки (показывают Toast).
 *
 * Дальше: сюда будем подключать ActivityResultLaunchers и ViewModel.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var editorView: EditText
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)

        editorView = findViewById(R.id.edittext_main)
        editorView.hint = getString(R.string.hint_editor)

        // Пример: как получить текст из поля
        // val currentText = editorView.text.toString()
    }

    // Inflate меню
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    // Обработка нажатий в меню — сейчас заглушки
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open_file -> {
                showStub("Открыть файл")
                // TODO: вызвать OpenDocument launcher и передать uri в ViewModel
                return true
            }
            R.id.action_open_multiple -> {
                showStub("Открыть несколько файлов")
                // TODO: вызвать OpenMultipleDocuments
                return true
            }
            R.id.action_save_as -> {
                showStub("Сохранить как")
                // TODO: CreateDocument -> saveAs
                return true
            }
            R.id.action_open_folder -> {
                showStub("Выбрать папку (replace)")
                // TODO: OpenDocumentTree -> выбрать рабочую папку
                return true
            }
            R.id.action_search_replace -> {
                showStub("Поиск / Замена")
                // TODO: открыть bottom sheet / диалог поиска
                return true
            }
            R.id.action_compare -> {
                showStub("Сравнить файлы")
                // TODO: выбрать два файла и показать diff view
                return true
            }
            R.id.action_settings -> {
                showStub("Настройки")
                // TODO: открыть настройки (может быть Fragment)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun showStub(name: String) {
        Toast.makeText(this, "Заглушка: $name", Toast.LENGTH_SHORT).show()
    }
}
