package com.example.calculatorsafe.helpers

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.example.calculatorsafe.data.Album

object DialogHelper {

    fun showConfirmationDialog(
        context: Context,
        title: String,
        message: String,
        positiveText: String = "Yes",
        negativeText: String = "No",
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit = {}
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onPositiveClick() }
            .setNegativeButton(negativeText) { _, _ -> onNegativeClick() }
            .show()
    }

    fun chooseAlbumDialog(
        context: Context,
        albums: List<Album>,  // List of albums to choose from
        title: String,
        onAlbumSelected: (Album) -> Unit
    ) {
        // Extracting album names from the list to display in the dialog
        val albumNames = albums.map { it.name }.toTypedArray()
        Log.e("chooseAlbumDialog", "albums = $albums albumNames: ${albumNames.joinToString(", ")}")
        AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(albumNames, -1) { dialog, which ->
                // `which` is the index of the selected album
                val selectedAlbum = albums[which]
                onAlbumSelected(selectedAlbum)
                dialog.dismiss()  // Dismiss the dialog after selection
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    fun showInfoDialog(
        context: Context,
        title: String,
        message: String,
        buttonText: String = "OK",
        onButtonClick: () -> Unit = {}
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonText) { _, _ -> onButtonClick() }
            .show()
    }
}