package com.example.calculatorsafe.helpers

import android.content.Context
import androidx.appcompat.app.AlertDialog

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