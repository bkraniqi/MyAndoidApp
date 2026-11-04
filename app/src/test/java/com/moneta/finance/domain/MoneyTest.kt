package com.moneta.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyTest {
    private fun ok(input: String, allowNegative: Boolean = false, allowZero: Boolean = false): Long =
        (Money.parse(input, allowNegative, allowZero) as Money.ParseResult.Success).minor

    private fun fails(input: String, allowNegative: Boolean = false) =
        assertTrue("expected failure for '$input'", Money.parse(input, allowNegative) is Money.ParseResult.Failure)

    @Test fun parsesWholeAndDecimalAmounts() {
        assertEquals(1200L, ok("12"))
        assertEquals(1250L, ok("12.5"))
        assertEquals(1250L, ok("12,5"))
        assertEquals(1205L, ok("12.05"))
        assertEquals(1L, ok("0.01"))
        assertEquals(50L, ok(",5"))
        assertEquals(1200L, ok("12."))
        assertEquals(123456789L, ok("1 234 567.89"))
    }

    @Test fun neverRoundsExtraDecimals() {
        fails("1.999")
        fails("0.001")
    }

    @Test fun rejectsInvalidInput() {
        fails("")
        fails("abc")
        fails("1.2.3")
        fails("1,2.3")
        fails("0")
        fails("0.00")
        fails("-5")
        fails("1e5")
    }

    @Test fun negativeAndZeroWhenAllowed() {
        assertEquals(-1050L, ok("-10.50", allowNegative = true))
        assertEquals(0L, ok("0", allowZero = true))
    }

    @Test fun rejectsAmountsAboveMaximum() {
        assertEquals(Money.MAX_AMOUNT_MINOR, ok("999999999999.99"))
        fails("1000000000000")
    }

    @Test fun avoidsFloatingPointErrors() {
        // 0.1 + 0.2 must be exactly 0.30
        assertEquals(30L, ok("0.1") + ok("0.2"))
        assertEquals("0.30", Money.toPlainString(ok("0.1") + ok("0.2")))
    }

    @Test fun plainAndInputStrings() {
        assertEquals("1234.56", Money.toPlainString(123456))
        assertEquals("-0.05", Money.toPlainString(-5))
        assertEquals("12", Money.toInputString(1200))
        assertEquals("12.50", Money.toInputString(1250))
    }

    @Test fun sanitizeInputLimitsDecimals() {
        assertEquals("12.3", Money.sanitizeInput("12.3"))
        assertEquals("12,34", Money.sanitizeInput("12,34"))
        assertNull(Money.sanitizeInput("12.345"))
        assertNull(Money.sanitizeInput("1.2.3"))
        assertNull(Money.sanitizeInput("12a"))
        assertNull(Money.sanitizeInput("-1"))
        assertEquals("-1", Money.sanitizeInput("-1", allowNegative = true))
    }

    @Test fun formatsCurrencyExactly() {
        val f = MoneyFormatter("EUR", Locale.GERMANY)
        val german = f.format(123456)
        assertTrue(german, german.startsWith("1.234,56") && german.endsWith("€"))
        val us = MoneyFormatter("EUR", Locale.US)
        assertEquals("€1,234.56", us.format(123456))
        assertEquals("€0.01", us.format(1))
        assertEquals("+€5.00", us.formatSigned(500))
        assertEquals("\u2212€5.00", us.formatSigned(-500))
    }

    @Test fun percentIsNullWithoutBase() {
        assertNull(Money.percent(10, 0))
        assertEquals(50.0, Money.percent(50, 100)!!, 0.0001)
    }

    @Test fun defaultCurrencyIsSupported() {
        assertEquals("EUR", Currencies.DEFAULT)
        assertTrue("EUR" in Currencies.supported)
    }
}
