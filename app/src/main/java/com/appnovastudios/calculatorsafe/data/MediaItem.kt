package com.appnovastudios.calculatorsafe.data

import android.net.Uri

sealed class MediaItemWrapper {
    data class Image(val path: String) : MediaItemWrapper()
    data class Video(val uri: Uri) : MediaItemWrapper()
}
