package com.example.calculatorsafe.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.example.calculatorsafe.data.Album
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

object EncryptionUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"

    fun getAlbumsDir(context: Context): File {
        return File(context.filesDir, "Albums")
    }

    fun encryptImage(bitmap: Bitmap?): ByteArray {
        val secretKey = KeystoreUtils.getOrCreateGlobalKey()
        if (bitmap == null) {
            Log.e("Encryption", "Bitmap is null")
        }

        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap?.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()

        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val encryptedData = cipher.doFinal(byteArray)

        return iv + encryptedData
    }

    fun saveEncryptedImageToStorage(
        context: Context,
        encryptedImage: ByteArray,
        targetAlbum: Album?,
        originalFileName: String,
        mimeType: String
    ): String {
        val albumsDir = getAlbumsDir(context)
        if (!albumsDir.exists()) {
            albumsDir.mkdirs() // Create the albums directory if it doesn't exist
        }

        val albumDir = File(albumsDir, targetAlbum?.name ?: "default")
        if (!albumDir.exists()) {
            albumDir.mkdirs() // Create the album directory if it doesn't exist
        }

        // Generate a unique file name
        val fileName = "IMG_${System.currentTimeMillis()}.enc"
        val file = File(albumDir, fileName)

        FileOutputStream(file).use {
            it.write(encryptedImage)
        }

        //saveMetadata(targetAlbum?.name ?: "default", originalFileName, fileName, mimeType)

        return file.absolutePath
    }

    fun decryptImage(file: File): Bitmap? {
        val secretKey = KeystoreUtils.getOrCreateGlobalKey()
        val iv = ByteArray(16) // 16 bytes for the IV

        // Read the IV
        file.inputStream().use { inputStream ->
            val bytesRead = inputStream.read(iv)
            if (bytesRead != 16) {
                throw IllegalArgumentException("Unable to read IV, bytes read: $bytesRead")
            }
        }

        // Read the encrypted data
        val encryptedData = file.inputStream().use { inputStream ->
            inputStream.skip(16) // Skip the IV
            inputStream.readBytes()
        }

        // Log encrypted data size for debugging
        Log.e("Decryption", "Encrypted data size: ${encryptedData.size}")

        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        val ivSpec = IvParameterSpec(iv)

        // Initialize cipher
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        try {
            // Perform decryption
            val decryptedData = cipher.doFinal(encryptedData)

            // Check if decrypted data is empty
            if (decryptedData.isEmpty()) {
                throw IllegalArgumentException("Decrypted data is empty.")
            }

            // Return the bitmap
            return BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.size)
        } catch (e: Exception) {
            Log.e("Decryption", "Error during decryption: ${e.message}")
            return null
        }
    }


    fun getBitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        try {
            // Open an InputStream to the content URI
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.use {
                // Decode the bitmap from the input stream
                return BitmapFactory.decodeStream(it)
            }
        } catch (e: FileNotFoundException) {
            Log.e("ImageDecoder", "File not found for URI: $uri", e)
        } catch (e: IOException) {
            Log.e("ImageDecoder", "I/O error while reading URI: $uri", e)
        } catch (e: Exception) {
            Log.e("ImageDecoder", "Error loading image from URI: $uri", e)
        }
        return null
    }


    private fun saveBitmapToFile(bitmap: Bitmap?, newFileName: String, context: Context): File? {
        return try {
            val tempFile = File(context.cacheDir, newFileName)
            val outputStream = FileOutputStream(tempFile)
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, outputStream) // Save as JPEG with 100% quality
            outputStream.flush()
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    fun restorePhotoToDevice(file: File, context: Context): Boolean {
        val decryptedBitmap = decryptImage(file)
        val restoredFile = saveBitmapToFile(decryptedBitmap,"restored_img_${System.currentTimeMillis()}.jpg", context) ?: return false
        try {
            val fileName = "${System.currentTimeMillis()}_${file.name}"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Restored")
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it).use { outputStream ->
                    restoredFile.inputStream().copyTo(outputStream!!)
                }
                file.delete() // Delete the encrypted file
                return true // Photo restored successfully
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false // Restoration failed
    }

    fun restorePhotos(selectedFiles: List<File>, context: Context) {
        selectedFiles.forEach { file ->
            val success = restorePhotoToDevice(file, context)
            if (!success) {
                Toast.makeText(context, "Failed to restore: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
        Toast.makeText(context, "Photos restored successfully!", Toast.LENGTH_SHORT).show()
    }


}