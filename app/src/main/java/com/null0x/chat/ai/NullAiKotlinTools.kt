package com.null0x.chat.ai

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.pow

data class NullAiKotlinResult(
    val kind: String,
    val answer: String,
    val context: String
)

object NullAiKotlinTools {
    private val numberRegex = Regex("-?\\d+(?:[.,]\\d+)?")
    private val expressionChars = Regex("^[\\d\\s+\\-*/^().,%]+$")

    fun analyze(rawMessage: String): NullAiKotlinResult? {
        val message = rawMessage.trim()
        if (message.isBlank()) return null
        val normalized = normalize(message)
        val asksTextCount = containsAny(
            normalized,
            "quantas letras",
            "contar letras",
            "quantos caracteres",
            "contar caracteres",
            "quantas palavras",
            "contar palavras"
        )
        if (!message.any { it.isDigit() } && !asksTextCount) return null
        return percentage(message)
            ?: aggregateNumbers(message)
            ?: unitConversion(message)
            ?: dateOffset(message)
            ?: textAnalysis(message)
            ?: mathExpression(message)
    }

    private fun percentage(message: String): NullAiKotlinResult? {
        val normalized = normalize(message)
        val percentOf = Regex("(\\d+(?:[.,]\\d+)?)\\s*%\\s*(?:de|do|da)\\s*(-?\\d+(?:[.,]\\d+)?)")
            .find(normalized)
        if (percentOf != null) {
            val percent = decimal(percentOf.groupValues[1])
            val base = decimal(percentOf.groupValues[2])
            val result = base.multiply(percent).divide(BigDecimal("100"), MathContext.DECIMAL64)
            return result("porcentagem", "${format(result)}", "Calculo Kotlin: ${format(percent)}% de ${format(base)} = ${format(result)}.")
        }

        val discount = Regex("(?:desconto|descontar)\\s*(?:de\\s*)?(\\d+(?:[.,]\\d+)?)\\s*%\\s*(?:em|de|sobre)\\s*(-?\\d+(?:[.,]\\d+)?)")
            .find(normalized)
        if (discount != null) {
            val percent = decimal(discount.groupValues[1])
            val base = decimal(discount.groupValues[2])
            val delta = base.multiply(percent).divide(BigDecimal("100"), MathContext.DECIMAL64)
            val finalValue = base.subtract(delta)
            return result(
                "porcentagem",
                "${format(finalValue)}",
                "Calculo Kotlin: ${format(base)} com desconto de ${format(percent)}% = ${format(finalValue)}."
            )
        }

        val increase = Regex("(?:aumento|aumentar|acrescimo|acréscimo)\\s*(?:de\\s*)?(\\d+(?:[.,]\\d+)?)\\s*%\\s*(?:em|de|sobre)\\s*(-?\\d+(?:[.,]\\d+)?)")
            .find(normalized)
        if (increase != null) {
            val percent = decimal(increase.groupValues[1])
            val base = decimal(increase.groupValues[2])
            val delta = base.multiply(percent).divide(BigDecimal("100"), MathContext.DECIMAL64)
            val finalValue = base.add(delta)
            return result(
                "porcentagem",
                "${format(finalValue)}",
                "Calculo Kotlin: ${format(base)} com aumento de ${format(percent)}% = ${format(finalValue)}."
            )
        }
        return null
    }

    private fun aggregateNumbers(message: String): NullAiKotlinResult? {
        val normalized = normalize(message)
        val numbers = numberRegex.findAll(normalized).map { decimal(it.value) }.toList()
        if (numbers.size < 2) return null

        val asksAverage = containsAny(normalized, "media", "média", "promedio", "promédio")
        val asksSum = containsAny(normalized, "soma", "somar", "total")
        val asksMinMax = containsAny(normalized, "maior", "menor", "maximo", "máximo", "minimo", "mínimo")
        if (!asksAverage && !asksSum && !asksMinMax) return null

        val sum = numbers.fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
        val lines = mutableListOf<String>()
        if (asksSum) lines += "soma = ${format(sum)}"
        if (asksAverage) {
            val average = sum.divide(BigDecimal(numbers.size), MathContext.DECIMAL64)
            lines += "media = ${format(average)}"
        }
        if (asksMinMax) {
            lines += "menor = ${format(numbers.minOrNull() ?: BigDecimal.ZERO)}"
            lines += "maior = ${format(numbers.maxOrNull() ?: BigDecimal.ZERO)}"
        }
        return result("lista numerica", lines.joinToString("; "), "Calculo Kotlin com ${numbers.size} numeros: ${lines.joinToString("; ")}.")
    }

