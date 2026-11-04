package com.moneta.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class FinanceCalculatorTest {
    private val ledger = listOf(
        income(250_000, "2026-10-01"),          // salary 2,500.00
        expense(80_000, "2026-10-03", 6),       // rent 800.00
        expense(4_599, "2026-10-03", 1),        // groceries 45.99
        expense(1_250, "2026-10-15", 2),        // coffee 12.50
        income(30_000, "2026-11-02", 101),      // freelance 300.00
        expense(9_999, "2026-11-04", 1),        // groceries 99.99
        expense(1, "2026-11-04", 2),            // 0.01
    )

    @Test fun totalsCountEveryTransactionOnce() {
        val t = FinanceCalculator.totals(ledger)
        assertEquals(280_000L, t.incomeMinor)
        assertEquals(80_000L + 4_599 + 1_250 + 9_999 + 1, t.expenseMinor)
        assertEquals(280_000L - 95_849, t.netMinor)
    }

    @Test fun totalBalanceIncludesOpeningBalance() {
        // Total balance = opening + income - expenses
        assertEquals(10_000L + 280_000 - 95_849, FinanceCalculator.totalBalance(10_000, ledger))
        assertEquals(-500L, FinanceCalculator.totalBalance(-500, emptyList()))
    }

    @Test fun monthSummaryUsesOnlyThatMonth() {
        val oct = FinanceCalculator.monthSummary(ledger, YearMonth.of(2026, 10))
        assertEquals(250_000L, oct.incomeMinor)
        assertEquals(85_849L, oct.expenseMinor)
        assertEquals(164_151L, oct.remainingMinor)
        assertEquals(34.3396, oct.spentPercent!!, 0.0001)

        val nov = FinanceCalculator.monthSummary(ledger, YearMonth.of(2026, 11))
        assertEquals(30_000L, nov.incomeMinor)
        assertEquals(10_000L, nov.expenseMinor)
        assertEquals(20_000L, nov.remainingMinor)

        val empty = FinanceCalculator.monthSummary(ledger, YearMonth.of(2026, 9))
        assertEquals(0L, empty.remainingMinor)
        assertNull(empty.spentPercent)
    }

    @Test fun monthsPartitionTheYearExactly() {
        val months = FinanceCalculator.monthlyTotals(ledger, 2026)
        assertEquals(12, months.size)
        val sum = months.fold(Totals()) { acc, m -> acc + Totals(m.incomeMinor, m.expenseMinor) }
        assertEquals(FinanceCalculator.totals(ledger), sum)
    }

    @Test fun boundariesAreInclusiveAndExclusiveCorrectly() {
        val entries = listOf(expense(100, "2026-10-31"), expense(200, "2026-11-01"), expense(400, "2026-11-30"), expense(800, "2026-12-01"))
        assertEquals(600L, FinanceCalculator.monthSummary(entries, YearMonth.of(2026, 11)).expenseMinor)
    }

    @Test fun dailyTotalsCoverEveryDay() {
        val range = DateRange.month(YearMonth.of(2026, 10))
        val days = FinanceCalculator.dailyTotals(ledger, range)
        assertEquals(31, days.size)
        assertEquals(84_599L, days[2].expenseMinor)
        assertEquals(LocalDate.of(2026, 10, 3), days[2].date)
        assertEquals(85_849L, days.sumOf { it.expenseMinor })
    }

    @Test fun weeklyTotalsAreClippedToMonthAndSumToMonth() {
        val weeks = FinanceCalculator.weeklyTotals(ledger, YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 1), weeks.first().range.start)
        assertEquals(LocalDate.of(2026, 10, 31), weeks.last().range.endInclusive)
        assertEquals(85_849L, weeks.sumOf { it.expenseMinor })
        // No overlap and no gap between consecutive weeks
        weeks.zipWithNext().forEach { (a, b) -> assertEquals(a.range.endInclusive.plusDays(1), b.range.start) }
    }

    @Test fun categoryBreakdownSortedDescending() {
        val list = FinanceCalculator.byCategory(ledger, TransactionType.EXPENSE, null)
        assertEquals(listOf(6L, 1L, 2L), list.map { it.categoryId })
        assertEquals(14_598L, list[1].amountMinor)
        assertEquals(100.0, list.sumOf { it.sharePercent }, 0.01)
    }

    @Test fun balanceTrendCarriesPreviousYears() {
        val entries = listOf(income(1_000, "2025-12-31"), expense(300, "2026-01-10"), income(500, "2026-03-01"))
        val trend = FinanceCalculator.balanceTrend(2_000, entries, 2026)
        assertEquals(12, trend.size)
        assertEquals(2_700L, trend[0].balanceMinor)   // Jan: 2000 + 1000 - 300
        assertEquals(2_700L, trend[1].balanceMinor)   // Feb unchanged
        assertEquals(3_200L, trend[2].balanceMinor)   // Mar +500
        assertEquals(3_200L, trend[11].balanceMinor)
        assertEquals(FinanceCalculator.totalBalance(2_000, entries), trend.last().balanceMinor)
    }

    @Test fun recentMonthsSpanYearBoundary() {
        val entries = listOf(income(100, "2025-12-15"), expense(50, "2026-01-15"))
        val months = FinanceCalculator.recentMonths(entries, YearMonth.of(2026, 1), 3)
        assertEquals(listOf(YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 1)), months.map { it.month })
        assertEquals(100L, months[1].incomeMinor)
        assertEquals(50L, months[2].expenseMinor)
    }

    @Test fun savingsRate() {
        assertEquals(50.0, FinanceCalculator.savingsRate(Totals(1000, 500))!!, 0.0001)
        assertEquals(-50.0, FinanceCalculator.savingsRate(Totals(1000, 1500))!!, 0.0001)
        assertNull(FinanceCalculator.savingsRate(Totals(0, 500)))
    }

    @Test fun spendingSnapshot() {
        val s = FinanceCalculator.spendingSnapshot(ledger, LocalDate.of(2026, 11, 4), DayOfWeek.MONDAY)
        assertEquals(10_000L, s.todayMinor)
        assertEquals(10_000L, s.weekMinor)   // week of Mon 2 Nov
        assertEquals(10_000L, s.monthMinor)
        assertEquals(95_849L, s.yearMinor)
    }

    @Test fun editingAndDeletingChangesTotalsExactly() {
        val original = ledger.toMutableList()
        val before = FinanceCalculator.totalBalance(0, original)
        // Edit: 99.99 -> 100.00
        val idx = original.indexOfFirst { it.amountMinor == 9_999L }
        original[idx] = (original[idx] as TestEntry).copy(amountMinor = 10_000)
        assertEquals(before - 1, FinanceCalculator.totalBalance(0, original))
        // Delete it
        original.removeAt(idx)
        assertEquals(before + 9_999, FinanceCalculator.totalBalance(0, original))
    }

    @Test(expected = ArithmeticException::class)
    fun overflowIsDetectedNotWrapped() {
        FinanceCalculator.totals(listOf(income(Long.MAX_VALUE, "2026-01-01"), income(1, "2026-01-02")))
    }

    @Test fun averageDailyExpense() {
        val range = DateRange.month(YearMonth.of(2026, 10))
        assertEquals(85_849L / 31, FinanceCalculator.averageDailyExpense(ledger, range))
    }
}
