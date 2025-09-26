package org.syndes.rust

import android.content.SearchRecentSuggestionsProvider

class SearchSuggestions : SearchRecentSuggestionsProvider() {
    companion object {
        const val AUTHORITY = "com.maxistar.authority"
        const val MODE = DATABASE_MODE_QUERIES
    }

    init {
        setupSuggestions(AUTHORITY, MODE)
    }
}
