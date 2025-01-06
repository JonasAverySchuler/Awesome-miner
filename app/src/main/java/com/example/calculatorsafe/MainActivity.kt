package com.example.calculatorsafe

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputFilter
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
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
import com.example.calculatorsafe.adapters.AlbumAdapter
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
import com.example.calculatorsafe.utils.FileUtils.getImageFileCountFromAlbum
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
    private val REQUEST_CODE_READ_MEDIA = 1001
    private lateinit var albumsDir: File
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var albums: MutableList<Album>
    private var targetAlbum: Album? = null
    private lateinit var albumActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var manageStoragePermissionLauncher: ActivityResultLauncher<Intent>
    private val REQUEST_CODE_MANAGE_STORAGE = 1003

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

        // Register the ActivityResultLauncher
        albumActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val updatedFileCount = result.data?.getIntExtra("updatedFileCount", 0) ?: 0
                val albumId = result.data?.getStringExtra("albumId") ?: ""
                if (albumId.isNotEmpty()) {
                    albumAdapter.updateAlbumFileCount(albumId, updatedFileCount)
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

        if (!PreferenceHelper.isPasscodeSet(this)) {
            //TODO: passcode setup
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
            Toast.makeText(this, "No albums found", Toast.LENGTH_SHORT).show()
            return
        }

        val albumNames = albums.map { it.name }.toTypedArray()

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Choose an Album to store media")
            .setItems(albumNames) { dialog, which ->
                targetAlbum = albums[which]
                dialog.dismiss()
                checkAndRequestPermissions()
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
                        showDeleteAlbumConfirmationDialog(album)
                        dialog.dismiss()
                    }
                }
            }.setCancelable(true).show()
    }

    private fun showDeleteAlbumConfirmationDialog(album: Album) {
        // Inflate the custom layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirmation, null)

        val messageView: TextView = dialogView.findViewById(R.id.dialog_message)
        val positiveButton: Button = dialogView.findViewById(R.id.btn_positive)
        val negativeButton: Button = dialogView.findViewById(R.id.btn_negative)

        // Set text color or other properties if needed
        messageView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))  // Ensure text color is black
        messageView.text = "Are you sure you want to delete this album and its contents?"
        // Create the AlertDialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)  // Use the custom layout
            .setCancelable(false)
            .create()

        // Set button listeners
        positiveButton.setOnClickListener {
            // Proceed with the deletion if the user confirms
            deleteAlbumAndContents(album)
            dialog.dismiss()
        }

        negativeButton.setOnClickListener {
            dialog.dismiss()  // Do nothing if the user cancels
        }

        // Show the dialog
        dialog.show()
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
        albums.remove(album)
        albumAdapter.deleteAlbum(album)
        albumAdapter.notifyDataSetChanged()
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

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        //PermissionHelper.checkAllGrantedPermissions(this)
        // Check for API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  // Android 13 (API 33) or above
            // Request media permissions separately
            if (ContextCompat.checkSelfPermission(this, READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(READ_MEDIA_VIDEO)
            }
        } else // Android 10 (API 29) to Android 12 (API 31)
            // Request storage permission for reading media
            if (ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(READ_EXTERNAL_STORAGE)
            }
        if (permissionsNeeded.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $permissionsNeeded")
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

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
            updatePhotoCount(targetAlbum)

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

    private fun updatePhotoCount(album: Album?) {
        album?.let {
            // Get the current count of image files in the album directory
            val albumDir = File(getAlbumPath(albumsDir, album.name)) // Get the path of the album
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

    data class Album(val name: String, var photoCount: Int, val albumID: String, val pathString: String = "")
}