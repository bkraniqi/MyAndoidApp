package com.moneta.finance.domain

import java.time.LocalDate

data class TestEntry(
    override val type: TransactionType,
    override val amountMinor: Long,
    override val epochDay: Long,
    override val categoryId: Long = 1,
    override val paymentMethod: PaymentMethod = PaymentMethod.CASH,
) : LedgerEntry

fun income(amount: Long, date: String, category: Long = 100) =
    TestEntry(TransactionType.INCOME, amount, LocalDate.parse(date).toEpochDay(), category)

fun expense(amount: Long, date: String, category: Long = 1, method: PaymentMethod = PaymentMethod.CASH) =
    TestEntry(TransactionType.EXPENSE, amount, LocalDate.parse(date).toEpochDay(), category, method)
