package com.example.calculatorsafe

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class MediaViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_view)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val btnDelete = findViewById<Button>(R.id.btnDelete)
        val btnShare = findViewById<Button>(R.id.btnShare)

        val imagePaths = FileManager.getFilePaths()
        Log.e("MediaViewActivity", "Image paths: $imagePaths")
        val startPosition = intent.getIntExtra("position", 0)
        val adapter = ImagePagerAdapter(imagePaths)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(startPosition, false)

        btnDelete.setOnClickListener{

        }

        btnShare.setOnClickListener {
            // Share the current image
            val currentPosition = viewPager.currentItem
            //val bitmap = images[currentPosition]

            //val uri = saveBitmapToCache(bitmap)
            //val shareIntent = Intent(Intent.ACTION_SEND).apply {
            //    type = "image/*"
             //   putExtra(Intent.EXTRA_STREAM, uri)
          //  }
           // startActivity(Intent.createChooser(shareIntent, "Share image via"))
        }

    }
}