package com.appnovastudios.calculatorsafe

import android.graphics.Bitmap
import android.widget.ImageView
import com.appnovastudios.calculatorsafe.utils.EncryptionUtils
import com.example.calculatorsafe.data.Album
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailLoader {
    fun loadThumbnailAsync(album: Album, imageView: ImageView, callback: (ImageView, Bitmap?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val albumDir = File(album.pathString)
                val encryptedFiles = albumDir.listFiles()?.filter { !it.name.endsWith(".json") } ?: emptyList() //sort out metadata files
                // If there are no encrypted images, return early
                val firstImagePath = encryptedFiles.firstOrNull()?.absolutePath ?: return@launch callback(imageView, null)
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
