package com.example.calculatorsafe

import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.ByteArrayOutputStream
import java.io.File

class AlbumActivity : AppCompatActivity() {

    private val REQUEST_CODE_READ_MEDIA = 1001
    private lateinit var directoryPath: String
    private lateinit var recyclerViewAdapter: EncryptedImageAdapter

    companion object {
        private val TAG = "AlbumActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)
        val albumName = intent.getStringExtra("albumName")
        directoryPath = intent.getStringExtra("directoryPath") ?: ""
        Log.e(TAG, "Directory path: $directoryPath")

        val encryptedFiles = File(directoryPath).listFiles { _, name -> name.endsWith(".enc") }?.toList() ?: emptyList()
        FilePathManager.setFilePaths(encryptedFiles.map { it.absolutePath })
        Log.e(TAG, "File paths: ${FilePathManager.getFilePaths()}")

        val toolbar = findViewById<Toolbar>(R.id.album_toolbar)
        val albumRecyclerView = findViewById<RecyclerView>(R.id.album_RecyclerView)
        val gridLayoutManager = GridLayoutManager(this, 3)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = albumName

        // Calculate and set item width dynamically
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val spacing = 3 // Adjust for margins and padding
        val itemWidth = (screenWidth - (spacing * 4)) / 3

        albumRecyclerView.layoutManager = gridLayoutManager

        recyclerViewAdapter = EncryptedImageAdapter(encryptedFiles, itemWidth) { file ->
            EncryptionUtils.decryptImage(file)
        }

        albumRecyclerView.adapter = recyclerViewAdapter

        val albumFab = findViewById<FloatingActionButton>(R.id.album_fab)
        albumFab.setOnClickListener {
            checkAndRequestPermissions()
            }
        }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish() // Closes the activity and goes back to the previous one
                true
            }
            else -> super.onOptionsItemSelected(item)
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
        val secretKey = KeystoreUtils.getOrCreateGlobalKey()
        val byteArray = getByteArrayFromUri(contentResolver, uri)
        if (byteArray != null) {
            //val encryptedPhoto = encryptPhoto(byteArray, secretKey)
            //if (encryptedPhoto != null) {
                //saveEncryptedPhoto(this, encryptedPhoto, "photo_${System.currentTimeMillis()}.png")
                //deleteOriginalPhoto(contentResolver, uri)

                // Refresh your RecyclerView
                //loadPhotos()
            //}
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

    class EncryptedImageAdapter(
        private val encryptedFiles: List<File>,
        private val itemWidth: Int,
        private val decryptFunction: (File) -> Bitmap
        ) : RecyclerView.Adapter<EncryptedImageAdapter.PhotoViewHolder>() {

        inner class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardView: CardView = view.findViewById(R.id.card_view)
            val photoImageView: ImageView = view.findViewById(R.id.photo_image_view)

            fun setItemWidth(width: Int) {
                // Set the width for the CardView
                val layoutParams = cardView.layoutParams
                layoutParams.width = width
                cardView.layoutParams = layoutParams

                layoutParams.height = width // Set the same height as the width to maintain a square shape
                cardView.layoutParams = layoutParams
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.grid_item_layout, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            holder.setItemWidth(itemWidth)
            val encryptedFile = encryptedFiles[position]
            val decryptedBitmap = decryptFunction(encryptedFile)
            holder.photoImageView.setImageBitmap(decryptedBitmap)
            holder.itemView.setOnClickListener {
                val intent = Intent(holder.itemView.context, MediaViewActivity::class.java)
                intent.putExtra("position", position)
                holder.itemView.context.startActivity(intent)
            }
        }

        override fun getItemCount(): Int = encryptedFiles.size
    }

}