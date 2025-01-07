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

    fun isPasswordValid(passwordString: String): Boolean {
        // regex pattern for a 4 to 8 digit numeric code
        val pattern = Regex("^[0-9]{4,8}$")

        // Check if the passcode matches the regex pattern
        return pattern.matches(passwordString)
    }
}