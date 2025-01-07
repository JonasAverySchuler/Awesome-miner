package com.example.calculatorsafe.activities

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.calculatorsafe.R
import com.example.calculatorsafe.helpers.PreferenceHelper
import com.example.calculatorsafe.utils.StringUtils.isPasswordValid

class PasscodeSetupActivity: AppCompatActivity() {

    companion object {
        const val TAG = "PassCodeSetupActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passcode_setup)


        val etPin = findViewById<EditText>(R.id.etPin)
        val tvError = findViewById<TextView>(R.id.tvError)
        val btnSave = findViewById<Button>(R.id.btnSave)

        btnSave.setOnClickListener {
            val pin = etPin.text.toString().trim()
            if (isPasswordValid(pin)) {
                // Save passcode logic
                tvError.visibility = View.INVISIBLE
                PreferenceHelper.setPasscode(this, pin)
                Toast.makeText(this, "Passcode set successfully!", Toast.LENGTH_SHORT).show()
                finish() // Go back or proceed to the next activity
            } else {
                // Show error
                tvError.visibility = View.VISIBLE
            }
        }

    }

}
