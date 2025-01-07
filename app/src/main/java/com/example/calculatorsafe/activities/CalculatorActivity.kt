package com.example.calculatorsafe.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.calculatorsafe.R

class CalculatorActivity : AppCompatActivity() {
    lateinit var displayTextView: TextView
    private var currentInput: String = ""
    private var operator: String? = null
    private var operand1: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.calculator_layout)
        displayTextView = findViewById(R.id.display)

        //TODO: add logic for passcode, finalize UI, handle intent to enter app here and into main activity on successful passcode

        val buttons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,
            R.id.btnPlus, R.id.btnMinus, R.id.btnTimes, R.id.btnDiv,
            R.id.btnEquals, R.id.btnClear,
           // R.id.btnImagePlus, R.id.btnImageMinus // Add ImageButton IDs here
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
            R.id.btnEquals -> calculateResult()
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

        Log.e("CalculatorActivity", "Handling input: $input")
        if (input in "0123456789") {
            currentInput += input
            displayTextView.text = currentInput
        } else if (input in "+-*/") {
            operator = input
            operand1 = currentInput.toDoubleOrNull()
            currentInput = ""
        }
    }

    private fun calculateResult() {
        //TODO: if is the password go to vault
        val operand2 = currentInput.toDoubleOrNull()
        if (operand1 != null && operand2 != null && operator != null) {
            val result = when (operator) {
                "+" -> operand1!! + operand2
                "-" -> operand1!! - operand2
                "*" -> operand1!! * operand2
                "/" -> operand1!! / operand2
                else -> 0.0
            }
            displayTextView.text = result.toString()
            currentInput = result.toString()
            operand1 = null
            operator = null
        }
    }

    private fun passcodeSuccess(){
        val intent = Intent()
        startActivity(intent)
        finish()
    }
}
