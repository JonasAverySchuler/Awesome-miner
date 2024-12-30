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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class AlbumActivity : AppCompatActivity() {

    private val REQUEST_CODE_READ_MEDIA = 1001
    private val permissions = arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    private lateinit var albumId: String
    private lateinit var directoryPath: String
    private lateinit var recyclerViewAdapter: EncryptedImageAdapter

    companion object {
        private val TAG = "AlbumActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)
        val albumName = intent.getStringExtra("albumName")
        albumId = intent.getStringExtra("keystoreAlias") ?: ""
        directoryPath = intent.getStringExtra("directoryPath") ?: ""
        Log.e(TAG, "Album name: $albumName")
        Log.e(TAG, "Album ID: $albumId")
        Log.e(TAG, "Directory path: $directoryPath")

        val encryptedFiles = File(directoryPath).listFiles { _, name -> name.endsWith(".enc") }?.toList() ?: emptyList()

        val albumRecyclerView = findViewById<RecyclerView>(R.id.album_RecyclerView)
        albumRecyclerView.layoutManager = GridLayoutManager(this, 3)

        recyclerViewAdapter = EncryptedImageAdapter(encryptedFiles) { file ->
            decryptImage(file)
        }

        albumRecyclerView.adapter = recyclerViewAdapter

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

    fun decryptImage(file: File): Bitmap {
        val secretKey = KeystoreUtils.getOrCreateGlobalKey()
        val iv = ByteArray(16) // 16 bytes for the IV
        file.inputStream().use { inputStream ->
            val bytesRead = inputStream.read(iv)
            if (bytesRead != 16) {
                throw IllegalArgumentException("Unable to read IV, bytes read: $bytesRead")
            }
        }
        Log.e(TAG, "Decryption IV: ${iv.joinToString("") { "%02x".format(it) }}")

        val encryptedData = file.inputStream().use { inputStream ->
            inputStream.skip(16) // Skip the IV
            inputStream.readBytes()
        }
        Log.e(TAG, "Encrypted data size: ${encryptedData.size}")

        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        val decryptedData = cipher.doFinal(encryptedData) //crash here
        return BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.size)
    }

    fun getIV(file: File): ByteArray {
        val iv = ByteArray(16) // AES block size
        file.inputStream().use { inputStream ->
            val bytesRead = inputStream.read(iv)
            if (bytesRead != 16) {
                throw IllegalArgumentException("Invalid IV length: $bytesRead bytes")
            }
        }
        return iv
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

    class EncryptedImageAdapter(
        private val encryptedFiles: List<File>,
        private val decryptFunction: (File) -> Bitmap
        ) : RecyclerView.Adapter<EncryptedImageAdapter.PhotoViewHolder>() {

        inner class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val photoImageView: ImageView = view.findViewById(R.id.photo_image_view)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.grid_item_layout, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val encryptedFile = encryptedFiles[position]
            val decryptedBitmap = decryptFunction(encryptedFile)
            holder.photoImageView.setImageBitmap(decryptedBitmap)
            holder.itemView.setOnClickListener {
                val imagePaths = encryptedFiles.map { it.absolutePath }
                val intent = Intent(holder.itemView.context, MediaViewActivity::class.java)
                intent.putExtra("imagePaths", ArrayList(imagePaths))
                intent.putExtra("position", position)
                holder.itemView.context.startActivity(intent)
            }
        }

        override fun getItemCount(): Int = encryptedFiles.size
    }

}