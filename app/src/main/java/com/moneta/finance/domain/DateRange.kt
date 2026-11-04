package com.moneta.finance.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Inclusive range of calendar dates. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "Range end must not be before its start" }
    }

    val startEpochDay: Long get() = start.toEpochDay()
    val endEpochDay: Long get() = endInclusive.toEpochDay()
    val dayCount: Long get() = endEpochDay - startEpochDay + 1

    operator fun contains(epochDay: Long): Boolean = epochDay in startEpochDay..endEpochDay
    operator fun contains(date: LocalDate): Boolean = date.toEpochDay() in this

    fun days(): Sequence<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(endInclusive) }

    companion object {
        fun day(date: LocalDate) = DateRange(date, date)
        fun month(month: YearMonth) = DateRange(month.atDay(1), month.atEndOfMonth())
        fun year(year: Int) = DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
        fun year(year: Year) = year(year.value)
        fun week(date: LocalDate, firstDayOfWeek: DayOfWeek): DateRange {
            val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            return DateRange(start, start.plusDays(6))
        }
    }
}
