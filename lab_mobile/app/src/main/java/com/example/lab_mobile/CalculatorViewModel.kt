package com.example.lab_mobile3

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import kotlin.math.*

enum class AppTheme(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    ORANGE("Orange"),
    BLUE("Blue")
}

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    
    private val _display = mutableStateOf("0")
    val display: State<String> = _display

    private val _isScientificExpanded = mutableStateOf(false)
    val isScientificExpanded: State<Boolean> = _isScientificExpanded

    private val _appTheme = mutableStateOf(loadLocalTheme())
    val appTheme: State<AppTheme> = _appTheme

    private val _history = mutableStateOf<List<String>>(emptyList())
    val history: State<List<String>> = _history

    private var isFinalResult = false
    private val db = FirebaseFirestore.getInstance()
    private val userId = "default_user"

    init {
        loadSettingsFromFirebase()
        loadHistory()
    }

    private fun loadLocalTheme(): AppTheme {
        val savedTheme = prefs.getString("theme", AppTheme.SYSTEM.name)
        return try {
            AppTheme.valueOf(savedTheme!!)
        } catch (e: Exception) {
            AppTheme.SYSTEM
        }
    }

    private fun saveLocalTheme(theme: AppTheme) {
        prefs.edit().putString("theme", theme.name).apply()
    }

    private fun loadSettingsFromFirebase() {
        viewModelScope.launch {
            try {
                val document = db.collection("settings").document(userId).get().await()
                val themeName = document.getString("theme")
                if (themeName != null) {
                    val firebaseTheme = AppTheme.valueOf(themeName)
                    if (_appTheme.value != firebaseTheme) {
                        _appTheme.value = firebaseTheme
                        saveLocalTheme(firebaseTheme)
                    }
                }
            } catch (e: Exception) {
                Log.e("CalculatorViewModel", "Error loading settings from Firebase", e)
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        _appTheme.value = theme
        saveLocalTheme(theme)
        viewModelScope.launch {
            try {
                db.collection("settings").document(userId).set(mapOf("theme" to theme.name)).await()
            } catch (e: Exception) {
                Log.e("CalculatorViewModel", "Error saving theme to Firebase", e)
            }
        }
    }

    fun toggleScientific() {
        _isScientificExpanded.value = !_isScientificExpanded.value
    }

    fun onDigitClick(digit: String) {
        if (_display.value == "0" || isFinalResult) {
            _display.value = digit
            isFinalResult = false
        } else {
            _display.value += digit
        }
    }

    fun onOperatorClick(op: String) {
        if (isFinalResult) {
            isFinalResult = false
        }
        val current = _display.value
        if (current.isNotEmpty() && !isOperator(current.last().toString())) {
            _display.value += op
        } else if (current.isNotEmpty()) {
            _display.value = current.dropLast(1) + op
        }
    }

    private fun isOperator(s: String): Boolean {
        return s == "+" || s == "-" || s == "*" || s == "/" || s == "×" || s == "÷" || s == "^"
    }

    fun onEqualClick() {
        try {
            val expression = _display.value
            val evalExpr = expression.replace("×", "*").replace("÷", "/")
            val result = evaluate(evalExpr)
            val formattedResult = formatResult(result)
            
            saveToHistory("$expression = $formattedResult")
            
            _display.value = formattedResult
            isFinalResult = true
        } catch (e: Exception) {
            _display.value = "Error"
            isFinalResult = true
        }
    }

    private fun saveToHistory(entry: String) {
        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "entry" to entry,
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("history").add(data).await()
                loadHistory()
            } catch (e: Exception) {
                Log.e("CalculatorViewModel", "Error saving history", e)
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("history")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(10)
                    .get()
                    .await()
                _history.value = snapshot.documents.mapNotNull { it.getString("entry") }
            } catch (e: Exception) {
                Log.e("CalculatorViewModel", "Error loading history", e)
            }
        }
    }

    fun onClearClick() {
        _display.value = "0"
        isFinalResult = false
    }

    fun onDecimalClick() {
        if (isFinalResult) {
            _display.value = "0."
            isFinalResult = false
            return
        }
        val lastNumber = _display.value.split(Regex("[\\+\\-\\×\\÷\\*\\/\\^]")).last()
        if (!lastNumber.contains(".")) {
            _display.value += "."
        }
    }

    fun onPercentClick() {
        try {
            val expression = _display.value.replace("×", "*").replace("÷", "/")
            val result = evaluate(expression) / 100.0
            _display.value = formatResult(result)
            isFinalResult = true
        } catch (e: Exception) {
            _display.value = "Error"
            isFinalResult = true
        }
    }

    fun onSignClick() {
        if (isFinalResult) return
        val current = _display.value
        if (current == "0") return
        if (current.startsWith("-")) {
            _display.value = current.substring(1)
        } else {
            _display.value = "-$current"
        }
    }

    fun onPiClick() {
        val piVal = PI.toString()
        if (_display.value == "0" || isFinalResult) {
            _display.value = piVal
            isFinalResult = false
        } else {
            val lastChar = _display.value.last().toString()
            if (isOperator(lastChar)) {
                _display.value += piVal
            } else {
                // Если вставляем PI после числа, автоматически добавляем умножение
                _display.value += "*$piVal"
            }
        }
    }

    fun onSqrtClick() {
        try {
            val expression = _display.value.replace("×", "*").replace("÷", "/")
            val currentVal = evaluate(expression)
            if (currentVal >= 0) {
                _display.value = formatResult(sqrt(currentVal))
                isFinalResult = true
            } else {
                _display.value = "Error"
                isFinalResult = true
            }
        } catch (e: Exception) {
            _display.value = "Error"
            isFinalResult = true
        }
    }

    fun onFactorialClick() {
        try {
            val expression = _display.value.replace("×", "*").replace("÷", "/")
            val currentVal = evaluate(expression)
            if (currentVal >= 0 && currentVal == floor(currentVal) && currentVal <= 170) {
                var res = 1.0
                for (i in 1..currentVal.toInt()) {
                    res *= i
                }
                _display.value = formatResult(res)
                isFinalResult = true
            } else {
                _display.value = "Error"
                isFinalResult = true
            }
        } catch (e: Exception) {
            _display.value = "Error"
            isFinalResult = true
        }
    }

    private fun formatResult(result: Double): String {
        if (result.isInfinite() || result.isNaN()) return "Error"
        val absoluteResult = abs(result)
        
        val symbols = DecimalFormatSymbols(Locale.US)
        if (absoluteResult >= 1e9 || (absoluteResult > 0 && absoluteResult < 1e-4)) {
            val df = DecimalFormat("0.######E0", symbols)
            return df.format(result)
        }
        
        return if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            // Используем Locale.US для точки вместо запятой
            val s = String.format(Locale.US, "%.8f", result).trimEnd('0').trimEnd('.')
            if (s.length > 12) s.take(12) else s
        }
    }

    private fun evaluate(expression: String): Double {
        if (expression.isEmpty()) return 0.0
        val cleanExpr = expression.replace(" ", "")
        return object : Any() {
            var pos = -1
            var ch = 0
            fun nextChar() {
                ch = if (++pos < cleanExpr.length) cleanExpr[pos].toInt() else -1
            }
            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.toInt()) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }
            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < cleanExpr.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }
            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.toInt())) x += parseTerm()
                    else if (eat('-'.toInt())) x -= parseTerm()
                    else return x
                }
            }
            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.toInt())) x *= parseFactor()
                    else if (eat('/'.toInt())) x /= parseFactor()
                    else return x
                }
            }
            fun parseFactor(): Double {
                if (eat('+'.toInt())) return parseFactor()
                if (eat('-'.toInt())) return -parseFactor()
                var x: Double
                val startPos = pos
                if (eat('('.toInt())) {
                    x = parseExpression()
                    eat(')'.toInt())
                } else if (ch >= '0'.toInt() && ch <= '9'.toInt() || ch == '.'.toInt()) {
                    while (ch >= '0'.toInt() && ch <= '9'.toInt() || ch == '.'.toInt()) nextChar()
                    x = cleanExpr.substring(startPos, pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }
                if (eat('^'.toInt())) x = x.pow(parseFactor())
                return x
            }
        }.parse()
    }
}
