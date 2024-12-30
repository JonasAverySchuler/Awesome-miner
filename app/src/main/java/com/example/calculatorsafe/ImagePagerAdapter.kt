package com.example.calculatorsafe

import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class ImagePagerAdapter(
    private val imagePaths: List<String>
) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: ImageView) : RecyclerView.ViewHolder(itemView) {
        private val imageView = itemView as ImageView

        fun bind(imagePath: String) {
            // Decrypt and display the image
            //val bitmap = decryptImage(imagePath)
            //imageView.setImageBitmap(bitmap)
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
}
