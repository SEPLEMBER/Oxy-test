package org.syndes.rust.preferences

import android.content.Context
import android.util.AttributeSet
import org.syndes.rust.R
import org.syndes.rust.service.SettingsService

@Suppress("DEPRECATION")
class SearchSelectionColorPreference(context: Context, attrs: AttributeSet) : ColorPreference(context, attrs) {
    init {
        titleText = context.resources.getString(R.string.preferenceChooseSearchSelectionColor)
    }

    override fun saveColor(color: Int) {
        settingsService.setSearchSelectionColor(color, context)
    }

    override fun initColor() {
        color = settingsService.getSearchSelectionColor()
    }

    override fun getDefaultColor(): Int = SettingsService.DEFAULT_SEARCH_SELECTION_COLOR
}
