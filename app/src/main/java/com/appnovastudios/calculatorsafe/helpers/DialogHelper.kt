package com.appnovastudios.calculatorsafe.helpers

import android.app.Dialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
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

        val dialog = Dialog(context)
        dialog.setContentView(R.layout.custom_confirmation_dialog)
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
        val dialogTitle = dialog.findViewById<TextView>(R.id.confirmationTitle)
        val dialogTextView = dialog.findViewById<TextView>(R.id.confirmationTextView)
        val dialogCancelButton = dialog.findViewById<Button>(R.id.confirmationCancelButton)
        val dialogCreateButton = dialog.findViewById<Button>(R.id.confirmationCreateButton)

        // Set the title dynamically
        dialogTitle.text = title
        dialogTextView.text = message

        // Set button click listeners
        dialogCancelButton.setOnClickListener {
            onNegativeClick()
            dialog.dismiss()
        }
        dialogCancelButton.text = negativeText
        dialogCreateButton.text = positiveText
        dialogCreateButton.setOnClickListener {
            onPositiveClick()
            dialog.dismiss()
        }

        dialog.show()
    }

    fun chooseAlbumDialog(
        context: Context,
        albums: List<Album>,  // List of albums to choose from
        title: String,
        onAlbumSelected: (Album) -> Unit
    ) {

        val dialog = Dialog(context)
        dialog.setContentView(R.layout.custom_single_choice_dialog)
        dialog.setCancelable(true)

        // Set dialog dimensions
        val window = dialog.window
        if (window != null) {
            val metrics = context.resources.displayMetrics
            val width = (metrics.widthPixels * 0.85).toInt()
            val height = (metrics.heightPixels * 0.5).toInt()
            window.setLayout(width, height)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }

        val albumNames = albums.map { it.name }.toTypedArray()
        // Set title
        val titleView = dialog.findViewById<TextView>(R.id.dialogTitle)
        titleView.text = title

        // Populate list with single-choice items
        val listView = dialog.findViewById<ListView>(R.id.dialogListView)
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_single_choice, albumNames)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        listView.setOnItemClickListener { adapterView, view, i, l ->
            val selectedAlbum = albums[i]
            onAlbumSelected(selectedAlbum)
            dialog.dismiss()
        }
        dialog.show()
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