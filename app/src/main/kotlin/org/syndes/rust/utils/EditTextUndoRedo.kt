package org.syndes.rust.utils

import android.app.Activity
import android.content.SharedPreferences
import android.text.Editable
import android.text.Selection
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.UnderlineSpan
import android.widget.TextView
import java.util.*

/**
 * Port of the original Java EditTextUndoRedo to Kotlin, preserving behavior.
 *
 * Usage:
 *   val undoRedo = EditTextUndoRedo(myTextView, myActivity)
 */
class EditTextUndoRedo(private val mTextView: TextView, activity: Activity) {

    /**
     * Is undo/redo being performed?
     */
    private var mIsUndoOrRedo = false

    /**
     * The edit history.
     */
    private val mEditHistory = EditHistory()

    /**
     * The change listener.
     */
    private val mChangeListener = EditTextChangeListener(activity)

    init {
        mTextView.addTextChangedListener(mChangeListener)
    }

    fun disconnect() {
        mTextView.removeTextChangedListener(mChangeListener)
    }

    fun setMaxHistorySize(maxHistorySize: Int) {
        mEditHistory.setMaxHistorySize(maxHistorySize)
    }

    fun clearHistory() {
        mEditHistory.clear()
    }

    fun getCanUndo(): Boolean {
        return (mEditHistory.mmPosition > 0)
    }

    fun undo() {
        val edit = mEditHistory.getPrevious() ?: return

        val text = mTextView.editableText
        val start = edit.mmStart
        val end = start + (edit.mmAfter?.length ?: 0)

        mIsUndoOrRedo = true
        text.replace(start, end, edit.mmBefore)
        mIsUndoOrRedo = false

        // remove underlines inserted by suggestions
        val spans = text.getSpans(0, text.length, UnderlineSpan::class.java)
        for (o in spans) {
            text.removeSpan(o)
        }

        Selection.setSelection(text, if (edit.mmBefore.isNullOrEmpty()) start else start + edit.mmBefore.length)
    }

    fun getCanRedo(): Boolean {
        return (mEditHistory.mmPosition < mEditHistory.mmHistory.size)
    }

    fun redo() {
        val edit = mEditHistory.getNext() ?: return

        val text = mTextView.editableText
        val start = edit.mmStart
        val end = start + (edit.mmBefore?.length ?: 0)

        mIsUndoOrRedo = true
        text.replace(start, end, edit.mmAfter)
        mIsUndoOrRedo = false

        val spans = text.getSpans(0, text.length, UnderlineSpan::class.java)
        for (o in spans) {
            text.removeSpan(o)
        }

        Selection.setSelection(text, if (edit.mmAfter.isNullOrEmpty()) start else start + edit.mmAfter.length)
    }

    fun storePersistentState(editor: SharedPreferences.Editor, prefix: String) {
        editor.putString("$prefix.hash", mTextView.text.toString().hashCode().toString())
        editor.putInt("$prefix.maxSize", mEditHistory.mmMaxHistorySize)
        editor.putInt("$prefix.position", mEditHistory.mmPosition)
        editor.putInt("$prefix.size", mEditHistory.mmHistory.size)

        var i = 0
        for (ei in mEditHistory.mmHistory) {
            val pre = "$prefix.$i"
            editor.putInt("$pre.start", ei.mmStart)
            editor.putString("$pre.before", ei.mmBefore.toString())
            editor.putString("$pre.after", ei.mmAfter.toString())
            i++
        }
    }

    @Throws(IllegalStateException::class)
    fun restorePersistentState(sp: SharedPreferences, prefix: String): Boolean {
        val ok = doRestorePersistentState(sp, prefix)
        if (!ok) {
            mEditHistory.clear()
        }
        return ok
    }

    private fun doRestorePersistentState(sp: SharedPreferences, prefix: String): Boolean {
        val hash = sp.getString("$prefix.hash", null) ?: return true
        if (hash.toInt() != mTextView.text.toString().hashCode()) {
            return false
        }

        mEditHistory.clear()
        mEditHistory.mmMaxHistorySize = sp.getInt("$prefix.maxSize", -1)
        val count = sp.getInt("$prefix.size", -1)
        if (count == -1) return false

        for (i in 0 until count) {
            val pre = "$prefix.$i"
            val start = sp.getInt("$pre.start", -1)
            val before = sp.getString("$pre.before", null)
            val after = sp.getString("$pre.after", null)
            if (start == -1 || before == null || after == null) return false
            mEditHistory.add(EditItem(start, before, after))
        }
        mEditHistory.mmPosition = sp.getInt("$prefix.position", -1)
        return mEditHistory.mmPosition != -1
    }

    // ========================================================= //

    private class EditHistory {
        var mmPosition = 0
        var mmMaxHistorySize = -1
        val mmHistory: LinkedList<EditItem> = LinkedList()

        fun clear() {
            mmPosition = 0
            mmHistory.clear()
        }

        fun add(item: EditItem) {
            while (mmHistory.size > mmPosition) {
                mmHistory.removeLast()
            }
            mmHistory.add(item)
            mmPosition++
            if (mmMaxHistorySize >= 0) trimHistory()
        }

        fun setMaxHistorySize(maxHistorySize: Int) {
            mmMaxHistorySize = maxHistorySize
            if (mmMaxHistorySize >= 0) trimHistory()
        }

        private fun trimHistory() {
            while (mmHistory.size > mmMaxHistorySize) {
                mmHistory.removeFirst()
                mmPosition--
            }
            if (mmPosition < 0) mmPosition = 0
        }

        fun getPrevious(): EditItem? {
            if (mmPosition == 0) return null
            mmPosition--
            return mmHistory[mmPosition]
        }

        fun getNext(): EditItem? {
            if (mmPosition >= mmHistory.size) return null
            val item = mmHistory[mmPosition]
            mmPosition++
            return item
        }
    }

    private class EditItem(val mmStart: Int, val mmBefore: CharSequence, val mmAfter: CharSequence)

    private inner class EditTextChangeListener(private val mActivity: Activity) : TextWatcher {

        private var mBeforeChange: CharSequence? = null

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (mIsUndoOrRedo) return
            mBeforeChange = s.subSequence(start, start + count)
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            mActivity.invalidateOptionsMenu()
            if (mIsUndoOrRedo) return
            val mAfterChange = s.subSequence(start, start + count)
            mEditHistory.add(EditItem(start, mBeforeChange ?: "", mAfterChange))
        }

        override fun afterTextChanged(s: Editable) {}
    }
}
