package com.moneta.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class TransactionQueryTest {
    private val coffee = expense(350, "2026-11-03", category = 2, method = PaymentMethod.MOBILE_PAYMENT)
    private val salary = income(250_000, "2026-11-01")

    @Test fun filtersByTypeCategoryMethodAndDate() {
        assertTrue(TransactionFilter().matches(coffee, ""))
        assertFalse(TransactionFilter(type = TransactionType.INCOME).matches(coffee, ""))
        assertTrue(TransactionFilter(categoryIds = setOf(2)).matches(coffee, ""))
        assertFalse(TransactionFilter(categoryIds = setOf(3)).matches(coffee, ""))
        assertTrue(TransactionFilter(paymentMethods = setOf(PaymentMethod.MOBILE_PAYMENT)).matches(coffee, ""))
        assertFalse(TransactionFilter(paymentMethods = setOf(PaymentMethod.CASH)).matches(coffee, ""))
        assertTrue(TransactionFilter(range = DateRange.month(YearMonth.of(2026, 11))).matches(coffee, ""))
        assertFalse(TransactionFilter(range = DateRange.month(YearMonth.of(2026, 10))).matches(coffee, ""))
    }

    @Test fun searchMatchesTextAndAmount() {
        val text = "Flat white Restaurants & coffee"
        assertTrue(TransactionFilter(query = "flat").matches(coffee, text))
        assertTrue(TransactionFilter(query = "white coffee").matches(coffee, text))
        assertTrue(TransactionFilter(query = "3.50").matches(coffee, text))
        assertTrue(TransactionFilter(query = "3,50").matches(coffee, text))
        assertFalse(TransactionFilter(query = "tea").matches(coffee, text))
    }

    @Test fun groupingPutsEachEntryInExactlyOneGroup() {
        val items = listOf(coffee, salary, expense(100, "2026-10-31"), expense(200, "2026-10-26")).sortedByDescending { it.epochDay }
        for (period in SummaryPeriod.entries) {
            val groups = TransactionGrouping.group(items, period, DayOfWeek.MONDAY)
            assertEquals(items.size, groups.sumOf { it.items.size })
            assertEquals(FinanceCalculator.totals(items), groups.fold(Totals()) { a, g -> a + g.totals })
        }
        val weekly = TransactionGrouping.group(items, SummaryPeriod.WEEK, DayOfWeek.MONDAY)
        // 26 Oct – 1 Nov is one ISO week; 2 Nov – 8 Nov the next
        assertEquals(2, weekly.size)
        assertEquals(LocalDate.of(2026, 10, 26), weekly[1].range.start)
        val monthly = TransactionGrouping.group(items, SummaryPeriod.MONTH, DayOfWeek.MONDAY)
        assertEquals(2, monthly.size)
    }
}
