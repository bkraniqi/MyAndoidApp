package com.moneta.finance.domain

import java.time.LocalDate

/**
 * Date math for recurring rules. Occurrence n is always computed from the start date
 * (start + n periods) rather than by repeatedly adding to the previous date, so a rule that
 * starts on the 31st falls on the last day of shorter months and returns to the 31st afterwards.
 */
object Recurrence {
    const val MAX_OCCURRENCES_PER_RUN = 5000

    fun occurrenceDate(start: LocalDate, frequency: Frequency, index: Int): LocalDate {
        require(index >= 0)
        val n = index.toLong()
        return when (frequency) {
            Frequency.DAILY -> start.plusDays(n)
            Frequency.WEEKLY -> start.plusWeeks(n)
            Frequency.MONTHLY -> start.plusMonths(n)
            Frequency.YEARLY -> start.plusYears(n)
        }
    }

    data class Occurrence(val index: Int, val date: LocalDate)

    /** Occurrences not yet generated whose date is on or before [today] (and not after [end]). */
    fun dueOccurrences(
        start: LocalDate,
        end: LocalDate?,
        frequency: Frequency,
        alreadyGenerated: Int,
        today: LocalDate,
        limit: Int = MAX_OCCURRENCES_PER_RUN,
    ): List<Occurrence> {
        val result = mutableListOf<Occurrence>()
        var index = alreadyGenerated
        while (result.size < limit) {
            val date = occurrenceDate(start, frequency, index)
            if (date.isAfter(today)) break
            if (end != null && date.isAfter(end)) break
            result += Occurrence(index, date)
            index++
        }
        return result
    }

    /** Next date that will be generated, or null if the rule has finished. */
    fun nextOccurrence(start: LocalDate, end: LocalDate?, frequency: Frequency, alreadyGenerated: Int): LocalDate? {
        val date = occurrenceDate(start, frequency, alreadyGenerated)
        return if (end != null && date.isAfter(end)) null else date
    }
}
