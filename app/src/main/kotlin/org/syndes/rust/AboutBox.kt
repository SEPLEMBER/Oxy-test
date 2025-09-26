package org.syndes.rust

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.preference.DialogPreference
import android.text.SpannableString
import android.text.util.Linkify
import android.util.AttributeSet
import android.view.View
import android.widget.TextView

@Suppress("DEPRECATION")
class AboutBox(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    override fun onCreateDialogView(): View {
        val s = SpannableString(l(R.string.about_message))
        Linkify.addLinks(s, Linkify.WEB_URLS)
        val view = TextView(context)
        view.text = s
        val padding = 32
        view.setPadding(padding, padding, padding, padding)
        view.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        return view
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        builder.setTitle(R.string.About)
        builder.setPositiveButton(R.string.Continue) { _: DialogInterface, _: Int -> }
        builder.setNegativeButton(null as String?, null as DialogInterface.OnClickListener?)
    }

    private fun l(id: Int): String = context.resources.getString(id)
}
