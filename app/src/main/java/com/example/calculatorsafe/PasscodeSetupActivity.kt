package com.example.calculatorsafe

import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.example.calculatorsafe.helpers.PreferenceHelper

class PasscodeSetupActivity: AppCompatActivity() {

    companion object {
        const val TAG = "PassCodeSetupActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        setContentView(R.layout.activity_passcode_setup)

        // Handle passcode setup UI and logic here
        // After passcode setup is complete, save the flag

        val passwordCreationEditText: EditText = findViewById(R.id.editText_password)
        val submissionButton: Button = findViewById(R.id.button_submit)

        submissionButton.setOnClickListener {
           if( isPasswordValid(passwordCreationEditText.text?.toString() ?: "")) {
               PreferenceHelper.setPasscode(this, passwordCreationEditText.text?.toString()?: "")
               //show password set, remind them to save and not forget
               finish()
           } else {
               //show that the passcode is not valid and show rules
           }
        }

        // Optionally, navigate back to the main activity
        // val intent = Intent(this, MainActivity::class.java)
        // startActivity(intent)
        // finish()
    }

    private fun isPasswordValid(passwordString: String): Boolean {
        // Define the regex pattern for passcode validation
        val pattern = Regex("\\d{1,6}")

        // Check if the passcode matches the regex pattern
        return pattern.matches(passwordString)
    }
}
