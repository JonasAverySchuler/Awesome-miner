package com.example.calculatorsafe.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.net.URLConnection

object FileUtils {

    fun isImageFile(file: File): Boolean {
        // Check if the file extension or MIME type is for an image
        val mimeType = getMimeType(file)
        return mimeType.startsWith("image") && !file.name.endsWith(".meta")  // exclude metadata files
    }

    fun getMimeType(file: File): String {
        // Logic to get MIME type (could be based on file extension or using a MIME detection library)
        return URLConnection.guessContentTypeFromName(file.name) ?: "unknown"
    }

    fun getAlbumPath(albumsDir: File, albumName: String): String {
        val albumDir = File(albumsDir, albumName)

        // Ensure the album directory exists
        if (!albumDir.exists()) {
            albumDir.mkdirs() // Create directory if it doesn't exist
        }

        return albumDir.absolutePath
    }

    fun getImageFileCountFromAlbum(albumDirectory: File): Int {
        val imageFiles = albumDirectory.listFiles()?.filter {
            // Check if the file is an image by its extension or MIME type or is an encoded file
            it.isFile && isImageFile(it) || it.name.endsWith(".enc", ignoreCase = true)
        } ?: emptyList()

        return imageFiles.size
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && it.moveToFirst()) {
                return it.getString(nameIndex) // This is the original file name
            }
        }
        return null // Return null if the name cannot be found
    }

    fun deleteFile(file: File): Boolean {
        //TODO: delete .enc file and metadata perhaps as well as update recyclerview to display updated files
        return file.delete()
    }
}