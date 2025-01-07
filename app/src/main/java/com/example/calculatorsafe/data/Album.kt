package com.example.calculatorsafe.data

data class Album(
    val name: String,
    var photoCount: Int,
    val albumID: String,
    val pathString: String = "") {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Album) return false
        return albumID == other.albumID // Compare by a unique identifier
    }

    override fun hashCode(): Int {
        return albumID.hashCode() // Use the same unique identifier
    }
}

