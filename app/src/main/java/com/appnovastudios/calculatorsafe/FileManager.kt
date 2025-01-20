package com.example.calculatorsafe

import android.content.Context
import com.example.calculatorsafe.data.Album
import com.example.calculatorsafe.utils.FileUtils
import com.google.gson.Gson
import java.io.File

object FileManager {
    private val filePaths = mutableListOf<String>()

    // Add file paths
    fun setFilePaths(paths: List<String>) {
        filePaths.clear()
        filePaths.addAll(paths)
    }

    fun getFilePaths(): List<String> = filePaths

    fun getAlbums(context: Context): List<Album> {
        val albumsDir = File(context.filesDir, "Albums") // Or wherever your albums are stored

        // Get the directories inside the albumsDir (only directories are albums)
        val albumDirs = albumsDir.listFiles { file -> file.isDirectory } ?: return emptyList()

        // Map each directory to an Album object
        return albumDirs.map { dir ->
            val metadataFile = File(dir, "metadata.json")

            // If metadata file exists, parse it
            val album = if (metadataFile.exists()) {
                val gson = Gson()
                val metadata = gson.fromJson(metadataFile.readText(), FileUtils.Metadata::class.java)
                val photoCount = metadata.files.size // Count files in metadata
                val albumId = metadata.albumName ?: "" // Get album name or default to empty string
                Album(
                    name = metadata.albumName ?: dir.name, // Use album name from metadata or fallback to directory name
                    photoCount = photoCount,
                    albumID = albumId,
                    pathString = dir.absolutePath // Path to the album directory
                )
            } else {
                // If metadata file does not exist, use default logic
                val photoCount = getImageFileCountFromAlbum(dir) // Get photo count from directory
                Album(
                    name = dir.name,
                    photoCount = photoCount,
                    albumID = "", // No album ID if metadata does not exist
                    pathString = dir.absolutePath
                )
            }
            album
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
