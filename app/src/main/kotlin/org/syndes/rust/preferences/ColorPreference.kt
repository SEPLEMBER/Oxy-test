package org.syndes.rust.preferences

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import org.syndes.rust.R
import org.syndes.rust.ServiceLocator
import org.syndes.rust.service.SettingsService
import java.util.*

@Suppress("DEPRECATION")
open class ColorPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {
    protected var color: Int = 0
    protected var attribute: String? = null
    protected var titleText: String? = null
    protected var settingsService: SettingsService = ServiceLocator.getInstance().getSettingsService(context)

    init {
        attribute = attrs.getAttributeValue(1)
        widgetLayoutResource = R.layout.colorpref
        initColor()
    }

    protected open fun saveColor(color: Int) {
        // to be overridden
    }

    protected open fun initColor() {
        // to be overridden
    }

    protected open fun getDefaultColor(): Int = 0

    override fun onBindView(view: View) {
        super.onBindView(view)
        val myView = view.findViewById<View?>(R.id.currentcolor) ?: return
        myView.setBackgroundColor(color)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        builder.setTitle(titleText)

        val factory = LayoutInflater.from(context)
        val colorView = factory.inflate(R.layout.colorpicker, null as ViewGroup?)
        val colormap = colorView.findViewById<ImageView>(R.id.colormap)
        val editText = colorView.findViewById<TextView>(R.id.textColor)
        val checkBox = colorView.findViewById<CheckBox>(R.id.defaultColorCheckBox)

        checkBox.isChecked = color == getDefaultColor()

        builder.setPositiveButton(R.string.OK) { _: DialogInterface, _: Int ->
            if (checkBox.isChecked) {
                saveColor(getDefaultColor())
                color = getDefaultColor()
            } else {
                saveColor(color)
            }
            notifyChanged()
        }

        builder.setNegativeButton(R.string.Cancel) { _, _ ->
            initColor()
        }

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                colormap.setBackgroundColor(getDefaultColor())
                editText.text = colorToText(getDefaultColor())
            } else {
                colormap.setBackgroundColor(color)
            }
        }

        colormap.setBackgroundColor(color)
        editText.text = colorToText(color)

        colormap.setOnTouchListener { v, event ->
            checkBox.isChecked = false
            val bd = colormap.drawable as? BitmapDrawable
            val bitmap = bd?.bitmap
            if (bitmap != null) {
                var x = ((event.x - 15) * bitmap.width / (colormap.width - 30)).toInt()
                var y = ((event.y - 15) * bitmap.height / (colormap.height - 30)).toInt()

                if (x >= bitmap.width) x = bitmap.width - 1
                if (x < 0) x = 0
                if (y >= bitmap.height) y = bitmap.height - 1
                if (y < 0) y = 0

                color = bitmap.getPixel(x, y)
                colormap.setBackgroundColor(color)
                editText.text = colorToText(color)
            }
            v.performClick()
            true
        }

        builder.setView(colorView)
    }

    private fun colorToText(color: Int): String {
        val red = Integer.toHexString(Color.red(color))
        val green = Integer.toHexString(Color.green(color))
        val blue = Integer.toHexString(Color.blue(color))
        return String.format(Locale.getDefault(), "#%s%s%s",
            if (red.length < 2) "0$red" else red,
            if (green.length < 2) "0$green" else green,
            if (blue.length < 2) "0$blue" else blue
        )
    }
}
