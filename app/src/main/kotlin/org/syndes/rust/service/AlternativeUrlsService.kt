package org.syndes.rust.service

import android.content.Context
import android.net.Uri
import java.io.*
import java.util.*

class AlternativeUrlsService {
    companion object {
        const val ALTERNATIVE_URLS_FILENAME = "alternative_urls"
    }

    private var storage: TreeMap<String, String>? = null

    private fun loadItems(context: Context) {
        if (storage == null) {
            storage = loadAlternativeUrl(context)
        }
    }

    fun hasAlternativeUrl(uri: Uri, context: Context): Boolean {
        loadItems(context)
        val uriString = uri.toString()
        return storage?.containsKey(uriString) ?: false
    }

    fun getAlternativeUrl(driveUri: Uri, context: Context): Uri? {
        loadItems(context)
        val uriString = driveUri.toString()
        val mapped = storage?.get(uriString) ?: return null
        return Uri.parse(mapped)
    }

    fun addAlternativeUrl(driveUri: Uri, mediaUrl: Uri, context: Context) {
        loadItems(context)
        storage?.put(driveUri.toString(), mediaUrl.toString())
        saveAlternativeUrls(storage ?: TreeMap(), context)
    }

    fun clearAlternativeUrls(context: Context) {
        storage = TreeMap()
        saveAlternativeUrls(storage ?: TreeMap(), context)
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadAlternativeUrl(context: Context): TreeMap<String, String> {
        val alternativeUris = TreeMap<String, String>()
        try {
            val fis: FileInputStream = context.applicationContext.openFileInput(ALTERNATIVE_URLS_FILENAME)
            ObjectInputStream(fis).use { objectIn ->
                val obj = objectIn.readObject()
                if (obj is TreeMap<*, *>) {
                    for ((k, v) in obj) {
                        if (k is String && v is String) {
                            alternativeUris[k] = v
                        }
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            // none yet
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return alternativeUris
    }

    private fun saveAlternativeUrls(alternativeUrls: TreeMap<String, String>, context: Context) {
        try {
            val fos: FileOutputStream = context.openFileOutput(ALTERNATIVE_URLS_FILENAME, Context.MODE_PRIVATE)
            ObjectOutputStream(fos).use { objectOut ->
                objectOut.writeObject(alternativeUrls)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
