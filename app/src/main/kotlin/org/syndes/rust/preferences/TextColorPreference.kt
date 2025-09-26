package org.syndes.rust.preferences

import android.content.Context
import android.util.AttributeSet
import org.syndes.rust.R
import org.syndes.rust.service.SettingsService

@Suppress("DEPRECATION")
class TextColorPreference(context: Context, attrs: AttributeSet) : ColorPreference(context, attrs) {
    init {
        titleText = context.resources.getString(R.string.Choose_a_font_color)
    }

    override fun saveColor(color: Int) {
        settingsService.setFontColor(color, context)
    }

    override fun initColor() {
        color = settingsService.getFontColor()
    }

    override fun getDefaultColor(): Int = SettingsService.DEFAULT_TEXT_COLOR
}
