package com.example.calculatorsafe

import android.content.Context
import com.example.calculatorsafe.data.Album
import com.example.calculatorsafe.helpers.PreferenceHelper.getAlbumId
import java.io.File

object FileManager {
    private val filePaths = mutableListOf<String>()

    // Add file paths
    fun setFilePaths(paths: List<String>) {
        filePaths.clear()
        filePaths.addAll(paths)
    }

    // Retrieve all file paths
    fun getFilePaths(): List<String> = filePaths

    // Get a specific file path by index
    fun getFilePath(index: Int): String? = filePaths.getOrNull(index)

    // Size of the file paths list
    fun getSize(): Int = filePaths.size

    fun getAlbums(context: Context): List<Album> {
        // Get the directory where albums are stored (adjust path as necessary)
        val albumsDir = File(context.filesDir, "Albums") // Or wherever your albums are stored

        // Get the directories inside the albumsDir (only directories are albums)
        val albumDirs = albumsDir.listFiles { file -> file.isDirectory } ?: return emptyList()

        // Map each directory to an Album object
        return albumDirs.map { dir ->
            val photoCount = getImageFileCountFromAlbum(dir) // Get photo count for this album
            val albumId = getAlbumId(context, dir.name) ?: "" // Get album ID (if available)
            Album(
                name = dir.name,
                photoCount = photoCount,
                albumID = albumId,
                pathString = dir.absolutePath // Path to the album directory
            )
        }
    }

    // Helper function to get the number of image files in an album directory
    private fun getImageFileCountFromAlbum(albumDir: File): Int {
        // Adjust this based on your actual image file extension(s)
        val imageFiles = albumDir.listFiles { file ->
            file.isFile && (file.extension == "jpg" || file.extension == "png" || file.extension == "enc")
        }
        return imageFiles?.size ?: 0
    }

}
