package com.example.calculatorsafe.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.calculatorsafe.FileManager
import com.example.calculatorsafe.FileManager.getAlbums
import com.example.calculatorsafe.R
import com.example.calculatorsafe.adapters.EncryptedImageAdapter
import com.example.calculatorsafe.data.Album
import com.example.calculatorsafe.helpers.DialogHelper
import com.example.calculatorsafe.helpers.PermissionHelper.checkAndRequestPermissions
import com.example.calculatorsafe.helpers.PreferenceHelper.getAlbumId
import com.example.calculatorsafe.utils.FileUtils
import com.example.calculatorsafe.utils.FileUtils.accessUserImages
import com.example.calculatorsafe.utils.FileUtils.getImageFileCountFromAlbum
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class AlbumActivity : AppCompatActivity() {
    private lateinit var albumDirectoryPath: String
    private lateinit var album: Album
    private lateinit var adapter: EncryptedImageAdapter
    private lateinit var selectionModeCallback: OnBackPressedCallback
    private lateinit var toolbar: Toolbar
    private lateinit var mediaViewActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickMediaLauncher: ActivityResultLauncher<Intent>
    private lateinit var manageStoragePermissionLauncher: ActivityResultLauncher<Intent>

    companion object {
        private val TAG = "AlbumActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)
        val albumName = intent.getStringExtra("albumName") ?: ""
        albumDirectoryPath = intent.getStringExtra("albumDirectoryPath") ?: ""
        album = Album(albumName, getImageFileCountFromAlbum(File(albumDirectoryPath)), getAlbumId(this, albumName) ?: "", albumDirectoryPath)

        val encryptedFiles = FileUtils.getEncryptedFilesFromMetadata(albumDirectoryPath)
        FileManager.setFilePaths(encryptedFiles.map { it.absolutePath })

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
            }
        ) {
            enterSelectionMode()
        }.apply {
            onSelectionChanged = {
                updateSelectionSubtitle()
            }
        }

        albumRecyclerView.adapter = adapter

        toolbar.setNavigationOnClickListener {
            if (selectionModeCallback.isEnabled) {
                selectionModeCallback.handleOnBackPressed()
            } else {
                setResultIntent()
                onBackPressedDispatcher.onBackPressed() // Default back behavior
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

        mediaViewActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                adapter.updateFromFileManager()
                toolbar.subtitle = "${adapter.itemCount} images"
            }
        }

        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    // Handle multiple selected files
                    intent.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            val uri = clipData.getItemAt(i).uri
                            val newFilePath = FileUtils.handleSelectedMedia(this, uri, album)
                            adapter.addFile(File(newFilePath))
                        }
                    } ?: run {
                        // Handle single selected file
                        intent.data?.let { uri ->
                            val newFilePath = FileUtils.handleSelectedMedia(this, uri, album)
                            adapter.addFile(File(newFilePath))
                        }
                    }
                }
                toolbar.subtitle = "${adapter.itemCount} images"
            }
        }

        albumFab.setOnClickListener {
            checkAndRequestPermissions(
                this,
             { accessUserImages(pickMediaLauncher) }, manageStoragePermissionLauncher)
        }
    }

    private fun openMediaViewActivity(index: Int) {
        val intent = Intent(this, MediaViewActivity::class.java)
        intent.putExtra("position", index)
        intent.putExtra("albumDirectoryPath", albumDirectoryPath)
        mediaViewActivityResultLauncher.launch(intent)
    }

    private fun setResultIntent() {
        // Send the result back to MainActivity
        val resultIntent = Intent()
        resultIntent.putExtra("albumId", album.albumID)  // Send the album ID
        setResult(Activity.RESULT_OK, resultIntent)
    }

    // Inflate the menu (from menu_album.xml)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_album, menu)  // R.menu.menu_album is your XML menu file
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.cleanup()
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
                    DialogHelper.showConfirmationDialog(this, "Delete Files",
                        "Are you sure you want to delete the selected files?","Confirm", "Cancel",
                        { adapter.deleteSelectedFiles()
                            exitSelectionMode()},
                        {})
                }
                true
            }
            R.id.action_move -> {
                // Handle delete action
                if (adapter.selectedItems.isNotEmpty()) {

                    val albums = getAlbums(this).toMutableList()
                    val albumsNew = albums.filter { it.pathString != album.pathString } //dont show current album as an option
                    if (albumsNew.isEmpty()) {
                        Toast.makeText(this, "No other albums found to move media", Toast.LENGTH_SHORT).show()
                    } else {
                        DialogHelper.chooseAlbumDialog(
                            this,
                            albumsNew,
                            "Choose an Album to move media",
                        ) { album ->
                            adapter.moveSelectedFiles(File(album.pathString))
                            exitSelectionMode()
                        }

                    }
                }
                true
            }
            R.id.action_restore -> {
                if (adapter.selectedItems.isNotEmpty()) {
                    DialogHelper.showConfirmationDialog(this, "Restore Files",
                        "Are you sure you want to restore the selected files?","Confirm", "Cancel",
                        { adapter.restoreSelectedFiles(this)
                            exitSelectionMode()  // Exit selection mode after deletion},
                        })
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
        selectionModeCallback.isEnabled = false
        toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.back)
        toolbar.setNavigationOnClickListener {
            setResultIntent()
            onBackPressedDispatcher.onBackPressed()
        }
        toolbar.title = album.name
        toolbar.subtitle = "${adapter.itemCount} images"
    }
}