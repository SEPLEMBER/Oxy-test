package org.syndes.rust.activities

import android.text.Editable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.SearchView
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Externalized Search listener previously inner class.
 *
 * Dependencies are passed in constructor to avoid tight coupling with EditorActivity internals.
 */
class QueryTextListener(
    private val selectionColor: Int,
    private val editText: EditText,
    private val scrollView: ScrollView?,
    private val linearLayout: LinearLayout?,
    private val simpleScrolling: () -> Boolean,
    private val requestFocus: () -> Unit,
    private val removeHighlight: () -> Unit
) : SearchView.OnQueryTextListener, MenuItem.OnActionExpandListener {

    private val span = BackgroundColorSpan(selectionColor)
    private val editable: Editable = editText.editableText
    private var matcher: Matcher? = null
    private var index: Int = 0
    private val height: Int

    init {
        height = if (simpleScrolling()) {
            linearLayout?.height ?: 0
        } else {
            scrollView?.height ?: 0
        }
    }

    override fun onQueryTextChange(newText: String): Boolean {
        if (newText.isEmpty()) {
            index = 0
            editable.removeSpan(span)
            return false
        }

        try {
            val escapedTextToFind = Pattern.quote(newText)
            val pattern = Pattern.compile(escapedTextToFind, Pattern.MULTILINE or Pattern.CASE_INSENSITIVE)
            matcher = pattern.matcher(editable)
        } catch (e: Exception) {
            return false
        }

        val m = matcher
        if (m != null && m.find(index)) {
            if (editText.layout == null) return false
            doSearch()
        } else {
            index = 0
        }
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        val m = matcher
        if (m != null) {
            if (m.find()) {
                if (editText.layout == null) return false
                doSearch()
            } else {
                Toast.makeText(editText.context, String.format(editText.context.getString(org.syndes.rust.R.string.s_not_found), query), Toast.LENGTH_SHORT).show()
                m.reset()
                index = 0
                editable.removeSpan(span)
            }
        }
        return true
    }

    private fun doSearch() {
        val m = matcher ?: return
        index = m.start()
        val line = editText.layout.getLineForOffset(index)
        val pos = editText.layout.getLineBaseline(line)
        if (simpleScrolling()) {
            editText.scrollTo(0, pos - height / 2)
        } else {
            scrollView?.smoothScrollTo(0, pos - height / 2)
        }
        editable.setSpan(span, m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean = true

    override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
        editable.removeSpan(span)
        requestFocus()
        // allow activity to drop reference
        removeHighlight()
        return true
    }
}
