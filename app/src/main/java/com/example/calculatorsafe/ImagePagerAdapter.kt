package com.example.calculatorsafe

import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.calculatorsafe.utils.EncryptionUtils.decryptImage
import java.io.File

class ImagePagerAdapter(
    private val imagePaths: MutableList<String>
) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: ImageView) : RecyclerView.ViewHolder(itemView) {
        private val imageView = itemView as ImageView

        fun bind(imagePath: String) {
            // Decrypt and display the image
            val encryptedFile = File(imagePath)
            val bitmap = decryptImage(encryptedFile)
            imageView.setImageBitmap(bitmap)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        return ImageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(imagePaths[position])
    }

    override fun getItemCount(): Int = imagePaths.size

    fun getFilePaths(): List<String> {
        return imagePaths
    }

    fun deleteFileAt(position: Int): String? {
        if (position in imagePaths.indices) {
            val deletedImagePath = imagePaths.removeAt(position)
            notifyItemRemoved(position)
            return deletedImagePath
        }
        return null
    }

    fun restoreImage(position: Int) {
        val imagePath = imagePaths[position]
        val file = File(imagePath)
        if (file.exists()) {
            file.delete()
        }
    }
}
