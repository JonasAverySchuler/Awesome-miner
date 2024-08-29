package com.example.calculatorsafe

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {
    private val REQUEST_CODE_READ_MEDIA = 1001
    private lateinit var albumsDir: File
    companion object{

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fab: FloatingActionButton = findViewById(R.id.fab)
        val mainRecyclerView: RecyclerView = findViewById(R.id.main_RecyclerView)


        albumsDir = File(this.filesDir, "Albums")
        if (!albumsDir.exists()) {
            albumsDir.mkdirs() // Create albums directory if it doesn't exist
        }

        mainRecyclerView.layoutManager = LinearLayoutManager(this)
        val albums = getAlbums() // Fetch albums and photo count
        mainRecyclerView.adapter = AlbumAdapter(albums)


        fab.setOnClickListener {  // Register ActivityResult handler
            // Register your observer here
            checkAndRequestPermissions()
        }


        val fileNames = getAllEncryptedFileNames(this)
        fileNames.forEach {
            Log.e("filenames", it)
        }


        if (!PreferenceHelper.isPasscodeSet(this)) {
            // Passcode not set, navigate to passcode setup activity
            val intent = Intent(this, PasscodeSetupActivity::class.java)
            //startActivity(intent)
           // finish() // Optionally finish the main activity to prevent going back
        } else {
            Toast.makeText(this, "Passcode has been set before", Toast.LENGTH_SHORT).show()
            // Passcode already set, proceed with normal flow
            //show calculatavtivity and let t hem enter code
        }
    }

    private fun createAlbum(albumName: String) {

        val albumDir = File(albumsDir, albumName)
        if (!albumDir.exists()) {
            albumDir.mkdirs()
        }
    }


    fun getAlbums(): List<Album> {
        val albumDirs = albumsDir.listFiles { file -> file.isDirectory } ?: return emptyList()
        return albumDirs.map { dir ->
            val photoCount = dir.listFiles()?.size ?: 0
            Album(dir.name, photoCount)
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

    // Handle the permissions request result
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_READ_MEDIA -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission granted, proceed with your logic
                    accessUserImages()
                } else {
                    // Permission denied, show a message to the user
                    showPermissionDeniedMessage()
                }
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

    private fun showPermissionDeniedMessage() {
        // Show a message to the user explaining why the permission is needed
        Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
        //show dialog telling user permissions are required and to enable them to access features

    }

    private fun generateAESKey(): ByteArray {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(128) // 128-bit key size
        return keyGen.generateKey().encoded
    }

    private fun encryptImage(bitmap: Bitmap, key: ByteArray): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()

        val secretKeySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
        return cipher.doFinal(byteArray)
    }

    fun getAllEncryptedFileNames(context: Context): Array<String> {
        return context.fileList()
    }

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val mediaUri: Uri? = result.data?.data
            mediaUri?.let {
                // Handle the selected image or video URI
                handleSelectedMedia(it)
            }
        }
    }

    private fun saveEncryptedImageToStorage(encryptedImage: ByteArray): String {
        val file = File(getExternalFilesDir(null), "encrypted_image.png")
        FileOutputStream(file).use {
            it.write(encryptedImage)
        }
        return file.absolutePath
    }
    // Function to decrypt an image file
    fun decryptImage(context: Context, fileName: String, key: ByteArray): Bitmap? {
        try {
            // Read encrypted file from internal storage
            val fis = context.openFileInput(fileName)
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            val secretKey = SecretKeySpec(key, "AES")
            val iv = ByteArray(cipher.blockSize)
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val cis = CipherInputStream(fis, cipher)

            // Decrypt and decode image file
            val decryptedBitmap = BitmapFactory.decodeStream(cis)

            fis.close()
            return decryptedBitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun deleteImageFromUri(uri: Uri) {
        contentResolver.delete(uri, null, null)
    }

    private fun handleSelectedMedia(mediaUri: Uri) {
        val key = generateAESKey()
        val bitmap = getBitmapFromUri(mediaUri)
        val encryptedImage = encryptImage(bitmap, key)
        val encryptedImagePath = saveEncryptedImageToStorage(encryptedImage)
        //deleteImageFromUri(mediaUri)
        //addImageToAlbum(encryptedImagePath)
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source)
    }

    private fun createAlbumDirectory(context: Context, albumName: String): File {
        val albumDirectory = File(context.filesDir, "albums/$albumName")
        if (!albumDirectory.exists()) {
            albumDirectory.mkdirs()
        }
        return albumDirectory
    }

    class AlbumAdapter(private val albums: List<Album>) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

        class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val albumThumbnail: ImageView = view.findViewById(R.id.album_thumbnail)
            val albumName: TextView = view.findViewById(R.id.album_name)
            val photoCount: TextView = view.findViewById(R.id.photo_count)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
            return AlbumViewHolder(view)
        }

        override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
            val album = albums[position]
            // Set album thumbnail (if applicable), name, and photo count
            holder.albumName.text = album.name
            holder.photoCount.text = "${album.photoCount} photos"
            // Set a placeholder or real image if available
        }

        override fun getItemCount(): Int = albums.size
    }


    data class MediaItem(val id: Long, val uri: Uri)
    data class Album(val name: String, val photoCount: Int)
}