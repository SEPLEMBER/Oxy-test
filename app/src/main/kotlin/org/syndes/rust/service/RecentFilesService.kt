package org.syndes.rust.service

import android.content.Context
import java.io.*
import java.util.*

class RecentFilesService {

    companion object {
        const val MAX_ELEMENTS_STORED = 10
        const val MAX_ELEMENTS_SHOWN = 5
        const val RECENT_FILES_FILENAME = "recent_files_filename"
    }

    private var items: ArrayList<String>? = null

    fun addRecentFile(url: String, context: Context) {
        loadItems(context)
        // preserve original behavior: add at end, then trim duplicates/oldest
        items?.add(url)
        removeOldestItems(url)
        saveRecentFiles(items ?: ArrayList(), context)
    }

    private fun removeOldestItems(url: String) {
        val current = items ?: ArrayList()
        val newItems = ArrayList<String>()
        var counter = 0
        val lastIndex = current.size
        val skip = current.size - MAX_ELEMENTS_STORED
        for (item in current) {
            counter++
            if (counter != lastIndex && item == url) {
                continue
            }
            if (counter <= skip) continue
            newItems.add(item)
        }
        items = newItems
    }

    private fun loadItems(context: Context) {
        if (items == null) {
            items = loadRecentFiles(context)
        }
    }

    fun getLastFiles(skip: Int, context: Context): ArrayList<String> {
        loadItems(context)
        val result = ArrayList<String>()
        val it = (items ?: ArrayList()).listIterator((items ?: ArrayList()).size)
        var counter = 0
        while (it.hasPrevious()) {
            val name = it.previous()
            counter++
            if (counter <= skip) continue
            if (counter > MAX_ELEMENTS_SHOWN + 1) continue
            result.add(name)
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadRecentFiles(context: Context): ArrayList<String> {
        val history = ArrayList<String>()
        try {
            val fis: FileInputStream = context.applicationContext.openFileInput(RECENT_FILES_FILENAME)
            ObjectInputStream(fis).use { objectIn ->
                val obj = objectIn.readObject()
                if (obj is ArrayList<*>) {
                    for (o in obj) {
                        if (o is String) history.add(o)
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            // ok, no recent files yet
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return history
    }

    private fun saveRecentFiles(recentFiles: ArrayList<String>, context: Context) {
        try {
            val fos: FileOutputStream = context.openFileOutput(RECENT_FILES_FILENAME, Context.MODE_PRIVATE)
            ObjectOutputStream(fos).use { objectOut ->
                objectOut.writeObject(recentFiles)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
