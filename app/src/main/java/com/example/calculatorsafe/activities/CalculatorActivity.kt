package com.example.calculatorsafe.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.calculatorsafe.R
import com.example.calculatorsafe.helpers.PreferenceHelper
import java.util.Locale
import kotlin.math.pow

class CalculatorActivity : AppCompatActivity() {
    lateinit var displayTextView: TextView
    private var currentInput: String = ""
    private var operator: String? = null
    private var operand1: Double? = null
    private val maxDisplayLength = 12

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.calculator_layout)
        displayTextView = findViewById(R.id.display)

        val buttons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,
            R.id.btnPlus, R.id.btnMinus, R.id.btnTimes, R.id.btnDiv,
            R.id.btnEquals, R.id.btnClear,R.id.btnPercent,R.id.btnRoot, R.id.btnPoint
        )

        buttons.forEach { id ->
            val view = findViewById<View>(id)
            when (view) {
                is Button -> {
                    // For regular Buttons
                    view.setOnClickListener { onButtonClick(view) }
                }
                is ImageButton -> {
                    // For ImageButtons
                    view.setOnClickListener { onButtonClick(view) }
                }
            }
        }
        val isPasscodeSet = PreferenceHelper.isPasscodeSet(this)
        if (!isPasscodeSet) {
            Toast.makeText(this, "Please set a passcode", Toast.LENGTH_SHORT).show()
            launchPasscodeSetupActivity()
            //finish()
        }
    }

    private fun launchPasscodeSetupActivity() {
        val intent = Intent(this, PasscodeSetupActivity::class.java)
        startActivity(intent)
    }

    private fun clearInput() {
        currentInput = ""
        operator = null
        operand1 = null
        displayTextView.text = "0"
    }

    private fun onButtonClick(view: View) {
        when (view.id) {
            R.id.btnClear -> clearInput()
            R.id.btnEquals -> {
                if(validatePasscode(currentInput)){
                    passcodeSuccess()
                } else {
                    calculateResult()
                }
            }
            else -> handleInput(view)
        }
    }

    private fun handleInput(view: View) {
        val input = when (view) {
            is Button -> view.text.toString()
            is ImageButton -> {
                // You can use the content description or some other way to identify the operation
                view.contentDescription.toString()
            }
            else -> return
        }

        when (input) {
            in "0123456789" -> {
                currentInput += input
                displayTextView.text = currentInput
            }
            in "+-*/" -> {
                operator = input
                operand1 = currentInput.toDoubleOrNull()
                currentInput = ""
            }
            "%" -> {
                // Percent operation
                operand1 = currentInput.toDoubleOrNull()
                if (operand1 != null) {
                    val result = operand1!! / 100
                    displayResult(result)
                }
            }
            "x^" -> {
                // Superscript operation: Wait for the exponent input
                operator = "x^"
                operand1 = currentInput.toDoubleOrNull()
                currentInput = ""
            }
            "." -> {
                if (currentInput.isEmpty()) {
                    // If input is empty, add "0."
                    currentInput = "0."
                } else if (!currentInput.contains(".")) {
                    // Allow decimal point only if it doesn't already exist
                    currentInput += "."
                }
                displayTextView.text = currentInput
        }

            else -> {
                validatePasscode(input)
            }
        }
    }

    private fun validatePasscode(input: String): Boolean {
        val savedPasscode = PreferenceHelper.getPasscode(this)
        return input == savedPasscode
    }

    private fun displayResult(value: Double) {
        val resultString = formatResult(value)
        if (resultString.length > maxDisplayLength) {
            // Handle overflow
            displayTextView.text = "Overflow"
            currentInput = ""
        } else {
            displayTextView.text = resultString
            currentInput = resultString
        }
    }

    private fun calculateResult() {
        val operand2 = currentInput.toDoubleOrNull()
        if (operand1 != null && operand2 != null && operator != null) {
            val result = when (operator) {
                "+" -> operand1!! + operand2
                "-" -> operand1!! - operand2
                "*" -> operand1!! * operand2
                "/" -> operand1!! / operand2
                "%" -> operand1!! * (operand2 / 100)
                "X^" -> operand1!!.pow(operand2)
                else -> 0.0
            }
            displayResult(result)
            operand1 = null
            operator = null
        }
    }

    private fun formatResult(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString() // If whole number, display without decimals
        } else {
            String.format(Locale.US, "%.4f", value) // Show up to 4 decimals
        }
    }

    private fun passcodeSuccess(){
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
