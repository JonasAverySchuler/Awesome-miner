package com.example.calculatorsafe.adapters

import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.example.calculatorsafe.utils.EncryptionUtils.decryptImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

// Data class to represent image or video
sealed class MediaItemWrapper {
    data class Image(val path: String) : MediaItemWrapper()
    data class Video(val uri: Uri) : MediaItemWrapper()
}

class MediaPagerAdapter(
    private val mediaItems: MutableList<MediaItemWrapper>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val TAG = "MediaPagerAdapter"
        const val TYPE_IMAGE = 0
        const val TYPE_VIDEO = 1
    }

    inner class ImageViewHolder(itemView: View, private val imageView: ImageView, private val progressBar: ProgressBar) : RecyclerView.ViewHolder(itemView) {

        fun bind(imagePath: String) {
            // Decrypt and display the image
            val encryptedFile = File(imagePath)
            progressBar.visibility = View.VISIBLE
            imageView.visibility = View.GONE

            adapterScope.launch {
                val decryptedBitmap = decryptImage(encryptedFile, false)
                progressBar.visibility = View.GONE
                imageView.visibility = View.VISIBLE
                if (decryptedBitmap != null) {
                    imageView.setImageBitmap(decryptedBitmap)
                } else {
                    //TODO: add error handling
                }
            }
        }
    }

    // ViewHolder for Video
    inner class VideoViewHolder(itemView: PlayerView) : RecyclerView.ViewHolder(itemView) {
        private val playerView = itemView as PlayerView
        private var exoPlayer: ExoPlayer? = null

        fun bind(videoUri: Uri) {
            // Initialize ExoPlayer (Media3)
            exoPlayer = ExoPlayer.Builder(playerView.context).build()
            val mediaItem = androidx.media3.common.MediaItem.fromUri(videoUri)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
            playerView.player = exoPlayer
        }

        fun releasePlayer() {
            exoPlayer?.release()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (mediaItems[position]) {
            is MediaItemWrapper.Image -> TYPE_IMAGE
            is MediaItemWrapper.Video -> TYPE_VIDEO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_IMAGE -> {
                // Create a FrameLayout to hold both the ImageView and ProgressBar
                val frameLayout = FrameLayout(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                // Create and configure the ImageView
                val imageView = ImageView(parent.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                // Create and configure the ProgressBar
                val progressBar = ProgressBar(parent.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER // Center the ProgressBar in the FrameLayout
                    }
                    visibility = View.GONE // Initially hide the ProgressBar
                    isIndeterminate = true
                }

                // Add ImageView and ProgressBar to the FrameLayout
                frameLayout.addView(imageView)
                frameLayout.addView(progressBar)

                ImageViewHolder(frameLayout, imageView, progressBar)
            }
            TYPE_VIDEO -> {
                val playerView = PlayerView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                VideoViewHolder(playerView)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ImageViewHolder -> holder.bind((mediaItems[position] as MediaItemWrapper.Image).path)
            is VideoViewHolder -> holder.bind((mediaItems[position] as MediaItemWrapper.Video).uri)
        }
    }

    override fun getItemCount(): Int = mediaItems.size

    // Releasing resources when the ViewHolder is recycled
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            holder.releasePlayer()
        }
    }

    fun getFilePaths(): List<String> {
        return mediaItems.map { when (it) {
            is MediaItemWrapper.Image -> it.path
            is MediaItemWrapper.Video -> it.uri.toString()
        }}
    }

    // Handling file operations
    fun deleteFileAt(position: Int): String? {
        if (position in mediaItems.indices) {
            val deletedMediaItem = mediaItems.removeAt(position)
            notifyItemRemoved(position)
            return when (deletedMediaItem) {
                is MediaItemWrapper.Image -> deletedMediaItem.path
                is MediaItemWrapper.Video -> deletedMediaItem.uri.toString()
            }
        }
        return null
    }

    fun restoreMediaItem(position: Int) {
        val mediaItem = mediaItems[position]
        val file = File(when (mediaItem) {
            is MediaItemWrapper.Image -> mediaItem.path
            is MediaItemWrapper.Video -> mediaItem.uri.toString()
        })
        if (file.exists()) {
            file.delete()
        }
    }
}
