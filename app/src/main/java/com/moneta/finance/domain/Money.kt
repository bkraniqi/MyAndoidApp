package com.moneta.finance.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Money is represented everywhere as a [Long] number of minor units (cents).
 * Floating point is never used for amounts. Parsing rejects input with more decimals
 * than the currency supports instead of rounding it.
 */
object Money {
    const val FRACTION_DIGITS = 2
    private const val MAX_INTEGER_DIGITS = 12

    /** Largest amount accepted from user input: 999,999,999,999.99 */
    const val MAX_AMOUNT_MINOR: Long = 99_999_999_999_999L

    sealed interface ParseResult {
        data class Success(val minor: Long) : ParseResult
        data class Failure(val message: String) : ParseResult
    }

    private val pattern = Regex("""^(-)?(\d*)(?:[.,](\d*))?$""")

    fun parse(
        raw: String,
        allowNegative: Boolean = false,
        allowZero: Boolean = false,
    ): ParseResult {
        val input = raw.trim().replace(" ", "").replace("\u00A0", "").replace("\u202F", "")
        if (input.isEmpty() || input == "-") return ParseResult.Failure("Enter an amount")
        val match = pattern.matchEntire(input) ?: return ParseResult.Failure("Use digits and one decimal separator only")
        val negative = match.groupValues[1].isNotEmpty()
        val integerPart = match.groupValues[2]
        val fractionPart = match.groupValues[3]
        if (integerPart.isEmpty() && fractionPart.isEmpty()) return ParseResult.Failure("Enter an amount")
        if (negative && !allowNegative) return ParseResult.Failure("Amount cannot be negative")
        if (fractionPart.length > FRACTION_DIGITS) {
            return ParseResult.Failure("Use at most $FRACTION_DIGITS decimal places")
        }
        val significantInteger = integerPart.trimStart('0')
        if (significantInteger.length > MAX_INTEGER_DIGITS) return ParseResult.Failure("Amount is too large")
        val units = if (significantInteger.isEmpty()) 0L else significantInteger.toLong()
        val cents = fractionPart.padEnd(FRACTION_DIGITS, '0').ifEmpty { "0" }.toLong()
        val magnitude = Math.addExact(Math.multiplyExact(units, 100L), cents)
        if (magnitude > MAX_AMOUNT_MINOR) return ParseResult.Failure("Amount is too large")
        if (magnitude == 0L && !allowZero) return ParseResult.Failure("Enter an amount greater than zero")
        return ParseResult.Success(if (negative) -magnitude else magnitude)
    }

    /** Exact decimal representation, e.g. 123456 -> "1234.56", -5 -> "-0.05". */
    fun toPlainString(minor: Long): String = BigDecimal.valueOf(minor, FRACTION_DIGITS).toPlainString()

    /** Value suitable for pre-filling an input field: "12.5" stays "12.50", "12.00" becomes "12". */
    fun toInputString(minor: Long): String {
        val plain = toPlainString(minor)
        return if (plain.endsWith(".00")) plain.dropLast(3) else plain
    }

    /**
     * Filters keyboard input for amount fields. Returns the accepted text or null when the
     * edit must be rejected (it would introduce invalid characters or too many decimals).
     */
    fun sanitizeInput(text: String, allowNegative: Boolean = false): String? {
        if (text.isEmpty()) return text
        var seenSeparator = false
        var decimals = 0
        var integerDigits = 0
        text.forEachIndexed { index, c ->
            when {
                c == '-' && index == 0 && allowNegative -> Unit
                c.isDigit() -> if (seenSeparator) decimals++ else integerDigits++
                (c == '.' || c == ',') && !seenSeparator -> seenSeparator = true
                else -> return null
            }
        }
        if (decimals > FRACTION_DIGITS || integerDigits > MAX_INTEGER_DIGITS) return null
        return text
    }

    /** Ratio as a percentage for display purposes only (never used for stored money). */
    fun percent(part: Long, whole: Long): Double? {
        if (whole <= 0L) return null
        return BigDecimal.valueOf(part).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP)
            .toDouble()
    }
}

/** Currencies with two minor-unit digits, matching how amounts are stored. */
object Currencies {
    const val DEFAULT = "EUR"
    val supported: List<String> = listOf(
        "EUR", "USD", "GBP", "CHF", "ALL", "MKD", "RSD", "BAM", "BGN", "RON",
        "PLN", "CZK", "HUF", "SEK", "NOK", "DKK", "TRY", "CAD", "AUD", "NZD", "CNY", "INR",
        "AED", "SAR", "ZAR", "BRL", "MXN", "SGD", "HKD",
    ).filter { code ->
        runCatching { Currency.getInstance(code).defaultFractionDigits == Money.FRACTION_DIGITS }.getOrDefault(false)
    }

    fun displayName(code: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code)

    fun symbol(code: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(code).getSymbol(locale) }.getOrDefault(code)
}

/** Locale-aware currency formatting of minor units. Never rounds: values always have scale 2. */
class MoneyFormatter(val currencyCode: String, private val locale: Locale = Locale.getDefault()) {
    private val currency: Currency = runCatching { Currency.getInstance(currencyCode) }
        .getOrElse { Currency.getInstance(Currencies.DEFAULT) }

    private val format: NumberFormat = NumberFormat.getCurrencyInstance(locale).apply {
        currency = this@MoneyFormatter.currency
        minimumFractionDigits = Money.FRACTION_DIGITS
        maximumFractionDigits = Money.FRACTION_DIGITS
        roundingMode = RoundingMode.UNNECESSARY
    }

    private val compactNumber: NumberFormat = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
        roundingMode = RoundingMode.HALF_UP
    }

    val symbol: String get() = currency.getSymbol(locale)

    fun format(minor: Long): String = synchronized(format) { format.format(BigDecimal.valueOf(minor, Money.FRACTION_DIGITS)) }

    /** Formats with an explicit sign: "+€12.00" / "\u2212€12.00". */
    fun formatSigned(minor: Long): String = when {
        minor > 0 -> "+" + format(minor)
        minor < 0 -> "\u2212" + format(-minor)
        else -> format(0)
    }

    /** Short label for chart axes only, e.g. "€1.2k". */
    fun formatCompact(minor: Long): String {
        val value = BigDecimal.valueOf(minor, Money.FRACTION_DIGITS)
        val abs = value.abs()
        val (scaled, suffix) = when {
            abs >= BigDecimal(1_000_000) -> value.divide(BigDecimal(1_000_000)) to "M"
            abs >= BigDecimal(1_000) -> value.divide(BigDecimal(1_000)) to "k"
            else -> value to ""
        }
        return symbol + synchronized(compactNumber) { compactNumber.format(scaled) } + suffix
    }
}
