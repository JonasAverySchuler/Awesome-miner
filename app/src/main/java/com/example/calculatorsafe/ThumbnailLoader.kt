package com.example.calculatorsafe

import android.graphics.Bitmap
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailLoader {
    fun loadThumbnailAsync(album: MainActivity.Album, imageView: ImageView, callback: (ImageView, Bitmap?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firstImagePath = File(album.pathString).listFiles()?.firstOrNull()?.absolutePath ?: return@launch callback(imageView, null)
                val decryptedBitmap = EncryptionUtils.decryptImage(File(firstImagePath))
                withContext(Dispatchers.Main) {
                    callback(imageView, decryptedBitmap)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(imageView,null)
                }
            }
        }
    }
}
