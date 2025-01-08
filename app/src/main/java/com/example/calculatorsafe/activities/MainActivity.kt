package com.example.calculatorsafe.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputFilter
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.calculatorsafe.FileManager.getAlbums
import com.example.calculatorsafe.R
import com.example.calculatorsafe.adapters.AlbumAdapter
import com.example.calculatorsafe.data.Album
import com.example.calculatorsafe.helpers.DialogHelper
import com.example.calculatorsafe.helpers.PermissionHelper
import com.example.calculatorsafe.helpers.PermissionHelper.REQUEST_CODE_READ_MEDIA
import com.example.calculatorsafe.helpers.PreferenceHelper
import com.example.calculatorsafe.helpers.PreferenceHelper.getAlbumId
import com.example.calculatorsafe.helpers.PreferenceHelper.saveAlbumMetadata
import com.example.calculatorsafe.utils.EncryptionUtils
import com.example.calculatorsafe.utils.EncryptionUtils.getBitmapFromUri
import com.example.calculatorsafe.utils.EncryptionUtils.saveEncryptedImageToStorage
import com.example.calculatorsafe.utils.FileUtils
import com.example.calculatorsafe.utils.FileUtils.generateAlbumId
import com.example.calculatorsafe.utils.FileUtils.getAlbumPath
import com.example.calculatorsafe.utils.FileUtils.getFilePathFromUri
import com.example.calculatorsafe.utils.StringUtils.isValidAlbumName
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.w3c.dom.Document
import java.io.File
import java.io.FileOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class MainActivity : AppCompatActivity() {
    private lateinit var albumsDir: File
    private lateinit var albumAdapter: AlbumAdapter
    private var targetAlbum: Album? = null
    private lateinit var albumActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var manageStoragePermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickMediaLauncher: ActivityResultLauncher<Intent>

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
        val albums = getAlbums(this).toMutableList()

        albumAdapter = AlbumAdapter(albums,
            onAlbumClick = { album ->
                openAlbum(album)
            },
            onThumbnailReady = { imageView, bitmap ->
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(R.drawable.image)
                }
            },
            onOptionClick = { album ->
                showAlbumOptionsDialog(album)
            }
        )

        // Create the "Main" album inside "Albums" on first run
        if (PreferenceHelper.isFirstRun(this)) {
            createAlbum(this, "Main")
            PreferenceHelper.setFirstRun(this, false)
        }

        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    // Handle multiple selected files
                    intent.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            val uri = clipData.getItemAt(i).uri
                            handleSelectedMedia(uri)
                        }
                    } ?: run {
                        // Handle single selected file
                        intent.data?.let { uri ->
                            handleSelectedMedia(uri)
                        }
                    }
                }
                albumAdapter.updateFromFileManager(this)
            }
        }

        // Register the ActivityResultLauncher
        albumActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val albumId = result.data?.getStringExtra("albumId") ?: ""
                if (albumId.isNotEmpty()) {
                    albumAdapter.updateFromFileManager(this)
                }
            }
        }

        // Register the launcher for the settings intent
        manageStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (Environment.isExternalStorageManager()) {
                // Permission granted, proceed with your file operations
                Log.d("Permission", "Permission granted")
            } else {
                // Permission denied
                Log.d("Permission", "Permission denied")
            }
        }

        mainRecyclerView.adapter = albumAdapter

        fab.setOnClickListener {
            openAlbumSelector(this)
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
                showNewAlbumDialog(this)
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
        val albums = getAlbums(context).toMutableList()
        if (albums.isEmpty()) {
            Toast.makeText(this, "No albums found, please create at least one album", Toast.LENGTH_SHORT).show()
            return
        }

        val albumNames = albums.map { it.name }.toTypedArray()

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Choose an Album to store media")
            .setItems(albumNames) { dialog, which ->
                targetAlbum = albums[which]
                dialog.dismiss()
                PermissionHelper.checkAndRequestPermissions(this) {
                    accessUserImages()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAlbumOptionsDialog(album: Album) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Album Options").setItems(listOf("Rename", "Delete").toTypedArray()) { dialog, which ->
                when (which) {
                    0 -> {
                        // Handle Rename option
                        showRenameAlbumDialog(album)
                        dialog.dismiss()
                    }
                    1 -> {
                        DialogHelper.showConfirmationDialog(this, "Delete Album",
                            "Are you sure you want to delete this album and its contents?","Confirm", "Cancel",
                            { deleteAlbumAndContents(album)},
                            {})
                        dialog.dismiss()
                    }
                }
            }.setCancelable(true).show()
    }

    private fun showRenameAlbumDialog(album: Album) {
        // Inflate the custom layout
        val editText = EditText(this)
        editText.hint = "Enter new album name"

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Rename Album")
            .setView(editText)
            .setPositiveButton("Confirm") { _, _ ->
                val albumName = editText.text.toString().trim()
                if (isValidAlbumName(albumName)) {
                    //renameAlbum(context,albumName)
                    // Refresh the RecyclerView
                    albumAdapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(this, "Invalid album name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun openAlbum(album: Album) {
        val intent = Intent(this, AlbumActivity::class.java)
        intent.putExtra("albumName", album.name)
        intent.putExtra("albumDirectoryPath", getAlbumPath(albumsDir, album.name))
        albumActivityResultLauncher.launch(intent)
    }

    private fun showNewAlbumDialog(context: Context) {
        val editText = EditText(this)
        editText.hint = "Enter album name"
        editText.filters = arrayOf(InputFilter.LengthFilter(40))

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("New Album")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val albumName = editText.text.toString().trim()
                if (isValidAlbumName(albumName)) {
                    createAlbum(context,albumName)
                    // Refresh the RecyclerView
                    albumAdapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(this, "Invalid album name", Toast.LENGTH_SHORT).show()
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

    private fun deleteAlbumAndContents(album: Album) {
        //TODO: error check this
        val albumDir = File(album.pathString)
        albumDir.deleteRecursively()
        albumAdapter.deleteAlbum(album)
    }

    fun updateAlbumName(context: Context, oldAlbumName: String, newAlbumName: String) {
        val albumId = getAlbumId(context, oldAlbumName) ?: return
        saveAlbumMetadata(context, newAlbumName, albumId)
        // Optionally remove old metadata
        val prefs = context.getSharedPreferences("album_metadata", Context.MODE_PRIVATE)
        prefs.edit().remove(oldAlbumName).apply()
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
        // Using ACTION_OPEN_DOCUMENT for better control over file selection
        val pickMediaIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/* video/*" // You can adjust to select specific file types
            putExtra(Intent.EXTRA_LOCAL_ONLY, true) // Limit to local storage only
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // Allow multiple file selection
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        pickMediaLauncher.launch(pickMediaIntent)
    }

    private fun showPermissionDeniedMessage() {
        // Show a message to the user explaining why the permission is needed
        Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
        //TODO: send user to settings to enable permissions
        //show dialog telling user permissions are required and to enable them to access features
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
        try {
            // Step 1: Get Bitmap from URI
            val bitmap = getBitmapFromUri(contentResolver, mediaUri)

            // Step 2: Encrypt the Image
            val encryptedImage = EncryptionUtils.encryptImage(bitmap)

            // Step 3: Retrieve File Name and MIME Type
            val originalFileName = FileUtils.getFileNameFromUri(this, mediaUri) ?: "unknown_${System.currentTimeMillis()}.jpg"
            val mimeType = contentResolver.getType(mediaUri) ?: "image/jpeg"

            // Step 4: Save the Encrypted Image
            saveEncryptedImageToStorage(encryptedImage, albumsDir, targetAlbum, originalFileName, mimeType)

            // Step 5: Update the Photo Count in the UI
            //updatePhotoCount(targetAlbum)

            Log.d("MediaHandler", "MediaUri: $mediaUri")

            val filePath = getFilePathFromUri(this, mediaUri) ?: ""
            Log.d("MediaHandler", "File Path from getfilepathfromuri: $filePath")

            // Check if the permission is already granted
            if (Environment.isExternalStorageManager()) {
                // Permission granted, proceed with your file operations
                Log.d("Permission", "Permission granted")
                if(!FileUtils.deleteFile(filePath)) {
                    Log.e("MediaHandler", "In content scheme : Failed to delete original media at URI: $mediaUri")
                }
            } else {
                // Permission is not granted, request it by opening the settings
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                manageStoragePermissionLauncher.launch(intent)
            }

        } catch (e: Exception) {
            Log.e("MediaHandler", "Error handling selected media: ${e.message}", e)
            // Optionally show an error message to the user
            Toast.makeText(this, "Failed to process the selected media.", Toast.LENGTH_SHORT).show()
        }
    }
}