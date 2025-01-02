package com.example.calculatorsafe

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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
            R.id.btnPlus, R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide,
            R.id.btnEquals, R.id.btnClear
        )

        buttons.forEach { id ->
            findViewById<Button>(id).setOnClickListener { onButtonClick(it as Button) }
        }
    }

    private fun clearInput() {
        currentInput = ""
        operator = null
        operand1 = null
        displayTextView.text = "0"
    }

    private fun onButtonClick(button: Button) {
        when (button.id) {
            R.id.btnClear -> clearInput()
            R.id.btnEquals -> calculateResult()
            else -> handleInput(button.text.toString())
        }
    }

    private fun handleInput(input: String) {
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