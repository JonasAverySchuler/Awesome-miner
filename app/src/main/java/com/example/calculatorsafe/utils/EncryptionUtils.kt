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
import com.example.calculatorsafe.data.Album
import com.example.calculatorsafe.data.FileDetail
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
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

    fun saveEncryptedImageToStorage(context: Context, encryptedImage: ByteArray, targetAlbum: Album?, encryptedFileName: String, ): String {
        val albumsDir = getAlbumsDir(context)
        if (!albumsDir.exists()) {
            albumsDir.mkdirs() // Create the albums directory if it doesn't exist
        }

        val albumDir = File(albumsDir, targetAlbum?.name ?: "default")
        if (!albumDir.exists()) {
            albumDir.mkdirs() // Create the album directory if it doesn't exist
        }

        val file = File(albumDir, encryptedFileName)

        FileOutputStream(file).use {
            it.write(encryptedImage)
        }

        return file.absolutePath
    }

    fun decryptFile(file: File): File? {
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

        // Initialize the cipher
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        try {
            // Perform decryption
            val decryptedData = cipher.doFinal(encryptedData)

            // Check if decrypted data is empty
            if (decryptedData.isEmpty()) {
                throw IllegalArgumentException("Decrypted data is empty.")
            }

            // Create a temporary file for the decrypted content
            val decryptedFile = File(file.parent, "decrypted_${file.nameWithoutExtension}")
            decryptedFile.writeBytes(decryptedData)

            return decryptedFile
        } catch (e: Exception) {
            Log.e("Decryption", "Error during decryption: ${e.message}")
            return null
        }
    }

    suspend fun decryptImage(file: File, downscale: Boolean = true): Bitmap? = withContext(Dispatchers.IO) {
        val secretKey = KeystoreUtils.getOrCreateGlobalKey()
        val iv = ByteArray(16) // 16 bytes for the IV

        // Temporary file for decrypted data
        val decryptedFile = File.createTempFile("decrypted_", ".tmp", file.parentFile).apply {
            deleteOnExit() // Ensures the file is deleted when the JVM exits
        }

        try {
            // Read the IV
            file.inputStream().use { inputStream ->
                val bytesRead = inputStream.read(iv)
                if (bytesRead != 16) {
                    throw IllegalArgumentException("Unable to read IV, bytes read: $bytesRead")
                }
            }

            // Set up the cipher for decryption
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            // Use a CipherInputStream to decrypt in chunks and write to a file
            FileOutputStream(decryptedFile).use { outputStream ->
                val buffer = ByteArray(16 * 1024) // 16 KB buffer
                file.inputStream().use { inputStream ->
                    inputStream.skip(16) // Skip the IV
                    val cipherInputStream = CipherInputStream(inputStream, cipher)

                    var bytesRead: Int
                    while (cipherInputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }

            // Once the file is decrypted, load it as a Bitmap
            return@withContext loadBitmapFromFile(decryptedFile, downscale)

        } catch (e: Exception) {
            Log.e("Decryption", "Error during decryption: ${e.message}")
            null
        } finally {
            if (decryptedFile.exists()) {
                decryptedFile.delete()
            }
        }
    }

    private fun loadBitmapFromFile(file: File, downscale: Boolean): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        // Decode bounds first to get the image size
        BitmapFactory.decodeFile(file.absolutePath, options)

        // Optionally downscale the image to avoid OOM issues
        if (downscale) {
            options.inSampleSize = calculateInSampleSize(options, maxWidth = 200, maxHeight = 200)
        }
        options.inJustDecodeBounds = false

        // Decode and return the actual Bitmap
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, maxWidth: Int, maxHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 4

        if (height > maxHeight || width > maxWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2
            // and keeps both height and width larger than the requested max dimensions.
            while (halfHeight / inSampleSize >= maxHeight && halfWidth / inSampleSize >= maxWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
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

    suspend fun restorePhotoToDevice(file: File, context: Context): Boolean {
        val decryptedBitmap = decryptImage(file)
        val restoredFile = saveBitmapToFile(decryptedBitmap, "restored_img_${System.currentTimeMillis()}.jpg", context) ?: return false

        try {
            // Get the metadata for the album directory
            val albumDir = File(file.parent!!) //Parent is the album directory so we are certain it exists so it is safe to assert this here
            val metadataFile = File(albumDir, "metadata.json")

            // Read and update metadata
            val gson = Gson()
            val metadata = if (metadataFile.exists()) {
                gson.fromJson(metadataFile.readText(), FileUtils.Metadata::class.java)
            } else {
                FileUtils.Metadata(albumName = albumDir.name, files = emptyList<FileDetail>().toMutableList())
            }

            // Find the file in the metadata and retrieve the original name
            val fileDetail = metadata.files.find { it.encryptedFileName == file.name }
            val originalFileName = fileDetail?.originalFileName ?: "${System.currentTimeMillis()}.jpg"

            // Step 1: Name the restored file with the original file name
            val restoredFileName = "$originalFileName.jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, restoredFileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Restored")
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it).use { outputStream ->
                    restoredFile.inputStream().copyTo(outputStream!!)
                }

                // Step 2: Delete the encrypted file
                file.delete()

                // Step 3: Remove the file from metadata and save
                val updatedFiles = metadata.files.filterNot { it.encryptedFileName == file.name }
                metadata.files = updatedFiles.toMutableList()

                // Save the updated metadata
                metadataFile.writeText(gson.toJson(metadata))

                return true // Photo restored successfully
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false // Restoration failed
    }

}