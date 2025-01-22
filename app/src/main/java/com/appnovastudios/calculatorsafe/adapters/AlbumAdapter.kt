package com.example.calculatorsafe.adapters

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.appnovastudios.calculatorsafe.FileManager
import com.example.calculatorsafe.R
import com.appnovastudios.calculatorsafe.ThumbnailLoader.loadThumbnailAsync
import com.example.calculatorsafe.data.Album
import com.appnovastudios.calculatorsafe.helpers.PreferenceHelper.getAlbumId
import com.appnovastudios.calculatorsafe.utils.FileUtils.getEncryptedFilesFromMetadata
import java.io.File

class AlbumAdapter(
    private val albums: MutableList<Album>,
    private val onAlbumClick: (Album) -> Unit,
    private val onThumbnailReady: (ImageView, Bitmap?) -> Unit,
    private val onOptionClick: (Album) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    inner class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val albumThumbnail: ImageView = view.findViewById(R.id.album_thumbnail)
        val albumName: TextView = view.findViewById(R.id.album_name)
        val photoCount: TextView = view.findViewById(R.id.photo_count)
        val optionsButton: ImageButton = view.findViewById(R.id.album_options_button)

        init {
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && albums.isNotEmpty() && position != albums.size) {
                    // Safe to use adapterPosition
                    onAlbumClick(albums[position])
                } else {
                    // Invalid position, log or handle accordingly
                    Log.e("Error", "Invalid position: $position")
                    notifyDataSetChanged()
                }
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
            onOptionClick(album)
        }

        loadThumbnailAsync(album, holder.albumThumbnail, onThumbnailReady)
    }

    override fun getItemCount(): Int = albums.size

    fun addAlbum(album: Album) {
        // Check if album already exists
        if (!albums.contains(album)) {
            albums.add(album)
            notifyItemInserted(albums.size - 1)
        } else {
            Log.e("AlbumAdapter", "Album already exists: ${album.name}")
        }
    }


    fun updateAlbumFileCount(albumId: String, updatedFileCount: Int) {
        val album = albums.find { it.albumID == albumId }
        album?.let {
            val position = albums.indexOf(it)
            Log.d("AlbumAdapter", "Updating album file count for album: ${it.name}, position: $position")
            if (position != -1) {
                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    it.photoCount = updatedFileCount
                    albums[position] = it
                    this.notifyItemChanged(position)
                }

            }
        }
    }

    fun deleteAlbum(album: Album) {
        val position = albums.indexOf(album)
        if (position != -1) {
            albums.removeAt(position)
            notifyItemRemoved(position)

            // Check if the list is empty and notify the adapter
            if (albums.isEmpty()) {
                notifyDataSetChanged()  // Optional, but it can help ensure RecyclerView knows the list is empty
            }
        } else {
            Log.e("AlbumAdapter", "Album not found: ${album.name}")
        }
    }

    fun updateFromFileManager(context: Context) {
        albums.clear()
        albums.addAll(FileManager.getAlbums(context))
        notifyDataSetChanged()
    }

    fun updateFromMetadata(context: Context) {
        // Retrieve updated metadata
        val albumsDir = File(context.filesDir, "Albums")
        val albumDirs = albumsDir.listFiles { file -> file.isDirectory } ?: return
        val updatedAlbums = albumDirs.map { dir ->
            val albumId = getAlbumId(context, dir.name) ?: ""
            val albumFiles = getEncryptedFilesFromMetadata(dir.absolutePath)
            val photoCount = albumFiles.size

            Album(
                name = dir.name,
                photoCount = photoCount,
                albumID = albumId,
                pathString = dir.absolutePath
            )
        }

        // Update the dataset and notify adapter
        albums.clear()
        albums.addAll(updatedAlbums)
        notifyDataSetChanged()
    }
}