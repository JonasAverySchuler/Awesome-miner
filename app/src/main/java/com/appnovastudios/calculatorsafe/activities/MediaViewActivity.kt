package com.appnovastudios.calculatorsafe.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.appnovastudios.calculatorsafe.FileManager
import com.appnovastudios.calculatorsafe.helpers.DialogHelper
import com.appnovastudios.calculatorsafe.utils.FileUtils
import com.example.calculatorsafe.R
import com.example.calculatorsafe.adapters.MediaItemWrapper
import com.example.calculatorsafe.adapters.MediaPagerAdapter
import java.io.File

class MediaViewActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: MediaPagerAdapter

    companion object {
        const val TAG = "MediaViewActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_view)

        viewPager = findViewById(R.id.viewPager)
        val btnDelete = findViewById<Button>(R.id.btnDelete)
        val btnRestore = findViewById<Button>(R.id.btnRestore)

        val albumDirectoryPath = intent.getStringExtra("albumDirectoryPath") ?: ""
        val startPosition = intent.getIntExtra("position", 0)

        val fileDetails = FileUtils.readMetadataFile(albumDirectoryPath)

        val mediaItemWrapper = fileDetails.map { fileDetail ->
            if (fileDetail.mimeType == "image") {
                val fullPath = "$albumDirectoryPath/${fileDetail.encryptedFileName}"
                MediaItemWrapper.Image(fullPath)
            } else {
                MediaItemWrapper.Video(
                    Uri.fromFile(
                        File(
                            albumDirectoryPath,
                            fileDetail.encryptedFileName
                        )
                    )
                ) //TODO: Handle videos and error check this
            }
        }

        adapter = MediaPagerAdapter(mediaItemWrapper.toMutableList())
        viewPager.adapter = adapter
        viewPager.setCurrentItem(startPosition, false)

        btnDelete.setOnClickListener{
            DialogHelper.showConfirmationDialog(
                this,
                "Delete Image",
                "Are you sure you want to delete this image?",
                "Delete",
                "Cancel",
                { deleteImage()},
                {})
        }

        btnRestore.setOnClickListener {
            // Share the current image
            val currentPosition = viewPager.currentItem
            DialogHelper.showConfirmationDialog(
                this,
                "Restore Image",
                "Restore this image?",
                "Confirm",
                "Cancel",
                {restoreImage()},
                {})
        }
    }

    private fun deleteImage() {
        val position = viewPager.currentItem
        val deletedImagePath = adapter.deleteFileAt(position)

        if (deletedImagePath != null) {
            val file = File(deletedImagePath)
            if (file.exists() && file.delete()) {
                Toast.makeText(this, "Image deleted", Toast.LENGTH_SHORT).show()
                FileManager.setFilePaths(adapter.getFilePaths())
                // Pass the deleted image path back to the previous activity
                val resultIntent = Intent()
                resultIntent.putExtra("deletedImagePath", deletedImagePath)
                setResult(RESULT_OK, resultIntent)
            } else {
                Toast.makeText(this, "Failed to delete image", Toast.LENGTH_SHORT).show()
            }
        }

        // If no images are left, finish the activity
        if (adapter.itemCount == 0) {
            finish()
        }
    }

    private fun restoreImage() {
        val position = viewPager.currentItem

    }
}