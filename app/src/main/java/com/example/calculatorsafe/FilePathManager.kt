package com.example.calculatorsafe

object FilePathManager {
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
}
