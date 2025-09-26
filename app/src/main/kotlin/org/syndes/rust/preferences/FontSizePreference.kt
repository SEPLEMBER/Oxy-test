package org.syndes.rust.preferences

import android.app.AlertDialog
import android.content.Context
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.syndes.rust.R
import org.syndes.rust.ServiceLocator
import org.syndes.rust.service.SettingsService
import java.util.*
import androidx.annotation.NonNull

@Suppress("DEPRECATION")
class FontSizePreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    private var selected = 2
    private val settingsService: SettingsService = ServiceLocator.getInstance().getSettingsService(context)

    init {
        val fontSize = settingsService.getFontSize()
        selected = when (fontSize) {
            SettingsService.SETTING_EXTRA_SMALL -> 0
            SettingsService.SETTING_SMALL -> 1
            SettingsService.SETTING_MEDIUM -> 2
            SettingsService.SETTING_LARGE -> 3
            SettingsService.SETTING_HUGE -> 4
            else -> 2
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        builder.setTitle(R.string.Choose_a_font_type)
        builder.setPositiveButton(R.string.OK) { _, _ ->
            when (selected) {
                0 -> settingsService.setFontSize(SettingsService.SETTING_EXTRA_SMALL, context)
                1 -> settingsService.setFontSize(SettingsService.SETTING_SMALL, context)
                2 -> settingsService.setFontSize(SettingsService.SETTING_MEDIUM, context)
                3 -> settingsService.setFontSize(SettingsService.SETTING_LARGE, context)
                4 -> settingsService.setFontSize(SettingsService.SETTING_HUGE, context)
            }
            notifyChanged()
        }
        builder.setNegativeButton(R.string.Cancel, null)

        val arrayOfFonts = arrayOf(
            SettingsService.SETTING_EXTRA_SMALL,
            SettingsService.SETTING_SMALL,
            SettingsService.SETTING_MEDIUM,
            SettingsService.SETTING_LARGE,
            SettingsService.SETTING_HUGE
        )
        val fonts = Arrays.asList(*arrayOfFonts)
        val adapter = FontTypeArrayAdapter(context, android.R.layout.simple_list_item_single_choice, fonts)
        builder.setSingleChoiceItems(adapter, selected) { _, which -> selected = which }
    }

    class FontTypeArrayAdapter(context: Context, resource: Int, objects: List<String>) :
        ArrayAdapter<String>(context, resource, objects) {

        @NonNull
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getView(position, convertView, parent)
            val tv = v as TextView
            val option = tv.text.toString()
            when (option) {
                SettingsService.SETTING_EXTRA_SMALL -> tv.textSize = 12.0f
                SettingsService.SETTING_SMALL -> tv.textSize = 16.0f
                SettingsService.SETTING_MEDIUM -> tv.textSize = 20.0f
                SettingsService.SETTING_LARGE -> tv.textSize = 24.0f
                SettingsService.SETTING_HUGE -> tv.textSize = 28.0f
            }
            tv.setTextColor(android.graphics.Color.BLACK)
            tv.setPadding(10, 3, 3, 3)
            return v
        }
    }
}
