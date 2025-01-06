package com.example.calculatorsafe.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.net.URLConnection
import java.util.UUID

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

    fun getFilePathFromUri(context: Context, mediaUri: Uri): String? {
        var filePath: String? = null

        // Check if URI is a Document URI
        if (mediaUri.authority == "com.android.providers.media.documents") {
            val docId = mediaUri.lastPathSegment
            val split = docId?.split(":")
            if (split != null && split.size > 1) {
                val type = split[0]
                val id = split[1]

                // Query the MediaStore based on the document type and ID
                val contentUri = when (type) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri("external")
                }

                // Build the selection clause and selection args
                val selection = "${MediaStore.MediaColumns._ID} = ?"
                val selectionArgs = arrayOf(id)

                // Query MediaStore to get the file path
                val cursor: Cursor? = context.contentResolver.query(contentUri, arrayOf(MediaStore.MediaColumns.DATA), selection, selectionArgs, null)

                cursor?.let {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndex(MediaStore.MediaColumns.DATA)
                        filePath = it.getString(columnIndex)
                    }
                    it.close()
                }
            }
        }

        return filePath
    }

    fun deleteFile(filePath: String): Boolean {
        val file = File(filePath)

        return if (file.exists() && file.canWrite()) {
            // Attempt to delete the file
            val isDeleted = file.delete()
            if (isDeleted) {
                Log.d("FileDelete", "File deleted successfully: $filePath")
                true
            } else {
                Log.e("FileDelete", "Failed to delete file: $filePath")
                false
            }
        } else {
            Log.e("FileDelete", "File does not exist or is not writable: $filePath")
            false
        }
    }

    fun generateAlbumId(): String {
        return UUID.randomUUID().toString()
    }
}