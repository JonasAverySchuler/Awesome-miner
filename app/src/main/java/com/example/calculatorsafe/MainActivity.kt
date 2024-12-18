package com.example.calculatorsafe

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.example.calculatorsafe.EncryptionUtils.generateAESKey
import com.example.calculatorsafe.EncryptionUtils.getBitmapFromUri
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.w3c.dom.Document
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLConnection
import java.security.Key
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class MainActivity : AppCompatActivity() {
    private val REQUEST_CODE_READ_MEDIA = 1001
    private lateinit var albumsDir: File
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var albums: MutableList<Album>
    private var targetAlbum: Album? = null

    companion object{
        private val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fab: FloatingActionButton = findViewById(R.id.fab)
        val mainRecyclerView: RecyclerView = findViewById(R.id.main_RecyclerView)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        albumsDir = File(this.filesDir, "Albums")
        if (!albumsDir.exists()) {
            albumsDir.mkdirs() // Create albums directory if it doesn't exist
        }

        mainRecyclerView.layoutManager = LinearLayoutManager(this)
        albums = getAlbums(this).toMutableList() // Fetch albums and photo count
        albumAdapter = AlbumAdapter(albums) { album ->
            openAlbum(album)
        }
        mainRecyclerView.adapter = albumAdapter


        fab.setOnClickListener {  // Register ActivityResult handler
            // Register your observer here
            openAlbumSelector(this)
            //checkAndRequestPermissions()
        }


        if (!PreferenceHelper.isPasscodeSet(this)) {
            // Passcode not set, navigate to passcode setup activity
            val intent = Intent(this, PasscodeSetupActivity::class.java)
            //startActivity(intent)
           // finish() // Optionally finish the main activity to prevent going back
        } else {
            Toast.makeText(this, "Passcode has been set before", Toast.LENGTH_SHORT).show()
            // Passcode already set, proceed with normal flow
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_album -> {
                // Handle "New Album" action
                createNewAlbum(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openAlbumSelector(context: Context) {
        val albums = getAlbums(context).toMutableList() // Replace with your method to fetch album names
        val albumNames = albums.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Choose an Album")
            .setItems(albumNames) { _, which ->
                targetAlbum = albums[which]
                checkAndRequestPermissions()
                //openMediaPicker(selectedAlbum) // Pass the selected album
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getAlbumPath(albumName: String): String {
        val albumDir = File(albumsDir, albumName)

        // Ensure the album directory exists
        if (!albumDir.exists()) {
            albumDir.mkdirs() // Create directory if it doesn't exist
        }

        return albumDir.absolutePath
    }

    private fun openAlbum(album: Album) {
        val intent = Intent(this, AlbumActivity::class.java)
        intent.putExtra("albumName", album.name)
        intent.putExtra("keystoreAlias", album.albumID)
        startActivity(intent)
    }

    private fun createNewAlbum(context: Context) {
        // Logic for creating a new album (e.g., show a dialog to enter album name)
        showNewAlbumDialog(context)
    }

    private fun showNewAlbumDialog(context: Context) {
        val editText = EditText(this)
        editText.hint = "Enter album name"

        val dialog = AlertDialog.Builder(this)
            .setTitle("New Album")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val albumName = editText.text.toString().trim()
                if (albumName.isNotEmpty()) {
                    createAlbum(context,albumName)
                    // Refresh the RecyclerView
                    albumAdapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(this, "Album name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun createAlbum(context: Context,albumName: String) {
        val albumDir = File(albumsDir, albumName)
        if (!albumDir.exists()) {
            albumDir.mkdirs()
            // Add new album to the list and update RecyclerView
            val albumId = generateAlbumId() //returning empty string for now
            generateAndStoreKey(context, albumId)
            saveAlbumMetadata(context, albumName, albumId)
            val newAlbum = Album(albumName, 0, albumId)
            albumAdapter.addAlbum(newAlbum) // Implement this method in your adapter
        }
    }

    fun loadAlbum(context: Context, albumName: String) {
        //val key = getKeyForAlbum(context, albumName)
        // Use the key to decrypt and display photos
    }

    fun saveAlbumMetadata(context: Context, albumName: String, albumId: String) {
        val prefs = context.getSharedPreferences("album_metadata", Context.MODE_PRIVATE)
        prefs.edit().putString(albumName, albumId).apply()
    }

    fun getAlbumId(context: Context, albumName: String): String? {
        val prefs = context.getSharedPreferences("album_metadata", Context.MODE_PRIVATE)
        return prefs.getString(albumName, null)
    }

    fun getKeyForAlbum(context: Context, albumName: String): SecretKey? {
        val albumId = getAlbumId(context, albumName) ?: return null
        return getKey(context, albumId)
    }

    fun getKey(context: Context, albumId: String): SecretKey? {
        return try {
            // Initialize the KeyStore
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

            // Retrieve the key from the KeyStore using the alias
            val key = keyStore.getKey(albumId, null)

            // Return the key if it's an instance of SecretKey
            if (key is SecretKey) key else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateAlbumName(context: Context, oldAlbumName: String, newAlbumName: String) {
        val albumId = getAlbumId(context, oldAlbumName) ?: return
        saveAlbumMetadata(context, newAlbumName, albumId)
        // Optionally remove old metadata
        val prefs = context.getSharedPreferences("album_metadata", Context.MODE_PRIVATE)
        prefs.edit().remove(oldAlbumName).apply()
    }

    fun generateAndStoreKey(context: Context, albumId: String) {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenSpec = KeyGenParameterSpec.Builder(albumId, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGen.init(keyGenSpec)
        keyGen.generateKey()
    }

    fun getAlbums(context: Context): List<Album> {
        val albumDirs = albumsDir.listFiles { file -> file.isDirectory } ?: return emptyList()
        return albumDirs.map { dir ->
            val photoCount = (getImageFileCountFromAlbum(dir))
            Album(dir.name, photoCount, getAlbumId(context, dir.name) ?: "")
        }
    }

    fun generateAlbumId(): String {
        return UUID.randomUUID().toString()
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

    private fun saveEncryptedImageToStorage(encryptedImage: ByteArray, targetAlbum: Album?, originalFileName: String, mimeType: String): String {
        Log.e("MainActivity", "SaveEncryptedImageToStorage called")

        val albumDir = File(getAlbumPath(targetAlbum?.name ?: "default"))
        if (!albumDir.exists()) {
            albumDir.mkdirs() // Create the album directory if it doesn't exist
        }

        // Generate a unique file name
        val fileName = "IMG_${System.currentTimeMillis()}.enc"
        val file = File(albumDir, fileName)

        FileOutputStream(file).use {
            it.write(encryptedImage)
        }

        Log.e("MainActivity", "Saving encrypted image to: ${file.absolutePath}")
        saveMetadata(targetAlbum?.name ?: "default", originalFileName, fileName, mimeType)


        return file.absolutePath
    }

    private fun saveMetadata(albumName: String, originalFileName: String, encryptedFileName: String, mimeType: String) {
        val metadataFile = File(getAlbumPath(albumName), "album_metadata.xml")
        val document: Document = if (metadataFile.exists()) {
            // Parse existing metadata
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(metadataFile)
        } else {
            // Create a new document if none exists
            DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().apply {
                appendChild(createElement("album").apply { setAttribute("name", albumName) })
            }
        }

        val albumElement = document.documentElement

        val newImageElement = document.createElement("image").apply {
            appendChild(document.createElement("originalFileName").apply { textContent = originalFileName })
            appendChild(document.createElement("encryptedFileName").apply { textContent = encryptedFileName })
            appendChild(document.createElement("mimeType").apply { textContent = mimeType })
        }
        albumElement.appendChild(newImageElement)

        // Save back to XML file
        TransformerFactory.newInstance().newTransformer().apply {
            transform(DOMSource(document), StreamResult(FileOutputStream(metadataFile)))
        }
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
        val bitmap = getBitmapFromUri(contentResolver, mediaUri)
        val encryptedImage = encryptImage(bitmap, key)

        val originalFileName = getFileNameFromUri(this, mediaUri) ?: "unknown"
        val mimeType = contentResolver.getType(mediaUri) ?: "unknown"

        val encryptedImagePath = saveEncryptedImageToStorage(encryptedImage, targetAlbum, originalFileName, mimeType)
        updatePhotoCount(targetAlbum)
        //deleteImageFromUri(mediaUri)
    }

    private fun updatePhotoCount(album: Album?) {
        album?.let {
            // Get the current count of image files in the album directory
            val albumDir = File(getAlbumPath(album.name)) // Get the path of the album
            val imageCount = getImageFileCountFromAlbum(albumDir)
            Log.e("MainActivity", "Image count: $imageCount")
            // Increment the photo count
            it.photoCount = imageCount

            // Find the album in the list and notify adapter of the change
            val index = albums.indexOfFirst { it.albumID == album.albumID }
            if (index != -1) {
                albums[index] = it // Update the album in the list
                albumAdapter.notifyItemChanged(index) // Notify the adapter to refresh the item
            }
        }
    }

    fun getImageFileCountFromAlbum(albumDirectory: File): Int {
        Log.e("MainActivity", "getImageFileCountFromAlbum called" + albumDirectory.listFiles().size)
        val imageFiles = albumDirectory.listFiles()?.filter {
            // Check if the file is an image by its extension or MIME type or is an encoded file
            it.isFile && isImageFile(it) || it.name.endsWith(".enc", ignoreCase = true)
        } ?: emptyList()

        return imageFiles.size
    }

    fun isImageFile(file: File): Boolean {
        // Check if the file extension or MIME type is for an image
        val mimeType = getMimeType(file)
        return mimeType.startsWith("image") && !file.name.endsWith(".meta")  // exclude metadata files
    }

    fun getMimeType(file: File): String {
        // Logic to get MIME type (could be based on file extension or using a MIME detection library)
        return URLConnection.guessContentTypeFromName(file.name) ?: "unknown"
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        Log.e("MainActivity", "getFileNameFromUri called")
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


    class AlbumAdapter(
        private val albums: MutableList<Album>,
        private val onAlbumClick: (Album) -> Unit
    ) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

        inner class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val albumThumbnail: ImageView = view.findViewById(R.id.album_thumbnail)
            val albumName: TextView = view.findViewById(R.id.album_name)
            val photoCount: TextView = view.findViewById(R.id.photo_count)

            init {
                view.setOnClickListener {
                    onAlbumClick(albums[adapterPosition])
                }
            }
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

        fun addAlbum(album: Album) {
            albums.add(album)
            notifyItemInserted(albums.size - 1)
        }
    }

    data class Album(val name: String, var photoCount: Int, val albumID: String)
}