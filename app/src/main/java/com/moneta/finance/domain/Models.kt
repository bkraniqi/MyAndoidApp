package com.moneta.finance.domain

enum class TransactionType { INCOME, EXPENSE }

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    DEBIT_CARD("Debit card"),
    CREDIT_CARD("Credit card"),
    BANK_TRANSFER("Bank transfer"),
    MOBILE_PAYMENT("Mobile payment"),
    OTHER("Other"),
}

enum class Frequency(val label: String, val adjective: String) {
    DAILY("Daily", "day"),
    WEEKLY("Weekly", "week"),
    MONTHLY("Monthly", "month"),
    YEARLY("Yearly", "year"),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Minimal view of a recorded transaction used by every calculation in the app.
 * Amounts are always positive integer minor units (cents); the [type] carries the sign.
 * Dates are stored as epoch days of a calendar date (no time, no time zone).
 */
interface LedgerEntry {
    val type: TransactionType
    val amountMinor: Long
    val epochDay: Long
    val categoryId: Long
    val paymentMethod: PaymentMethod
}

/** Amount with the sign implied by the transaction type: income positive, expense negative. */
val LedgerEntry.signedAmountMinor: Long
    get() = if (type == TransactionType.INCOME) amountMinor else Math.negateExact(amountMinor)
