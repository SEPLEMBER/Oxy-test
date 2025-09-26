package org.syndes.rust.preferences

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.syndes.rust.R
import org.syndes.rust.ServiceLocator
import org.syndes.rust.TPStrings
import org.syndes.rust.service.SettingsService
import java.util.*
import androidx.annotation.NonNull

@Suppress("DEPRECATION")
class FontTypePreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    private var selected = 0
    private val settingsService: SettingsService = ServiceLocator.getInstance().getSettingsService(context)

    init {
        val font = settingsService.getFont()
        selected = when (font) {
            TPStrings.FONT_SERIF -> 1
            TPStrings.FONT_SANS_SERIF -> 2
            else -> 0
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        builder.setTitle(R.string.Choose_a_font_type)
        builder.setPositiveButton(R.string.OK) { _, _ ->
            when (selected) {
                0 -> settingsService.setFont(TPStrings.FONT_MONOSPACE, context)
                1 -> settingsService.setFont(TPStrings.FONT_SERIF, context)
                else -> settingsService.setFont(TPStrings.FONT_SANS_SERIF, context)
            }
            notifyChanged()
        }
        builder.setNegativeButton(R.string.Cancel) { _, _ -> }

        val arrayOfFonts = arrayOf(TPStrings.FONT_MONOSPACE, TPStrings.FONT_SERIF, TPStrings.FONT_SANS_SERIF)
        val fonts: List<String> = Arrays.asList(*arrayOfFonts)
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
                TPStrings.FONT_SERIF -> tv.typeface = Typeface.SERIF
                TPStrings.FONT_SANS_SERIF -> tv.typeface = Typeface.SANS_SERIF
                TPStrings.FONT_MONOSPACE -> tv.typeface = Typeface.MONOSPACE
            }
            tv.setTextColor(android.graphics.Color.BLACK)
            tv.setPadding(10, 3, 3, 3)
            return v
        }
    }
}
