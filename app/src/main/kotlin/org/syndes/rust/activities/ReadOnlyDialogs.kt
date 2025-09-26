package org.syndes.rust.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.TextView
import org.syndes.rust.R

/**
 * Helper functions extracted from EditorActivity for read-only & alternative-file dialogs.
 */

/**
 * Show read-only dialog (keeps same behavior as original).
 */
fun showReadOnlyDialog(context: Context) {
    val spannableMessage = SpannableString(context.getString(R.string.readOnlyDialogMessage) + " " + context.getString(R.string.readOnlyDialogClickHere))
    val clickText = context.getString(R.string.readOnlyDialogClickHere)
    val clickableSpan = object : ClickableSpan() {
        override fun onClick(widget: View) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://texteditor.maxistar.me/faq/"))
            context.startActivity(browserIntent)
        }
    }

    val start = spannableMessage.length - clickText.length
    val end = spannableMessage.length
    spannableMessage.setSpan(clickableSpan, start, end, 0)
    spannableMessage.setSpan(UnderlineSpan(), start, end, 0)

    val builder = AlertDialog.Builder(context)
    builder.setTitle(R.string.readOnlyDialogTitle)
    builder.setMessage(spannableMessage)
    builder.setCancelable(true)
    builder.setPositiveButton(R.string.readOnlyDialogButtonOpenAgain) { _, _ ->
        if (context is EditorActivity) {
            context.openFile()
        }
    }
    builder.setNegativeButton(R.string.readOnlyDialogButtonContinue) { dialog, _ -> dialog.dismiss() }
    val dialog = builder.create()
    dialog.show()
    (dialog.findViewById(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
}

/**
 * Show alternative-file dialog. `onResult` will be called with true if user chose "Yes" (open alternative location).
 */
fun showAlternativeFileDialog(activity: EditorActivity, uri: Uri, onResult: (Boolean) -> Unit) {
    AlertDialog.Builder(activity)
        .setTitle(R.string.AlternativeFileAccessTitle)
        .setMessage(R.string.SelectAlternativeLocationForFile)
        .setNegativeButton(R.string.Yes) { _, _ ->
            activity.lastTriedSystemUri = uri
            onResult(true)
        }
        .setPositiveButton(R.string.No) { _, _ ->
            activity.lastTriedSystemUri = null
            onResult(false)
        }
        .setOnCancelListener {
            activity.lastTriedSystemUri = null
            activity.onBackPressed()
        }
        .create()
        .show()
}
