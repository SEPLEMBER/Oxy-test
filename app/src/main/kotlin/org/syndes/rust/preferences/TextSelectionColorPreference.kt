package org.syndes.rust.preferences

import android.content.Context
import android.util.AttributeSet
import org.syndes.rust.R
import org.syndes.rust.service.SettingsService

@Suppress("DEPRECATION")
class TextSelectionColorPreference(context: Context, attrs: AttributeSet) : ColorPreference(context, attrs) {
    init {
        titleText = context.resources.getString(R.string.preferenceChooseTextSelectionColor)
    }

    override fun saveColor(color: Int) {
        settingsService.setTextSelectionColor(color, context)
    }

    override fun initColor() {
        color = settingsService.getTextSelectionColor()
    }

    override fun getDefaultColor(): Int = SettingsService.DEFAULT_TEXT_SELECTION_COLOR
}
