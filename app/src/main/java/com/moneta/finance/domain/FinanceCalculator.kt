package com.moneta.finance.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class Totals(val incomeMinor: Long = 0L, val expenseMinor: Long = 0L) {
    val netMinor: Long get() = Math.subtractExact(incomeMinor, expenseMinor)
    operator fun plus(other: Totals) = Totals(
        Math.addExact(incomeMinor, other.incomeMinor),
        Math.addExact(expenseMinor, other.expenseMinor),
    )
}

data class MonthSummary(
    val month: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long,
) {
    /** Money left this month: income minus expenses (may be negative). */
    val remainingMinor: Long get() = Math.subtractExact(incomeMinor, expenseMinor)

    /** Percentage of income spent, or null when no income was recorded. Display only. */
    val spentPercent: Double? get() = Money.percent(expenseMinor, incomeMinor)
}

data class CategoryAmount(val categoryId: Long, val amountMinor: Long, val sharePercent: Double)

data class DayTotal(val date: LocalDate, val incomeMinor: Long, val expenseMinor: Long)

data class PeriodTotal(val range: DateRange, val incomeMinor: Long, val expenseMinor: Long) {
    val netMinor: Long get() = Math.subtractExact(incomeMinor, expenseMinor)
}

data class MonthTotal(val month: YearMonth, val incomeMinor: Long, val expenseMinor: Long) {
    val netMinor: Long get() = Math.subtractExact(incomeMinor, expenseMinor)
}

data class BalancePoint(val month: YearMonth, val balanceMinor: Long)

data class SpendingSnapshot(
    val todayMinor: Long,
    val weekMinor: Long,
    val monthMinor: Long,
    val yearMinor: Long,
)

/**
 * Pure, exact financial calculations. Every entry is visited once per calculation and
 * all sums use overflow-checked integer arithmetic on minor units.
 */
object FinanceCalculator {

    fun totals(entries: Iterable<LedgerEntry>): Totals {
        var income = 0L
        var expense = 0L
        for (e in entries) {
            when (e.type) {
                TransactionType.INCOME -> income = Math.addExact(income, e.amountMinor)
                TransactionType.EXPENSE -> expense = Math.addExact(expense, e.amountMinor)
            }
        }
        return Totals(income, expense)
    }

    fun totals(entries: Iterable<LedgerEntry>, range: DateRange): Totals =
        totals(entries.filter { it.epochDay in range })

    /** Total balance = opening balance + total income - total expenses. */
    fun totalBalance(openingBalanceMinor: Long, entries: Iterable<LedgerEntry>): Long =
        Math.addExact(openingBalanceMinor, totals(entries).netMinor)

    /** Monthly balance = monthly income - monthly expenses. */
    fun monthSummary(entries: Iterable<LedgerEntry>, month: YearMonth): MonthSummary {
        val t = totals(entries, DateRange.month(month))
        return MonthSummary(month, t.incomeMinor, t.expenseMinor)
    }

    fun byCategory(entries: Iterable<LedgerEntry>, type: TransactionType, range: DateRange?): List<CategoryAmount> {
        val sums = LinkedHashMap<Long, Long>()
        var total = 0L
        for (e in entries) {
            if (e.type != type) continue
            if (range != null && e.epochDay !in range) continue
            sums[e.categoryId] = Math.addExact(sums[e.categoryId] ?: 0L, e.amountMinor)
            total = Math.addExact(total, e.amountMinor)
        }
        return sums.entries
            .map { (id, amount) -> CategoryAmount(id, amount, Money.percent(amount, total) ?: 0.0) }
            .sortedWith(compareByDescending<CategoryAmount> { it.amountMinor }.thenBy { it.categoryId })
    }

    /** One entry per calendar day in [range], including days without activity. */
    fun dailyTotals(entries: Iterable<LedgerEntry>, range: DateRange): List<DayTotal> {
        val size = range.dayCount.toInt()
        val income = LongArray(size)
        val expense = LongArray(size)
        for (e in entries) {
            if (e.epochDay !in range) continue
            val i = (e.epochDay - range.startEpochDay).toInt()
            when (e.type) {
                TransactionType.INCOME -> income[i] = Math.addExact(income[i], e.amountMinor)
                TransactionType.EXPENSE -> expense[i] = Math.addExact(expense[i], e.amountMinor)
            }
        }
        return List(size) { i -> DayTotal(range.start.plusDays(i.toLong()), income[i], expense[i]) }
    }

