package com.example.calculatorsafe.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
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
import com.example.calculatorsafe.utils.FileUtils
import com.example.calculatorsafe.utils.FileUtils.accessUserImages
import com.example.calculatorsafe.utils.FileUtils.generateAlbumId
import com.example.calculatorsafe.utils.FileUtils.getAlbumPath
import com.example.calculatorsafe.utils.FileUtils.handleSelectedMedia
import com.example.calculatorsafe.utils.StringUtils.isValidAlbumName
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import java.io.File

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

        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    // Handle multiple selected files
                    intent.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            val uri = clipData.getItemAt(i).uri
                            FileUtils.handleSelectedMedia(this, uri, targetAlbum!!, manageStoragePermissionLauncher)
                        }
                    } ?: run {
                        // Handle single selected file
                        intent.data?.let { uri ->
                            handleSelectedMedia(this, uri, targetAlbum!!, manageStoragePermissionLauncher)
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

        DialogHelper.chooseAlbumDialog(
            context,
            albums,
            "Choose an Album to store media"
        ) { album ->
            targetAlbum = album
            PermissionHelper.checkAndRequestPermissions(
                this, {
                    accessUserImages(pickMediaLauncher)
                },
                manageStoragePermissionLauncher
            )
        }
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
                if (isValidAlbumName(this, albumName)) {
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

    private fun showNewAlbumDialog(context: Context) {
        DialogHelper.showEditTextDialog(
            this,
            "New Album",
            "Enter album name",
            "Create",
            "Cancel",
            { albumName ->
                if (isValidAlbumName(context, albumName)) {
                    createAlbum(context,albumName)
                    // Refresh the RecyclerView
                    albumAdapter.notifyDataSetChanged()
                } else
                    Toast.makeText(this, "Invalid album name", Toast.LENGTH_SHORT).show()
            })
    }

    private fun openAlbum(album: Album) {
        val intent = Intent(this, AlbumActivity::class.java)
        intent.putExtra("albumName", album.name)
        intent.putExtra("albumDirectoryPath", getAlbumPath(albumsDir, album.name))
        albumActivityResultLauncher.launch(intent)
    }

    private fun createAlbum(context: Context, albumName: String) {
        val albumDir = File(albumsDir, albumName)
        if (!albumDir.exists()) {
            if (albumDir.mkdirs()) {
                // Add new album to the list and update RecyclerView
                val albumId = generateAlbumId()

                // Create an empty metadata.json file in the album directory
                val metadataFile = File(albumDir, "metadata.json")
                val metadata = FileUtils.Metadata(
                    albumName = albumName,
                    files = emptyList()  // Start with an empty list of files
                )
                metadataFile.writeText(Gson().toJson(metadata))

                // Create a new Album object and update the RecyclerView
                val newAlbum = Album(
                    name = albumName,
                    photoCount = 0,
                    albumID = albumId,
                    pathString = albumDir.absolutePath
                )

                albumAdapter.addAlbum(newAlbum)
            } else {
                // Failed to create the album directory
                Log.e("CreateAlbum", "Failed to create album directory: ${albumDir.absolutePath}")
                Toast.makeText(context, "Failed to create album.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Album directory already exists
            Log.e("CreateAlbum", "Album directory already exists: ${albumDir.absolutePath}")
            Toast.makeText(context, "Album already exists.", Toast.LENGTH_SHORT).show()
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
        //saveAlbumMetadata(context, newAlbumName, albumId)
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
                    accessUserImages(pickMediaLauncher)
                } else {
                    // Permission denied, show a message to the user
                    showPermissionDeniedMessage()
                }
            }
        }
    }

    private fun showPermissionDeniedMessage() {
        // Show a message to the user explaining why the permission is needed
        Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
        //TODO: send user to settings to enable permissions
        //show dialog telling user permissions are required and to enable them to access features
    }
}