package com.moneta.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun monthlyOnThe31stClampsAndRecovers() {
        val start = d("2026-01-31")
        val dates = (0..4).map { Recurrence.occurrenceDate(start, Frequency.MONTHLY, it) }
        assertEquals(listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30"), d("2026-05-31")), dates)
    }

    @Test fun yearlyOnLeapDay() {
        val start = d("2024-02-29")
        assertEquals(d("2025-02-28"), Recurrence.occurrenceDate(start, Frequency.YEARLY, 1))
        assertEquals(d("2028-02-29"), Recurrence.occurrenceDate(start, Frequency.YEARLY, 4))
    }

    @Test fun dueOccurrencesIncludesTodayAndNotTomorrow() {
        val due = Recurrence.dueOccurrences(d("2026-11-01"), null, Frequency.DAILY, 0, d("2026-11-04"))
        assertEquals(4, due.size)
        assertEquals(d("2026-11-04"), due.last().date)
        assertEquals(3, due.last().index)
    }

    @Test fun alreadyGeneratedOccurrencesAreNeverRepeated() {
        val first = Recurrence.dueOccurrences(d("2026-01-15"), null, Frequency.MONTHLY, 0, d("2026-11-04"))
        assertEquals(10, first.size)
        val again = Recurrence.dueOccurrences(d("2026-01-15"), null, Frequency.MONTHLY, first.size, d("2026-11-04"))
        assertTrue(again.isEmpty())
        val nextMonth = Recurrence.dueOccurrences(d("2026-01-15"), null, Frequency.MONTHLY, first.size, d("2026-12-20"))
        assertEquals(listOf(Recurrence.Occurrence(10, d("2026-11-15")), Recurrence.Occurrence(11, d("2026-12-15"))), nextMonth)
    }

    @Test fun endDateStopsGeneration() {
        val due = Recurrence.dueOccurrences(d("2026-01-01"), d("2026-01-21"), Frequency.WEEKLY, 0, d("2026-12-31"))
        assertEquals(listOf(d("2026-01-01"), d("2026-01-08"), d("2026-01-15")), due.map { it.date })
        assertNull(Recurrence.nextOccurrence(d("2026-01-01"), d("2026-01-21"), Frequency.WEEKLY, 3))
    }

    @Test fun futureStartGeneratesNothing() {
        assertTrue(Recurrence.dueOccurrences(d("2026-12-01"), null, Frequency.MONTHLY, 0, d("2026-11-04")).isEmpty())
        assertEquals(d("2026-12-01"), Recurrence.nextOccurrence(d("2026-12-01"), null, Frequency.MONTHLY, 0))
    }

    @Test fun indicesAreUniqueAndConsecutive() {
        val due = Recurrence.dueOccurrences(d("2025-01-01"), null, Frequency.DAILY, 5, d("2025-03-01"))
        assertEquals((5 until 5 + due.size).toList(), due.map { it.index })
        assertEquals(due.size, due.map { it.date }.toSet().size)
    }
}
