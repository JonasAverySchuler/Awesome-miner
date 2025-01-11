package com.example.calculatorsafe.utils

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import com.example.calculatorsafe.adapters.MediaItemWrapper
import com.example.calculatorsafe.data.Album
import com.example.calculatorsafe.helpers.PreferenceHelper
import com.example.calculatorsafe.utils.EncryptionUtils.getBitmapFromUri
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

    fun getFileType(file: File): MediaItemWrapper? {
        val fileExtension = file.extension.lowercase()

        // Use MimeTypeMap to get MIME type
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)

        return when {
            mimeType?.startsWith("image") == true -> {
                // It's an image
                MediaItemWrapper.Image(file.absolutePath) // Create a media item for image
            }
            mimeType?.startsWith("video") == true -> {
                // It's a video
                MediaItemWrapper.Video(file.toUri()) // Create a media item for video
            }
            else -> null
        }
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

    fun accessUserImages(pickMediaLauncher: ActivityResultLauncher<Intent>) {
        // Using ACTION_OPEN_DOCUMENT for better control over file selection
        val pickMediaIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*" // You can adjust to select specific file types
            putExtra(Intent.EXTRA_LOCAL_ONLY, true) // Limit to local storage only
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // Allow multiple file selection
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        pickMediaLauncher.launch(pickMediaIntent)
    }

    //Returns filepath of new file
    fun handleSelectedMedia(context: Context, mediaUri: Uri, targetAlbum: Album, manageStoragePermissionLauncher: ActivityResultLauncher<Intent>): String {
        try {
            // Step 1: Get Bitmap from URI
            val contentResolver = context.contentResolver
            val bitmap = getBitmapFromUri(contentResolver, mediaUri)
            if (bitmap == null) {
                Log.e("MediaHandler", "Failed to get bitmap from URI: $mediaUri")
                return ""
            }

            // Step 2: Encrypt the Image
            val encryptedImage = EncryptionUtils.encryptImage(bitmap)

            // Step 3: Retrieve File Name and MIME Type
            val originalFileName = getFileNameFromUri(context, mediaUri) ?: "unknown_${System.currentTimeMillis()}.jpg"
            val mimeType = contentResolver.getType(mediaUri) ?: "image/jpeg"

            // Step 4: Save the Encrypted Image
            val newFilePath = EncryptionUtils.saveEncryptedImageToStorage(context, encryptedImage,targetAlbum, originalFileName, mimeType)

            Log.d("MediaHandler", "MediaUri: $mediaUri")

            val filePath = getFilePathFromUri(context, mediaUri) ?: ""
            Log.d("MediaHandler", "File Path from getfilepathfromuri: $filePath")

            if (PreferenceHelper.getDeleteOriginal(context)) {
                // Permission granted, proceed with your file operations
                Log.d("Permission", "Permission granted")
                if(!FileUtils.deleteFile(filePath)) {
                    Log.e("MediaHandler", "In content scheme : Failed to delete original media at URI: $mediaUri")
                }
            }
            return newFilePath
        } catch (e: Exception) {
            Log.e("MediaHandler", "Error handling selected media: ${e.message}", e)
            // Optionally show an error message to the user
            Toast.makeText(context, "Failed to process the selected media.", Toast.LENGTH_SHORT).show()
            return ""
        }
    }

    fun generateAlbumId(): String {
        return UUID.randomUUID().toString()
    }
}