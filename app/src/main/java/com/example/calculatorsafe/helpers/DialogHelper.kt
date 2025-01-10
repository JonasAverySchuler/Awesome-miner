package com.example.calculatorsafe.helpers

import android.app.Dialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.calculatorsafe.R
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

    fun showEditTextDialog(
        context: Context,
        title: String,
        hint: String = "",
        positiveText: String = "OK",
        negativeText: String = "Cancel",
        onPositiveClick: (String) -> Unit,
        onNegativeClick: () -> Unit = {}
    ) {

        val dialog = Dialog(context)
        dialog.setContentView(R.layout.custom_dialog)
        dialog.setCancelable(true) // Allow cancel on outside touch if needed

        // Set dialog width and height as a percentage of the screen
        val window = dialog.window
        if (window != null) {
            val metrics = context.resources.displayMetrics
            val width = (metrics.widthPixels * 0.85).toInt() // 85% of screen width
            val height = (metrics.heightPixels * 0.5).toInt() // 50% of screen height

            window.setLayout(width, height) // Apply width and height
            window.setBackgroundDrawableResource(android.R.color.transparent) // Optional: Make corners visible
        }

        // Make the dialog rounded
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Access the views in the custom layout
        val dialogTitle = dialog.findViewById<TextView>(R.id.dialogTitle)
        val dialogEditText = dialog.findViewById<EditText>(R.id.dialogEditText)
        val dialogCancelButton = dialog.findViewById<Button>(R.id.dialogCancelButton)
        val dialogCreateButton = dialog.findViewById<Button>(R.id.dialogCreateButton)

        // Set the title dynamically
        dialogTitle.text = title

        // Set button click listeners
        dialogCancelButton.setOnClickListener {
            dialog.dismiss()
            onNegativeClick()
        }
        dialogCancelButton.text = negativeText
        dialogCreateButton.text = positiveText
        dialogCreateButton.setOnClickListener {
            val inputText = dialogEditText.text.toString()
            dialog.dismiss()
            onPositiveClick(inputText)
        }

        dialogEditText.apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            this.hint = hint
            filters = arrayOf(InputFilter.LengthFilter(40))
            setOnKeyListener { _, keyCode, event ->
                // Prevent Enter from adding a new line
                keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            }
        }

            dialog.show()
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