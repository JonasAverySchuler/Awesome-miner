package com.appnovastudios.calculatorsafe.adapters

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.appnovastudios.calculatorsafe.FileManager
import com.appnovastudios.calculatorsafe.data.FileDetail
import com.appnovastudios.calculatorsafe.utils.EncryptionUtils
import com.appnovastudios.calculatorsafe.utils.EncryptionUtils.decryptImage
import com.appnovastudios.calculatorsafe.utils.FileUtils
import com.appnovastudios.calculatorsafe.utils.FileUtils.removeFileFromMetadata
import com.example.calculatorsafe.R
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EncryptedFileAdapter(
    private val encryptedFiles: MutableList<File>,
    private val itemWidth: Int,
    private val onPhotoClick: (Int) -> Unit,
    private val onEnterSelectionMode: () -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    enum class Mode {
        VIEWING,
        SELECTION
    }

    var mode = Mode.VIEWING
        set(value) {
            field = value
            when (value) {
                Mode.VIEWING -> {
                    // Clear the selected items
                    notifySelectedItemsChanged()
                }
                Mode.SELECTION -> {

                }
            }
        }

    var onImageCountUpdated: ((Int) -> Unit)? = null

    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val selectedItems = mutableSetOf<Int>()
    private val decryptedBitmaps = mutableMapOf<String, Bitmap?>()  // Cache decrypted bitmaps by position
    var onSelectionChanged: (() -> Unit)? = null

    companion object {
        val TAG = "EncryptedFileAdapter"
        private const val TYPE_IMAGE = 1
        private const val TYPE_VIDEO = 2
    }

    inner class PhotoViewHolder(view: View) : ViewHolder(view) {
        private val cardView: CardView = view.findViewById(R.id.card_view)
        private val photoImageView: ImageView = view.findViewById(R.id.photo_image_view)
        private val overlay: View = itemView.findViewById(R.id.overlay) // A semi-transparent View
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar) // A ProgressBar

        fun bind(file: File, position: Int, isSelected: Boolean) {
            progressBar.visibility = View.GONE
            // Show or hide the selection overlay
            overlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            photoImageView.setImageDrawable(null) // Clear any previous image

            if(decryptedBitmaps[file.name] == null) {
                adapterScope.launch {
                    progressBar.visibility = View.VISIBLE
                    val decryptedBitmap = withContext(Dispatchers.IO) {
                        decryptImage(file)
                    }
                    progressBar.visibility = View.GONE
                    if (decryptedBitmap != null) {
                        decryptedBitmaps[file.name] = decryptedBitmap // Cache the decrypted bitmap
                        photoImageView.setImageBitmap(decryptedBitmap)
                    } else {
                        // Handle the case where decryption failed //TODO: add error handling
                        Log.e("EncryptedImageAdapter", "Decryption failed for file: ${file.name}")
                    }
                }
            } else {
                photoImageView.setImageBitmap(decryptedBitmaps[file.name])
            }

            itemView.setOnClickListener {
                when (mode) {
                    Mode.VIEWING -> {
                        onPhotoClick(position)
                    }
                    Mode.SELECTION -> {
                        toggleSelection(position)
                        notifyItemChanged(position)
                    }
                }
            }
            itemView.setOnLongClickListener {
                if (mode == Mode.VIEWING) {
                    toggleSelection(position)
                    onEnterSelectionMode()
                }
                true
            }
        }

        fun setItemWidth(width: Int) {
            // Set the width for the CardView
            val layoutParams = cardView.layoutParams
            layoutParams.width = width
            cardView.layoutParams = layoutParams

            layoutParams.height = width // Set the same height as the width to maintain a square shape
            cardView.layoutParams = layoutParams
        }
    }

    inner class VideoViewHolder(view: View): ViewHolder(view) {
        private val cardView: CardView = view.findViewById(R.id.video_card_view)
        private val videoImageView: ImageView = view.findViewById(R.id.video_image_view)
        private val overlay: View = itemView.findViewById(R.id.video_overlay) // A semi-transparent View
        private val progressBar: ProgressBar = itemView.findViewById(R.id.video_progress_bar) // A ProgressBar

        fun bind(file: File, position: Int, isSelected: Boolean) {
            progressBar.visibility = View.GONE
            // Show or hide the selection overlay
            overlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            videoImageView.setImageDrawable(null) // Clear any previous image

            if(decryptedBitmaps[file.name] == null) {
                adapterScope.launch {
                    progressBar.visibility = View.VISIBLE
                    val decryptedBitmap = withContext(Dispatchers.IO) {
                        decryptImage(file)
                    }
                    progressBar.visibility = View.GONE
                    if (decryptedBitmap != null) {
                        decryptedBitmaps[file.name] = decryptedBitmap // Cache the decrypted bitmap
                        videoImageView.setImageBitmap(decryptedBitmap)
                    } else {
                        // Handle the case where decryption failed //TODO: add error handling
                        Log.e("EncryptedImageAdapter", "Decryption failed for file: ${file.name}")
                    }
                }
            } else {
                videoImageView.setImageBitmap(decryptedBitmaps[file.name])
            }

            itemView.setOnClickListener {
                when (mode) {
                    Mode.VIEWING -> {
                        onPhotoClick(position)
                    }
                    Mode.SELECTION -> {
                        toggleSelection(position)
                        notifyItemChanged(position)
                    }
                }
            }
            itemView.setOnLongClickListener {
                if (mode == Mode.VIEWING) {
                    toggleSelection(position)
                    onEnterSelectionMode()
                }
                true
            }
        }

        fun setItemWidth(width: Int) {
            // Set the width for the CardView
            val layoutParams = cardView.layoutParams
            layoutParams.width = width
            cardView.layoutParams = layoutParams

            layoutParams.height = width // Set the same height as the width to maintain a square shape
            cardView.layoutParams = layoutParams
        }
    }

    override fun getItemViewType(position: Int): Int {
        val file = encryptedFiles[position]
        return if (file.name.endsWith(".mp4")) {
            TYPE_VIDEO
        } else {
            TYPE_IMAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_IMAGE -> {
                val view = inflater.inflate(R.layout.grid_item_layout, parent, false)
                PhotoViewHolder(view)
            }
            TYPE_VIDEO -> {
                val view = inflater.inflate(R.layout.video_item_layout, parent, false)
                VideoViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val encryptedFile = encryptedFiles[position]
        val isSelected = selectedItems.contains(position)

        when (getItemViewType(position)) {
            TYPE_IMAGE -> {
                // Safe cast to PhotoViewHolder
                if (holder is PhotoViewHolder) {
                    holder.setItemWidth(itemWidth)
                    holder.bind(encryptedFile, position, isSelected)
                }
            }
            TYPE_VIDEO -> {
                // Safe cast to VideoViewHolder
                if (holder is VideoViewHolder) {
                    holder.setItemWidth(itemWidth)
                    val thumbnailEncryptedFile = File(encryptedFile.parent, "${encryptedFile.nameWithoutExtension}_thumbnail.jpg")


                    holder.bind(thumbnailEncryptedFile, position, isSelected)
                }
            }
            else -> throw IllegalArgumentException("Invalid view type at position $position")
        }
    }

    private fun notifySelectedItemsChanged() {
        // Notify the affected items (selected items) that their state has changed
        selectedItems.forEach { position ->
            notifyItemChanged(position)
        }
        selectedItems.clear()
    }

    fun toggleSelection(position: Int) {
        if (selectedItems.contains(position)) {
            selectedItems.remove(position)
        } else {
            selectedItems.add(position)
        }
        notifyItemChanged(position)
        onSelectionChanged?.invoke()
    }

    fun getSelectedItems(): List<File> {
        return selectedItems.map { encryptedFiles[it] }
    }

    fun addFile(file: File) {
        encryptedFiles.add(file)
        notifyItemInserted(encryptedFiles.size - 1)
        FileManager.setFilePaths(encryptedFiles.map { it.absolutePath })
    }

    // Deletes selected files
    fun deleteSelectedFiles() {
        // Sort items from highest to lowest index to avoid index shifting during removal
        val sortedSelectedItems = selectedItems.sortedDescending()
        for (position in sortedSelectedItems) {
            val file = encryptedFiles[position]
            val encryptedFileName = file.name
            val albumPath = file.parent ?: ""
            if (file.exists() && file.delete()) {
                removeFileFromMetadata(albumPath, encryptedFileName)
                // Successfully deleted the file
                decryptedBitmaps.remove(file.name) // Remove from the cache
                encryptedFiles.removeAt(position)  // Remove from the list

                notifyItemRemoved(position)  // Notify the RecyclerView to update
            } else {
                // Handle failure if necessary, e.g., show a message to the user
                Log.e("Delete", "Failed to delete file: ${file.name}")
            }
        }
        selectedItems.clear()  // Clear the selection after deletion
    }

    fun moveSelectedFiles(destinationFolder: File) {
        // Read metadata from the current album
        val sourceAlbumPath = encryptedFiles[0].parent!!
        val metadataFile = File(sourceAlbumPath, "metadata.json")
        if (!metadataFile.exists()) {
            Log.e("Move", "Metadata file not found. Cannot move files.")
            return
        }

        // Parse metadata to extract file details
        val gson = Gson()
        val metadata = gson.fromJson(metadataFile.readText(), FileUtils.Metadata::class.java)
        val fileDetailsToMove = mutableListOf<FileDetail>()

        val sortedSelectedItems = selectedItems.sortedDescending()
        for (position in sortedSelectedItems) {
            val file = encryptedFiles[position]

            // Find the corresponding metadata entry for the selected file
            val metadataEntry = metadata.files.find { it.encryptedFileName == file.name }
            if (metadataEntry != null) {
                val fileDetail = FileDetail(
                    originalFileName = metadataEntry.originalFileName,
                    encryptedFileName = metadataEntry.encryptedFileName,
                    mimeType = metadataEntry.mimeType,
                    createdAt = metadataEntry.createdAt,
                    thumbnailFileName = metadataEntry.thumbnailFileName
                )
                fileDetailsToMove.add(fileDetail)
            } else {
                Log.e("Move", "Metadata entry not found for file: ${file.name}")
            }
        }

        // Move files and update metadata
        FileUtils.moveFilesAndUpdateMetadata(
            sourceAlbumPath = sourceAlbumPath,
            targetAlbumPath = destinationFolder.absolutePath,
            filesToMove = fileDetailsToMove
        )

        // Update adapter state
        for (position in sortedSelectedItems) {
            decryptedBitmaps.remove(encryptedFiles[position].name) // Remove from the cache
            encryptedFiles.removeAt(position) // Remove from the current list
            notifyItemRemoved(position)
        }

        selectedItems.clear() // Clear selection
        FileManager.setFilePaths(encryptedFiles.map { it.absolutePath })

        Log.d("Move", "Files moved and metadata updated.")
    }

    fun restoreSelectedFiles(context: Context) {
        val sortedSelectedItems = selectedItems.sortedDescending()

        adapterScope.launch {
            for (position in sortedSelectedItems) {
                val file = encryptedFiles[position]

                val success = withContext(Dispatchers.IO) {
                    if (file.exists()) {
                        EncryptionUtils.restorePhotoToDevice(file, context)
                    } else {
                        false
                    }
                }

                if (success) {
                    // Successfully restored the file
                    decryptedBitmaps.remove(file.name) // Remove from the cache
                    encryptedFiles.removeAt(position) // Remove from the list
                    notifyItemRemoved(position) // Notify the RecyclerView to update
                } else {
                    // Handle failure if necessary, e.g., show a message to the user
                    Log.e("Restore", "Failed to restore file: ${file.name}")
                }
            }
            onImageCountUpdated?.invoke(encryptedFiles.size)
        }

        selectedItems.clear()
        FileManager.setFilePaths(encryptedFiles.map { it.absolutePath })
    }

    fun updateFromFileManager() {
        encryptedFiles.clear()
        encryptedFiles.addAll(FileManager.getFilePaths().map { File(it) })
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = encryptedFiles.size

    fun cleanup() {
        adapterScope.cancel()
    }
}