    /** Weeks overlapping [month], clipped to the month so no transaction falls in two weeks. */
    fun weeklyTotals(entries: Iterable<LedgerEntry>, month: YearMonth, firstDayOfWeek: DayOfWeek): List<PeriodTotal> {
        val monthRange = DateRange.month(month)
        val weeks = mutableListOf<DateRange>()
        var cursor = monthRange.start
        while (!cursor.isAfter(monthRange.endInclusive)) {
            val week = DateRange.week(cursor, firstDayOfWeek)
            val end = minOf(week.endInclusive, monthRange.endInclusive)
            weeks += DateRange(cursor, end)
            cursor = end.plusDays(1)
        }
        val list = entries.toList()
        return weeks.map { w ->
            val t = totals(list, w)
            PeriodTotal(w, t.incomeMinor, t.expenseMinor)
        }
    }

    fun monthlyTotals(entries: Iterable<LedgerEntry>, year: Int): List<MonthTotal> {
        val income = LongArray(12)
        val expense = LongArray(12)
        val range = DateRange.year(year)
        for (e in entries) {
            if (e.epochDay !in range) continue
            val m = LocalDate.ofEpochDay(e.epochDay).monthValue - 1
            when (e.type) {
                TransactionType.INCOME -> income[m] = Math.addExact(income[m], e.amountMinor)
                TransactionType.EXPENSE -> expense[m] = Math.addExact(expense[m], e.amountMinor)
            }
        }
        return List(12) { i -> MonthTotal(YearMonth.of(year, i + 1), income[i], expense[i]) }
    }

    /** Totals for consecutive months ending with [lastMonth] (oldest first). */
    fun recentMonths(entries: Iterable<LedgerEntry>, lastMonth: YearMonth, count: Int): List<MonthTotal> {
        val first = lastMonth.minusMonths((count - 1).toLong())
        val range = DateRange(first.atDay(1), lastMonth.atEndOfMonth())
        val income = LongArray(count)
        val expense = LongArray(count)
        for (e in entries) {
            if (e.epochDay !in range) continue
            val d = LocalDate.ofEpochDay(e.epochDay)
            val i = ((d.year - first.year) * 12 + (d.monthValue - first.monthValue))
            when (e.type) {
                TransactionType.INCOME -> income[i] = Math.addExact(income[i], e.amountMinor)
                TransactionType.EXPENSE -> expense[i] = Math.addExact(expense[i], e.amountMinor)
            }
        }
        return List(count) { i -> MonthTotal(first.plusMonths(i.toLong()), income[i], expense[i]) }
    }

    /** Balance at the end of each month of [year], including the opening balance and all earlier activity. */
    fun balanceTrend(openingBalanceMinor: Long, entries: Iterable<LedgerEntry>, year: Int): List<BalancePoint> {
        val yearStart = LocalDate.of(year, 1, 1).toEpochDay()
        var carried = openingBalanceMinor
        val list = entries.toList()
        for (e in list) if (e.epochDay < yearStart) carried = Math.addExact(carried, e.signedAmountMinor)
        val months = monthlyTotals(list, year)
        var running = carried
        return months.map { m ->
            running = Math.addExact(running, m.netMinor)
            BalancePoint(m.month, running)
        }
    }

    /** Share of income saved, or null without income. Display only. */
    fun savingsRate(totals: Totals): Double? = Money.percent(totals.netMinor, totals.incomeMinor)

    fun spendingSnapshot(entries: Iterable<LedgerEntry>, today: LocalDate, firstDayOfWeek: DayOfWeek): SpendingSnapshot {
        val day = DateRange.day(today)
        val week = DateRange.week(today, firstDayOfWeek)
        val month = DateRange.month(YearMonth.from(today))
        val year = DateRange.year(today.year)
        var d = 0L; var w = 0L; var m = 0L; var y = 0L
        for (e in entries) {
            if (e.type != TransactionType.EXPENSE) continue
            if (e.epochDay in day) d = Math.addExact(d, e.amountMinor)
            if (e.epochDay in week) w = Math.addExact(w, e.amountMinor)
            if (e.epochDay in month) m = Math.addExact(m, e.amountMinor)
            if (e.epochDay in year) y = Math.addExact(y, e.amountMinor)
        }
        return SpendingSnapshot(d, w, m, y)
    }

    /** Average spending per day over [range], truncated toward zero to whole cents (display only). */
    fun averageDailyExpense(entries: Iterable<LedgerEntry>, range: DateRange): Long {
        val total = totals(entries, range).expenseMinor
        return total / range.dayCount
    }
}
