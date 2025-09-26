package org.syndes.rust.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.NonNull

object FileNameHelper {

    fun getFilenameByUri(context: Context, uriString: String): String {
        val uri = Uri.parse(uriString)
        return getFilenameByUri(context, uri)
    }

    /**
     * found solution here
     * https://stackoverflow.com/questions/64224012/xamarin-plugin-filepicker-content-com-android-providers-downloads-documents-p
     */
    fun getFilenameByUri(context: Context, @NonNull uri: Uri): String {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                cursor.getString(index)
            } else {
                getLegacyFilenameByUri(uri)
            }
        } catch (e: Exception) {
            getFilenameByUriFallback(uri)
        } finally {
            try {
                if (cursor != null && !cursor.isClosed) {
                    cursor.close()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun getLegacyFilenameByUri(uri: Uri): String {
        val path = uri.path ?: return ""
        val paths = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (paths.isEmpty()) return ""
        return paths[paths.size - 1]
    }

    /**
     * @todo Move to external class and cover with test
     */
    private fun getFilenameByUriFallback(@NonNull uri: Uri): String {
        val path = uri.path ?: return ""
        val paths = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (paths.isEmpty()) return ""
        return paths[paths.size - 1]
    }
}
