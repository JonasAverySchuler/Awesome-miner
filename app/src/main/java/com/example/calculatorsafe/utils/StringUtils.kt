package com.example.calculatorsafe.utils

object StringUtils {

    fun isValidAlbumName(albumName: String): Boolean {
        // Check if the album name is not empty
        if (albumName.isEmpty()) {
            return false
        }

        // Set a reasonable character limit (e.g., 50 characters)
        if (albumName.length > 50) {
            return false
        }

        // Disallow multiple spaces in a row
        if (albumName.contains("  ")) {
            return false
        }

        // Check for invalid characters (only letters, numbers, and spaces allowed)
        val regex = "^[a-zA-Z0-9 ]*$".toRegex()
        if (!albumName.matches(regex)) {
            return false
        }

        return true
    }
}