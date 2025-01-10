package com.example.calculatorsafe.activities

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.calculatorsafe.R
import com.example.calculatorsafe.helpers.DialogHelper
import com.example.calculatorsafe.helpers.PreferenceHelper
import com.example.calculatorsafe.utils.StringUtils.isPasswordValid

class PasscodeSetupActivity: AppCompatActivity() {

    companion object {
        const val TAG = "PassCodeSetupActivity"
    }

    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passcode_setup)

        val etPin = findViewById<EditText>(R.id.etPin)
        tvError = findViewById(R.id.tvError)
        val btnSave = findViewById<Button>(R.id.btnSave)

        btnSave.setOnClickListener {
            val pin = etPin.text.toString().trim()
            if (isPasswordValid(pin)) {
                // Save passcode logic
                DialogHelper.showConfirmationDialog(
                    this,
                    "Confirm Passcode",
                    "Are you sure you want to set this passcode? This action cannot be undone.\n Passcode: $pin \n Make sure to remember this passcode or risk losing media!",
                    "Confirm",
                    "Cancel",
                    onPositiveClick = { onPasscodeConfirmed(pin) })
            } else {
                // Show error
                tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun onPasscodeConfirmed(pin: String) {
        tvError.visibility = View.INVISIBLE
        PreferenceHelper.setPasscode(this, pin)
        Toast.makeText(this, "Passcode set successfully!", Toast.LENGTH_SHORT).show()
        finish() // Go back or proceed to the next activity
    }

}
