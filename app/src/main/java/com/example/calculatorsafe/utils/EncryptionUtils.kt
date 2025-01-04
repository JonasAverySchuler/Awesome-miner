package com.example.calculatorsafe.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import com.example.calculatorsafe.MainActivity.Album
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

object EncryptionUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"

    fun getAlbumsDir(context: Context): File {
        return File(context.filesDir, "Albums")
    }

    fun encryptImage(bitmap: Bitmap): ByteArray {
        val secretKey = KeystoreUtils.getOrCreateGlobalKey()

        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()

        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val encryptedData = cipher.doFinal(byteArray)

        return iv + encryptedData
    }

    fun saveEncryptedImageToStorage(
        encryptedImage: ByteArray,
        albumsDir: File,
        targetAlbum: Album?,
        originalFileName: String,
        mimeType: String
    ): String {
        Log.e("EncryptionUtils", "saveEncryptedImageToStorage")
        Log.e("EncryptionUtils", "albumsDir: $albumsDir")
        val albumDir = File(albumsDir, targetAlbum?.name ?: "default")
        Log.e("EncryptionUtils", "albumDir: $albumDir")
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

    fun decryptImage(file: File): Bitmap {
        val secretKey = KeystoreUtils.getOrCreateGlobalKey()
        val iv = ByteArray(16) // 16 bytes for the IV
        file.inputStream().use { inputStream ->
            val bytesRead = inputStream.read(iv)
            if (bytesRead != 16) {
                throw IllegalArgumentException("Unable to read IV, bytes read: $bytesRead")
            }
        }

        val encryptedData = file.inputStream().use { inputStream ->
            inputStream.skip(16) // Skip the IV
            inputStream.readBytes()
        }

        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        val decryptedData = cipher.doFinal(encryptedData)
        return BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.size)
    }

    fun getBitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source)
    }

    fun restoreImage(file: File): Bitmap {
        val secretKey = KeystoreUtils.getOrCreateGlobalKey()
        val iv = ByteArray(16) // 16 bytes for the IV
        //TODO: restore image to users gallery and delete encrypted file
        return BitmapFactory.decodeFile(file.absolutePath)
    }

}