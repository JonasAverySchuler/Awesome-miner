package com.example.calculatorsafe

import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.calculatorsafe.PreferenceHelper.getAlbumId
import com.example.calculatorsafe.PreferenceHelper.saveAlbumMetadata
import com.example.calculatorsafe.ThumbnailLoader.loadThumbnailAsync
import com.example.calculatorsafe.utils.EncryptionUtils
import com.example.calculatorsafe.utils.EncryptionUtils.getBitmapFromUri
import com.example.calculatorsafe.utils.EncryptionUtils.saveEncryptedImageToStorage
import com.example.calculatorsafe.utils.FileUtils
import com.example.calculatorsafe.utils.FileUtils.getAlbumPath
import com.example.calculatorsafe.utils.FileUtils.getImageFileCountFromAlbum
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.w3c.dom.Document
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
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
    private lateinit var albumActivityResultLauncher: ActivityResultLauncher<Intent>

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
        toolbar.overflowIcon?.setTint(resources.getColor(R.color.white, theme))

        albumsDir = File(this.filesDir, "Albums")
        if (!albumsDir.exists()) {
            albumsDir.mkdirs() // Create albums directory if it doesn't exist
        }

        mainRecyclerView.layoutManager = LinearLayoutManager(this)
        albums = getAlbums(this).toMutableList()

        //TODO: add lambda to handle album option click, show a dialog to rename or delete album
        albumAdapter = AlbumAdapter(albums,
            onAlbumClick = { album ->
                openAlbum(album)
            },
            onThumbnailReady = { imageView, bitmap ->
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    //imageView.setImageResource(R.drawable.baseline_image_24)
                    //TODO: add placeholder image
                }
            }
        )

        // Register the ActivityResultLauncher
        albumActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val updatedFileCount = result.data?.getIntExtra("updatedFileCount", 0) ?: 0
                val albumId = result.data?.getStringExtra("albumId") ?: ""
                if (albumId.isNotEmpty()) {
                    albumAdapter.updateAlbumFileCount(albumId, updatedFileCount)  // Update the specific album
                }
            }
        }

        mainRecyclerView.adapter = albumAdapter

        fab.setOnClickListener {
            openAlbumSelector(this)
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
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openAlbumSelector(context: Context) {
        //TODO: improve Dialog
        val albums = getAlbums(context).toMutableList() // Replace with your method to fetch album names
        val albumNames = albums.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Choose an Album to store media")
            .setItems(albumNames) { _, which ->
                targetAlbum = albums[which]
                checkAndRequestPermissions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAlbum(album: Album) {
        val intent = Intent(this, AlbumActivity::class.java)
        intent.putExtra("albumName", album.name)
        intent.putExtra("albumDirectoryPath", getAlbumPath(albumsDir, album.name))
        albumActivityResultLauncher.launch(intent)
    }

    private fun createNewAlbum(context: Context) {
        //TODO: error check and improve Dialogs
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
            val albumId = generateAlbumId()
            saveAlbumMetadata(context, albumName, albumId)
            val newAlbum = Album(albumName, 0, albumId, albumDir.absolutePath)
            albumAdapter.addAlbum(newAlbum)
        }
    }

    fun updateAlbumName(context: Context, oldAlbumName: String, newAlbumName: String) {
        val albumId = getAlbumId(context, oldAlbumName) ?: return
        saveAlbumMetadata(context, newAlbumName, albumId)
        // Optionally remove old metadata
        val prefs = context.getSharedPreferences("album_metadata", Context.MODE_PRIVATE)
        prefs.edit().remove(oldAlbumName).apply()
    }

    fun getAlbums(context: Context): List<Album> {
        val albumDirs = albumsDir.listFiles { file -> file.isDirectory } ?: return emptyList()
        return albumDirs.map { dir ->
            val photoCount = (getImageFileCountFromAlbum(dir))
            Album(dir.name, photoCount, getAlbumId(context, dir.name) ?: "", dir.absolutePath)
        }
    }

    private fun generateAlbumId(): String {
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

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val mediaUri: Uri? = result.data?.data
            mediaUri?.let {
                // Handle the selected image or video URI
                handleSelectedMedia(it)
            }
        }
    }

    private fun saveMetadata(albumName: String, originalFileName: String, encryptedFileName: String, mimeType: String) {
        val metadataFile = File(getAlbumPath(albumsDir,albumName), "album_metadata.xml")
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

    private fun handleSelectedMedia(mediaUri: Uri) {
        val bitmap = getBitmapFromUri(contentResolver, mediaUri)
        val encryptedImage = EncryptionUtils.encryptImage(bitmap)

        val originalFileName = FileUtils.getFileNameFromUri(this, mediaUri) ?: "unknown"
        val mimeType = contentResolver.getType(mediaUri) ?: "unknown"

        saveEncryptedImageToStorage(encryptedImage,albumsDir, targetAlbum, originalFileName, mimeType)
        updatePhotoCount(targetAlbum)
        //deleteImageFromUri(mediaUri)
    }

    private fun updatePhotoCount(album: Album?) {
        album?.let {
            // Get the current count of image files in the album directory
            val albumDir = File(getAlbumPath(albumsDir,album.name)) // Get the path of the album
            val imageCount = getImageFileCountFromAlbum(albumDir)
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

    class AlbumAdapter(
        private val albums: MutableList<Album>,
        private val onAlbumClick: (Album) -> Unit,
        private val onThumbnailReady: (ImageView, Bitmap?) -> Unit
    ) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

        inner class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val albumThumbnail: ImageView = view.findViewById(R.id.album_thumbnail)
            val albumName: TextView = view.findViewById(R.id.album_name)
            val photoCount: TextView = view.findViewById(R.id.photo_count)
            val optionsButton: ImageButton = view.findViewById(R.id.album_options_button)

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
            holder.optionsButton.setOnClickListener {
                //TODO: show dialog for deleting or renaming album
            }

            loadThumbnailAsync(album, holder.albumThumbnail, onThumbnailReady)
        }

        override fun getItemCount(): Int = albums.size

        fun addAlbum(album: Album) {
            albums.add(album)
            notifyItemInserted(albums.size - 1)
        }

        fun updateAlbumFileCount(albumId: String, updatedFileCount: Int) {
            val album = albums.find { it.albumID == albumId }
            album?.photoCount = updatedFileCount
            notifyDataSetChanged()
        }
    }

    data class Album(val name: String, var photoCount: Int, val albumID: String, val pathString: String = "")
}