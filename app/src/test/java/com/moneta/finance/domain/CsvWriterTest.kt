package com.moneta.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvWriterTest {
    @Test fun escapesSpecialCharacters() {
        assertEquals("plain", CsvWriter.escape("plain"))
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvWriter.escape("line1\nline2"))
    }

    @Test fun neutralisesFormulaInjection() {
        assertEquals("'=SUM(A1)", CsvWriter.escape("=SUM(A1)"))
        assertEquals("'+cmd", CsvWriter.escape("+cmd"))
        assertEquals("'@x", CsvWriter.escape("@x"))
        assertEquals("-12.50", CsvWriter.escape("-12.50"))
    }

    @Test fun writesExactAmounts() {
        val csv = CsvWriter.build(
            listOf(
                CsvRow("2026-11-04", TransactionType.EXPENSE, "Food", "Groceries, weekly", 4599, "EUR", "Cash", ""),
                CsvRow("2026-11-01", TransactionType.INCOME, "Salary", "November", 250000, "EUR", "Bank transfer", "net"),
            )
        )
        val lines = csv.removePrefix("\uFEFF").trim().lines()
        assertEquals(3, lines.size)
        assertEquals("2026-11-04,Expense,Food,\"Groceries, weekly\",45.99,-45.99,EUR,Cash,", lines[1])
        assertEquals("2026-11-01,Income,Salary,November,2500.00,2500.00,EUR,Bank transfer,net", lines[2])
        assertTrue(csv.startsWith("\uFEFF"))
    }
}
