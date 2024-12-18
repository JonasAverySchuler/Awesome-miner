package com.example.calculatorsafe

import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.calculatorsafe.EncryptionUtils.generateAESKey
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AlbumActivity : AppCompatActivity() {

    private val REQUEST_CODE_READ_MEDIA = 1001
    private val permissions = arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    private lateinit var albumId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)
        val albumName = intent.getStringExtra("albumName")
        albumId = intent.getStringExtra("keystoreAlias") ?: ""
        val albumDir = File(getExternalFilesDir(null), albumName)
        val key = getKeyForAlbum(albumId) // Implement key retrieval
        generateAndStoreKey(albumId)

        val photos = albumDir.listFiles()?.mapNotNull {
            retrieveDecryptedPhoto(it, key ?: return@mapNotNull null)
        } ?: emptyList()


        val albumRecyclerView = findViewById<RecyclerView>(R.id.album_RecyclerView)
        val albumFab = findViewById<FloatingActionButton>(R.id.album_fab)
        albumFab.setOnClickListener {
            checkAndRequestPermissions()
            }
        }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        //add if below a certain API level to check for different permission names
        if (ContextCompat.checkSelfPermission(this, READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(READ_MEDIA_IMAGES)
        }

        if (ContextCompat.checkSelfPermission(this, READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(READ_MEDIA_VIDEO)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), REQUEST_CODE_READ_MEDIA)
        } else {
            // Permissions are already granted
            accessUserImages()
        }

    }

    fun retrieveDecryptedPhoto(file: File, key: SecretKey): Bitmap? {
        val inputStream = file.inputStream()
        val encryptedBytes = inputStream.readBytes()
        val decryptedBytes = EncryptionUtils.decrypt(encryptedBytes, key)
        return BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
    }

    fun saveEncryptedPhoto(file: File, photoBitmap: Bitmap, key: SecretKey) {
        val outputStream = file.outputStream()
        val byteArrayOutputStream = ByteArrayOutputStream()
        photoBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val photoBytes = byteArrayOutputStream.toByteArray()
        val encryptedBytes = EncryptionUtils.encrypt(photoBytes, key)
        outputStream.write(encryptedBytes)
        outputStream.close()
    }

    private fun generateAndStoreKey(albumId: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(albumId, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }


    private fun getKeyForAlbum(albumId: String): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var key = keyStore.getKey(albumId, null) as? SecretKey

        if (key == null) {
            // Key does not exist, generate a new one
            key = generateAndStoreKey(albumId)
        }

        return key ?: throw IllegalStateException("Key generation failed")
    }

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val mediaUri: Uri? = result.data?.data
            mediaUri?.let {
                // Handle the selected image or video URI
                //handleSelectedMedia(contentResolver,it)
                handleImageUri(it)
            }
        }
    }

    private fun accessUserImages() {
        // Your code to pick images or videos from the gallery
        val pickMediaIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/* video/*"
        }
        pickMediaLauncher.launch(pickMediaIntent)
    }

    private fun handleSelectedMedia(contentResolver: ContentResolver, mediaUri: Uri) {
        val key = generateAESKey()
        //val bitmap = getBitmapFromUri(contentResolver, mediaUri)
        //val encryptedImage = encryptImage(bitmap, key)
        //val encryptedImagePath = saveEncryptedImageToStorage(encryptedImage)
        //deleteImageFromUri(mediaUri)
        //addImageToAlbum(encryptedImagePath)
    }

    private fun handleImageUri(uri: Uri) {
        val secretKey = getKeyForAlbum(albumId) // Retrieve your encryption key
        val byteArray = getByteArrayFromUri(contentResolver, uri)
        if (byteArray != null) {
            val encryptedPhoto = encryptPhoto(byteArray, secretKey)
            if (encryptedPhoto != null) {
                saveEncryptedPhoto(this, encryptedPhoto, "photo_${System.currentTimeMillis()}.png")
                //deleteOriginalPhoto(contentResolver, uri)

                // Refresh your RecyclerView
                //loadPhotos()
            }
        }
    }

    fun getByteArrayFromUri(contentResolver: ContentResolver, uri: Uri): ByteArray? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                ByteArrayOutputStream().use { byteArrayOutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
                    byteArrayOutputStream.toByteArray()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun encryptPhoto(byteArray: ByteArray, secretKey: SecretKey): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedBytes = cipher.doFinal(byteArray)
            encryptedBytes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveEncryptedPhoto(context: Context, encryptedPhoto: ByteArray, fileName: String) {
        try {
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(encryptedPhoto)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun decryptPhoto(encryptedPhoto: ByteArray, secretKey: SecretKey): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decryptedBytes = cipher.doFinal(encryptedPhoto)
            decryptedBytes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadAndDecryptPhoto(context: Context, fileName: String, secretKey: SecretKey): Bitmap? {
        return try {
            val file = File(context.filesDir, fileName)
            val encryptedPhoto = file.readBytes()
            val decryptedBytes = decryptPhoto(encryptedPhoto, secretKey)
            decryptedBytes?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    class PhotoAdapter(private val photos: List<Bitmap>) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

        inner class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val photoImageView: ImageView = view.findViewById(R.id.photo_image_view)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            holder.photoImageView.setImageBitmap(photos[position])
        }

        override fun getItemCount(): Int = photos.size
    }

}