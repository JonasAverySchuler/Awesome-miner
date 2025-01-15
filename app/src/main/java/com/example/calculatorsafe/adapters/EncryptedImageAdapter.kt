package com.example.calculatorsafe.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.calculatorsafe.FileManager
import com.example.calculatorsafe.R
import com.example.calculatorsafe.utils.EncryptionUtils
import com.example.calculatorsafe.utils.EncryptionUtils.decryptImage
import com.example.calculatorsafe.utils.FileUtils.removeFileFromMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EncryptedImageAdapter(
    private val encryptedFiles: MutableList<File>,
    private val itemWidth: Int,
    private val onPhotoClick: (Int) -> Unit,
    private val onEnterSelectionMode: () -> Unit
) : RecyclerView.Adapter<EncryptedImageAdapter.PhotoViewHolder>() {

    enum class Mode {
        VIEWING,
        SELECTION
    }

    var mode = Mode.VIEWING
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val selectedItems = mutableSetOf<Int>()
    var onSelectionChanged: (() -> Unit)? = null

    inner class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView: CardView = view.findViewById(R.id.card_view)
        private val photoImageView: ImageView = view.findViewById(R.id.photo_image_view)
        private val overlay: View = itemView.findViewById(R.id.overlay) // A semi-transparent View
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar) // A ProgressBar

        fun bind(file: File, position: Int, isSelected: Boolean) {

            adapterScope.launch {
                progressBar.visibility = View.VISIBLE
                val decryptedBitmap = withContext(Dispatchers.IO) {
                    decryptImage(file)
                }
                progressBar.visibility = View.GONE
                if (decryptedBitmap != null) {
                    photoImageView.setImageBitmap(decryptedBitmap)
                } else {
                    // Handle the case where decryption failed //TODO: add error handling
                    Log.e("EncryptedImageAdapter", "Decryption failed for file: ${file.name}")
                }
            }

            // Show or hide the selection overlay
            overlay.visibility = if (isSelected) View.VISIBLE else View.GONE
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.grid_item_layout, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.setItemWidth(itemWidth)
        val encryptedFile = encryptedFiles[position]
        holder.bind(encryptedFile, position, selectedItems.contains(position))
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
        val sortedSelectedItems = selectedItems.sortedDescending()
        //TODO: shift metadata
        for (position in sortedSelectedItems) {
            val file = encryptedFiles[position]
            val destinationFile = File(destinationFolder, file.name)

            // Move the file
            if (file.exists() && file.renameTo(destinationFile)) {
                // Successfully moved the file
                encryptedFiles.removeAt(position)  // Remove from the current list
                notifyItemRemoved(position)

                // Optionally, add the moved file to a new list (if applicable)
                // encryptedFiles.add(destinationFile)

                Log.d("Move", "Successfully moved file: ${file.name}")
            } else {
                // Handle failure if necessary, e.g., show a message to the user
                Log.e("Move", "Failed to move file: ${file.name}")
            }
        }

        selectedItems.clear()  // Clear the selection after moving the files
        FileManager.setFilePaths(encryptedFiles.map { it.absolutePath })
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
                    encryptedFiles.removeAt(position) // Remove from the list
                    notifyItemRemoved(position) // Notify the RecyclerView to update
                } else {
                    // Handle failure if necessary, e.g., show a message to the user
                    Log.e("Restore", "Failed to restore file: ${file.name}")
                }
            }
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