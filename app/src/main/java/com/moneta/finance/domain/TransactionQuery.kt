package com.moneta.finance.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class TransactionFilter(
    val query: String = "",
    val type: TransactionType? = null,
    val categoryIds: Set<Long> = emptySet(),
    val paymentMethods: Set<PaymentMethod> = emptySet(),
    val range: DateRange? = null,
) {
    val isActive: Boolean
        get() = type != null || categoryIds.isNotEmpty() || paymentMethods.isNotEmpty() || range != null

    val activeCount: Int
        get() = listOf(type != null, categoryIds.isNotEmpty(), paymentMethods.isNotEmpty(), range != null).count { it }

    /**
     * [searchText] is any text describing the entry (description, notes, category name);
     * the amount is matched as well, so typing "12.50" or "12,50" finds that transaction.
     */
    fun matches(entry: LedgerEntry, searchText: String): Boolean {
        if (type != null && entry.type != type) return false
        if (categoryIds.isNotEmpty() && entry.categoryId !in categoryIds) return false
        if (paymentMethods.isNotEmpty() && entry.paymentMethod !in paymentMethods) return false
        if (range != null && entry.epochDay !in range) return false
        val q = query.trim()
        if (q.isEmpty()) return true
        val plainAmount = Money.toPlainString(entry.amountMinor)
        val haystack = (searchText + " " + plainAmount + " " + plainAmount.replace('.', ',')).lowercase()
        return q.lowercase().split(Regex("\\s+")).all { it in haystack }
    }
}

enum class SummaryPeriod(val label: String) { DAY("Daily"), WEEK("Weekly"), MONTH("Monthly") }

data class EntryGroup<T : LedgerEntry>(val range: DateRange, val totals: Totals, val items: List<T>)

object TransactionGrouping {
    fun periodFor(date: LocalDate, period: SummaryPeriod, firstDayOfWeek: DayOfWeek): DateRange = when (period) {
        SummaryPeriod.DAY -> DateRange.day(date)
        SummaryPeriod.WEEK -> DateRange.week(date, firstDayOfWeek)
        SummaryPeriod.MONTH -> DateRange.month(YearMonth.from(date))
    }

    /** Groups entries (kept in their incoming order) into periods; each entry lands in exactly one group. */
    fun <T : LedgerEntry> group(items: List<T>, period: SummaryPeriod, firstDayOfWeek: DayOfWeek): List<EntryGroup<T>> {
        val buckets = LinkedHashMap<DateRange, MutableList<T>>()
        for (item in items) {
            val key = periodFor(LocalDate.ofEpochDay(item.epochDay), period, firstDayOfWeek)
            buckets.getOrPut(key) { mutableListOf() } += item
        }
        return buckets.map { (range, list) -> EntryGroup(range, FinanceCalculator.totals(list), list) }
    }
}
