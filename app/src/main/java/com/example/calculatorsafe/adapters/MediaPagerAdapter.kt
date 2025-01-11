package com.example.calculatorsafe.adapters

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.example.calculatorsafe.utils.EncryptionUtils.decryptImage
import java.io.File

// Data class to represent image or video
sealed class MediaItemWrapper {
    data class Image(val path: String) : MediaItemWrapper()
    data class Video(val uri: Uri) : MediaItemWrapper()
}

class MediaPagerAdapter(
    private val mediaItems: MutableList<MediaItemWrapper>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TAG = "MediaPagerAdapter"
        const val TYPE_IMAGE = 0
        const val TYPE_VIDEO = 1
    }

    inner class ImageViewHolder(itemView: ImageView) : RecyclerView.ViewHolder(itemView) {
        private val imageView = itemView as ImageView

        fun bind(imagePath: String) {
            // Decrypt and display the image
            Log.e(TAG, "Image path bind: $imagePath")
            val encryptedFile = File(imagePath)
            val bitmap = decryptImage(encryptedFile)
            imageView.setImageBitmap(bitmap)
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
                val imageView = ImageView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                ImageViewHolder(imageView)
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
        Log.e(TAG, "Image path bind: $position")
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
