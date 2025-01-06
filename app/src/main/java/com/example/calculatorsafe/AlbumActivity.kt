package com.example.calculatorsafe

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.calculatorsafe.adapters.EncryptedImageAdapter
import com.example.calculatorsafe.helpers.PermissionHelper
import com.example.calculatorsafe.helpers.PreferenceHelper.getAlbumId
import com.example.calculatorsafe.utils.EncryptionUtils
import com.example.calculatorsafe.utils.EncryptionUtils.getBitmapFromUri
import com.example.calculatorsafe.utils.EncryptionUtils.saveEncryptedImageToStorage
import com.example.calculatorsafe.utils.FileUtils
import com.example.calculatorsafe.utils.FileUtils.getImageFileCountFromAlbum
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class AlbumActivity : AppCompatActivity() {

    private val REQUEST_CODE_READ_MEDIA = 1001
    private lateinit var albumDirectoryPath: String
    private lateinit var album: MainActivity.Album
    private lateinit var adapter: EncryptedImageAdapter
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var selectionModeCallback: OnBackPressedCallback
    private lateinit var toolbar: Toolbar
    private lateinit var mediaViewActivityResultLauncher: ActivityResultLauncher<Intent>

    companion object {
        private val TAG = "AlbumActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)
        val albumName = intent.getStringExtra("albumName") ?: ""
        albumDirectoryPath = intent.getStringExtra("albumDirectoryPath") ?: ""
        album = MainActivity.Album(albumName, getImageFileCountFromAlbum(File(albumDirectoryPath)), getAlbumId(this, albumName) ?: "", albumDirectoryPath)

        val encryptedFiles = File(albumDirectoryPath).listFiles { _, name -> name.endsWith(".enc") }?.toList() ?: emptyList()
        FileManager.setFilePaths(encryptedFiles.map { it.absolutePath })
        permissionHelper = PermissionHelper(this)

        toolbar = findViewById<Toolbar>(R.id.album_toolbar)
        val albumRecyclerView = findViewById<RecyclerView>(R.id.album_RecyclerView)
        val albumFab = findViewById<FloatingActionButton>(R.id.album_fab)
        val gridLayoutManager = GridLayoutManager(this, 3)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = albumName
        supportActionBar?.subtitle = "${encryptedFiles.size} files" //TODO:count files types

        // Calculate and set item width dynamically
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val spacing = 3 // Adjust for margins and padding
        val itemWidth = (screenWidth - (spacing * 4)) / 3

        albumRecyclerView.layoutManager = gridLayoutManager
        selectionModeCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                exitSelectionMode()
            }
        }
        onBackPressedDispatcher.addCallback(this, selectionModeCallback)

        adapter = EncryptedImageAdapter(
            encryptedFiles.toMutableList(),
            itemWidth,
            { index ->
                openMediaViewActivity(index)
            },
            { file -> EncryptionUtils.decryptImage(file) }
        ) {
            enterSelectionMode()
        }.apply {
            onSelectionChanged = {
                updateSelectionSubtitle()
            }
        }

        albumRecyclerView.adapter = adapter

        albumFab.setOnClickListener {
            checkAndRequestPermissions()
            }

        toolbar.setNavigationOnClickListener {
            if (selectionModeCallback.isEnabled) {
                selectionModeCallback.handleOnBackPressed()
            } else {
                setResultIntent()
                onBackPressedDispatcher.onBackPressed() // Default back behavior
            }
        }

        mediaViewActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                adapter.updateFromFileManager()
                toolbar.subtitle = "${adapter.itemCount} images"
            }
        }

    }

    private fun openMediaViewActivity(index: Int) {
        val intent = Intent(this, MediaViewActivity::class.java)
        intent.putExtra("position", index)
        mediaViewActivityResultLauncher.launch(intent)
    }

    private fun setResultIntent() {
        // Send the result back to MainActivity
        val resultIntent = Intent()
        resultIntent.putExtra("updatedFileCount", FileManager.getSize())
        resultIntent.putExtra("albumId", album.albumID)  // Send the album ID
        setResult(Activity.RESULT_OK, resultIntent)
    }

    // Inflate the menu (from menu_album.xml)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_album, menu)  // R.menu.menu_album is your XML menu file
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        //TODO: add options sorting
        return when (item.itemId) {
            android.R.id.home -> {
                finish() // Closes the activity and goes back to the previous one
                true
            }
            R.id.action_delete -> {
                // Handle delete action
                if (adapter.selectedItems.isNotEmpty()) {
                    showDeleteConfirmationDialog()
                }
                true
            }
            R.id.action_restore -> {
                if (adapter.selectedItems.isNotEmpty()) {
                    showRestoreConfirmationDialog()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteConfirmationDialog() {
        // Inflate the custom layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirmation, null)

        val messageView: TextView = dialogView.findViewById(R.id.dialog_message)
        val positiveButton: Button = dialogView.findViewById(R.id.btn_positive)
        val negativeButton: Button = dialogView.findViewById(R.id.btn_negative)

        // Set text color or other properties if needed
        messageView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))  // Ensure text color is black

        // Create the AlertDialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)  // Use the custom layout
            .setCancelable(false)
            .create()

        // Set button listeners
        positiveButton.setOnClickListener {
            // Proceed with the deletion if the user confirms
            adapter.deleteSelectedFiles()
            exitSelectionMode()  // Exit selection mode after deletion
            dialog.dismiss()
        }

        negativeButton.setOnClickListener {
            dialog.dismiss()  // Do nothing if the user cancels
        }

        // Show the dialog
        dialog.show()
    }

    private fun showRestoreConfirmationDialog() {
        // Inflate the custom layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirmation, null)

        val messageView: TextView = dialogView.findViewById(R.id.dialog_message)
        val positiveButton: Button = dialogView.findViewById(R.id.btn_positive)
        val negativeButton: Button = dialogView.findViewById(R.id.btn_negative)

        // Set text color or other properties if needed
        messageView.setTextColor(ContextCompat.getColor(this, android.R.color.black))  // Ensure text color is black
        messageView.text = "Are you sure you want to restore the selected files?"
        // Create the AlertDialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)  // Use the custom layout
            .setCancelable(false)
            .create()

        // Set button listeners
        positiveButton.setOnClickListener {
            // Proceed with the deletion if the user confirms
            adapter.restoreSelectedFiles(this)
            exitSelectionMode()  // Exit selection mode after deletion
            dialog.dismiss()
        }

        negativeButton.setOnClickListener {
            dialog.dismiss()  // Do nothing if the user cancels
        }

        // Show the dialog
        dialog.show()
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        // Check for API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  // Android 13 (API 33) or above
            // Request media permissions separately
            if (ContextCompat.checkSelfPermission(this, READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(READ_MEDIA_VIDEO)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {  // Android 10 (API 29) to Android 12 (API 31)
            // Request storage permission for reading media
            if (ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(READ_EXTERNAL_STORAGE)
            }
        }
        if (permissionsNeeded.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $permissionsNeeded")
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), REQUEST_CODE_READ_MEDIA)
        } else {
            // Permissions are already granted
            accessUserImages()
        }
    }

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
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
    }

    private fun accessUserImages() {
        // Your code to pick images or videos from the gallery
        val pickMediaIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/* video/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        pickMediaLauncher.launch(pickMediaIntent)
    }

    private fun handleSelectedMedia(mediaUri: Uri) {
        val bitmap = getBitmapFromUri(contentResolver, mediaUri)
        val encryptedImage = EncryptionUtils.encryptImage(bitmap)

        val originalFileName = FileUtils.getFileNameFromUri(this, mediaUri) ?: "unknown"
        val mimeType = contentResolver.getType(mediaUri) ?: "unknown"

        //albumDirectoryPath will always have its parent directory and so it is safe to assert it, we need the parentDirectory Albums
        val newFilePath = saveEncryptedImageToStorage(encryptedImage, File(albumDirectoryPath).parentFile!!, album, originalFileName, mimeType)
        adapter.addFile(File(newFilePath))
        toolbar.subtitle = "${adapter.itemCount} images"
        //deleteImageFromUri(mediaUri) //TODO: Delete file when i feel confident we have a working solution
    }

    private fun enterSelectionMode() {
        adapter.mode = EncryptedImageAdapter.Mode.SELECTION
        selectionModeCallback.isEnabled = true
        toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.close)
        toolbar.setNavigationOnClickListener {
            selectionModeCallback.handleOnBackPressed()
        }
        toolbar.title = "Selection Mode"
        updateSelectionSubtitle()
        // Show toolbar or action bar for operations like Restore/Delete
    }
    private fun updateSelectionSubtitle() {
        val selectedCount = adapter.selectedItems.size
        val totalCount = adapter.itemCount
        toolbar.subtitle = "$selectedCount selected out of $totalCount"
    }

    private fun exitSelectionMode() {
        adapter.mode = EncryptedImageAdapter.Mode.VIEWING
        adapter.selectedItems.clear()
        selectionModeCallback.isEnabled = false
        toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.back)
        toolbar.setNavigationOnClickListener {
            setResultIntent()
            onBackPressedDispatcher.onBackPressed()
        }
        toolbar.title = album.name
        toolbar.subtitle = "${adapter.itemCount} images"
        // Hide toolbar or action bar
    }
}