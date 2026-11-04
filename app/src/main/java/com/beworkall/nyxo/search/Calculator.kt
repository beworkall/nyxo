package com.beworkall.nyxo.search

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/** Offline calculator for the search bar: + - * / ^ %, brackets, and "15% of 2400". */
object Calculator {

    private val percentOf = Regex("""^(\d+(?:\.\d+)?)\s*%\s*of\s*(\d+(?:\.\d+)?)$""")

    fun evaluate(input: String): Double? {
        val s = input.trim().lowercase()
            .replace('×', '*').replace('÷', '/').replace('x', '*')
            .replace(",", "")
        if (s.isEmpty()) return null

        val ofSource = input.trim().lowercase().replace(",", "")
        percentOf.matchEntire(ofSource)?.let { m ->
            val a = m.groupValues[1].toDoubleOrNull() ?: return null
            val b = m.groupValues[2].toDoubleOrNull() ?: return null
            return a * b / 100.0
        }

        // Only treat it as maths if it really looks like maths.
        if (s.none { it in "+-*/^%" }) return null
        if (s.any { !(it.isDigit() || it in "+-*/^%(). ") }) return null
        if (s.none { it.isDigit() }) return null

        val compact = s.replace(" ", "")
        val value = runCatching { Parser(compact).parse() }.getOrNull() ?: return null
        return if (value.isNaN() || value.isInfinite()) null else value
    }

    fun format(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return v.toString()
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val v = expr()
            if (i != s.length) throw IllegalArgumentException("trailing input")
            return v
        }

        private fun peek(): Char? = if (i < s.length) s[i] else null

        private fun expr(): Double {
            var v = term()
            while (true) {
                when (peek()) {
                    '+' -> { i++; v += term() }
                    '-' -> { i++; v -= term() }
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = power()
            while (true) {
                when (peek()) {
                    '*' -> { i++; v *= power() }
                    '/' -> { i++; v /= power() }
                    else -> return v
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            if (peek() == '^') {
                i++
                return base.pow(power())
            }
            return base
        }

        private fun unary(): Double {
            return when (peek()) {
                '-' -> { i++; -unary() }
                '+' -> { i++; unary() }
                else -> postfix()
            }
        }

        private fun postfix(): Double {
            var v = primary()
            while (peek() == '%') { i++; v /= 100.0 }
            return v
        }

        private fun primary(): Double {
            if (peek() == '(') {
                i++
                val v = expr()
                if (peek() != ')') throw IllegalArgumentException("missing )")
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalArgumentException("number expected")
            return s.substring(start, i).toDouble()
        }
    }
}

/** "10 km to mi", "70 f in c", "2 gb to mb". */
object UnitConverter {
    private data class UnitDef(val dim: String, val factor: Double)

    private val units: Map<String, UnitDef> = HashMap<String, UnitDef>().apply {
        fun add(dim: String, factor: Double, vararg names: String) {
            for (n in names) put(n, UnitDef(dim, factor))
        }
        add("length", 0.001, "mm")
        add("length", 0.01, "cm")
        add("length", 1.0, "m", "meter", "meters", "metre", "metres")
        add("length", 1000.0, "km")
        add("length", 0.0254, "in", "inch", "inches")
        add("length", 0.3048, "ft", "foot", "feet")
        add("length", 0.9144, "yd", "yard", "yards")
        add("length", 1609.344, "mi", "mile", "miles")
        add("mass", 0.000001, "mg")
        add("mass", 0.001, "g", "gram", "grams")
        add("mass", 1.0, "kg")
        add("mass", 0.45359237, "lb", "lbs", "pound", "pounds")
        add("mass", 0.028349523125, "oz")
        add("mass", 1000.0, "t", "ton", "tonne")
        add("volume", 0.001, "ml")
        add("volume", 1.0, "l", "liter", "litre", "liters", "litres")
        add("volume", 3.785411784, "gal", "gallon", "gallons")
        add("data", 1.0, "b", "byte", "bytes")
        add("data", 1024.0, "kb")
        add("data", 1048576.0, "mb")
        add("data", 1073741824.0, "gb")
        add("data", 1099511627776.0, "tb")
        add("speed", 1.0, "kmh", "kph", "km/h")
        add("speed", 1.609344, "mph")
    }

    private val pattern = Regex("""^(-?\d+(?:\.\d+)?)\s*([a-z°/]+)\s+(?:to|in|into)\s+([a-z°/]+)$""")

    fun convert(query: String): String? {
        val m = pattern.matchEntire(query.trim().lowercase()) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        val from = m.groupValues[2]
        val to = m.groupValues[3]

        val tFrom = tempKey(from)
        val tTo = tempKey(to)
        if (tFrom != null && tTo != null) {
            val celsius = when (tFrom) {
                'c' -> v
                'f' -> (v - 32.0) * 5.0 / 9.0
                else -> v - 273.15
            }
            val r = when (tTo) {
                'c' -> celsius
                'f' -> celsius * 9.0 / 5.0 + 32.0
                else -> celsius + 273.15
            }
            return "${Calculator.format(v)} ${tempName(tFrom)} = ${Calculator.format(r)} ${tempName(tTo)}"
        }

        val a = units[from] ?: return null
        val b = units[to] ?: return null
        if (a.dim != b.dim) return null
        val r = v * a.factor / b.factor
        return "${Calculator.format(v)} $from = ${Calculator.format(r)} $to"
    }

    private fun tempKey(s: String): Char? = when (s.removePrefix("°")) {
        "c", "celsius" -> 'c'
        "f", "fahrenheit" -> 'f'
        "k", "kelvin" -> 'k'
        else -> null
    }

    private fun tempName(c: Char): String = when (c) {
        'c' -> "°C"
        'f' -> "°F"
        else -> "K"
    }
}