    private fun unitConversion(message: String): NullAiKotlinResult? {
        val normalized = normalize(message)
        val match = Regex("(-?\\d+(?:[.,]\\d+)?)\\s*(km|quilometros|quilômetros|m|metros|cm|centimetros|centímetros|mm|milimetros|milímetros|kg|quilos|quilogramas|g|gramas|c|°c|f|°f|min|minutos|h|horas|dias?)\\s*(?:para|pra|em)\\s*(km|quilometros|quilômetros|m|metros|cm|centimetros|centímetros|mm|milimetros|milímetros|kg|quilos|quilogramas|g|gramas|c|°c|f|°f|min|minutos|h|horas|dias?)")
            .find(normalized) ?: return null
        val value = decimal(match.groupValues[1])
        val from = unitKey(match.groupValues[2])
        val to = unitKey(match.groupValues[3])
        val converted = convert(value, from, to) ?: return null
        return result("conversao", "${format(converted)} $to", "Conversao Kotlin: ${format(value)} $from = ${format(converted)} $to.")
    }

    private fun dateOffset(message: String): NullAiKotlinResult? {
        val normalized = normalize(message)
        val match = Regex("(?:daqui a|em)\\s*(\\d+)\\s*(dias?|semanas?|meses?)").find(normalized)
            ?: Regex("(\\d+)\\s*(dias?|semanas?|meses?)\\s*(?:a partir de hoje|depois de hoje)").find(normalized)
            ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2]
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val today = Calendar.getInstance()
        val target = today.clone() as Calendar
        val calendarAmount = amount.coerceAtMost(36_500L).toInt()
        when {
            unit.startsWith("semana") -> target.add(Calendar.WEEK_OF_YEAR, calendarAmount)
            unit.startsWith("mes") -> target.add(Calendar.MONTH, calendarAmount)
            else -> target.add(Calendar.DAY_OF_YEAR, calendarAmount)
        }
        val todayText = formatter.format(today.time)
        val targetText = formatter.format(target.time)
        return result("data", targetText, "Data Kotlin: hoje e $todayText; resultado = $targetText.")
    }

    private fun textAnalysis(message: String): NullAiKotlinResult? {
        val normalized = normalize(message)
        val wantsCount = containsAny(normalized, "quantas letras", "contar letras", "quantos caracteres", "contar caracteres", "quantas palavras", "contar palavras")
        if (!wantsCount) return null
        val quoted = Regex("\"([^\"]+)\"|'([^']+)'").find(message)
        val text = quoted?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() } ?: message.substringAfter(":", "").trim()
        if (text.isBlank() || text == message) return null
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val letters = text.count { it.isLetter() }
        val chars = text.length
        return result("analise de texto", "$letters letras, $chars caracteres, ${words.size} palavras", "Analise Kotlin: letras=$letters; caracteres=$chars; palavras=${words.size}.")
    }

    private fun mathExpression(message: String): NullAiKotlinResult? {
        val candidate = message
            .replace("quanto e", "", ignoreCase = true)
            .replace("quanto é", "", ignoreCase = true)
            .replace("calcule", "", ignoreCase = true)
            .replace("calcular", "", ignoreCase = true)
            .replace("?", "")
            .trim()
        if (!candidate.any { it.isDigit() } || !candidate.any { it in "+-*/^xX()" }) return null
        val normalized = candidate.replace('x', '*').replace('X', '*').replace(',', '.')
        if (!expressionChars.matches(normalized)) return null
        val value = runCatching { ExpressionParser(normalized).parse() }.getOrNull() ?: return null
        if (!value.isFinite()) return null
        val formatted = format(BigDecimal(value, MathContext.DECIMAL64))
        return result("expressao matematica", formatted, "Calculo Kotlin: $candidate = $formatted.")
    }

    private fun convert(value: BigDecimal, from: String, to: String): BigDecimal? {
        if (from == to) return value
        val length = mapOf("km" to BigDecimal("1000"), "m" to BigDecimal.ONE, "cm" to BigDecimal("0.01"), "mm" to BigDecimal("0.001"))
        val mass = mapOf("kg" to BigDecimal("1000"), "g" to BigDecimal.ONE)
        val time = mapOf("dias" to BigDecimal("24"), "h" to BigDecimal.ONE, "min" to BigDecimal.ONE.divide(BigDecimal("60"), MathContext.DECIMAL64))

        if (from in length && to in length) {
            return value.multiply(length.getValue(from)).divide(length.getValue(to), MathContext.DECIMAL64)
        }
        if (from in mass && to in mass) {
            return value.multiply(mass.getValue(from)).divide(mass.getValue(to), MathContext.DECIMAL64)
        }
        if (from in time && to in time) {
            return value.multiply(time.getValue(from)).divide(time.getValue(to), MathContext.DECIMAL64)
        }
        if (from == "c" && to == "f") return value.multiply(BigDecimal("9")).divide(BigDecimal("5"), MathContext.DECIMAL64).add(BigDecimal("32"))
        if (from == "f" && to == "c") return value.subtract(BigDecimal("32")).multiply(BigDecimal("5")).divide(BigDecimal("9"), MathContext.DECIMAL64)
        return null
    }

    private fun unitKey(unit: String): String {
        return when (normalize(unit)) {
            "quilometros", "quilômetros" -> "km"
            "metros" -> "m"
            "centimetros", "centímetros" -> "cm"
            "milimetros", "milímetros" -> "mm"
            "quilos", "quilogramas" -> "kg"
            "gramas" -> "g"
            "°c" -> "c"
            "°f" -> "f"
            "minutos" -> "min"
            "horas" -> "h"
            "dia" -> "dias"
            else -> normalize(unit)
        }
    }

    private fun result(kind: String, answer: String, context: String): NullAiKotlinResult {
        return NullAiKotlinResult(kind = kind, answer = answer, context = context)
    }

    private fun decimal(raw: String): BigDecimal {
        return BigDecimal(raw.replace(',', '.'))
    }

    private fun format(value: BigDecimal): String {
        return value
            .setScale(8, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun normalize(value: String): String {
        return value.lowercase(Locale.ROOT).trim()
    }

    private fun containsAny(text: String, vararg needles: String): Boolean {
        return needles.any { text.contains(it) }
    }

    private class ExpressionParser(private val input: String) {
        private var index = 0

        fun parse(): Double {
            val value = parseExpression()
            skipSpaces()
            require(index == input.length)
            return value
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                value = when {
                    match('+') -> value + parseTerm()
                    match('-') -> value - parseTerm()
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parsePower()
            while (true) {
                skipSpaces()
                value = when {
                    match('*') -> value * parsePower()
                    match('/') -> value / parsePower()
                    else -> return value
                }
            }
        }

        private fun parsePower(): Double {
            var value = parseFactor()
            skipSpaces()
            if (match('^')) {
                value = value.pow(parsePower())
            }
            return value
        }

        private fun parseFactor(): Double {
            skipSpaces()
            if (match('+')) return parseFactor()
            if (match('-')) return -parseFactor()
            if (match('(')) {
                val value = parseExpression()
                require(match(')'))
                return value
            }
            val start = index
            while (index < input.length && (input[index].isDigit() || input[index] == '.')) index++
            require(start != index)
            return input.substring(start, index).toDouble()
        }

        private fun match(char: Char): Boolean {
            skipSpaces()
            if (index >= input.length || input[index] != char) return false
            index++
            return true
        }

        private fun skipSpaces() {
            while (index < input.length && input[index].isWhitespace()) index++
        }
    }
}
