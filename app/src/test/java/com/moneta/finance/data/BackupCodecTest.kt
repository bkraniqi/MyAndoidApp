package com.moneta.finance.data

import com.moneta.finance.data.db.CategoryBudgetEntity
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.data.db.RecurringRuleEntity
import com.moneta.finance.data.db.SettingEntity
import com.moneta.finance.data.db.TransactionEntity
import com.moneta.finance.domain.Frequency
import com.moneta.finance.domain.PaymentMethod
import com.moneta.finance.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val categories = listOf(
        CategoryEntity(1, "Food", TransactionType.EXPENSE, "groceries", 0xFF2E9E6B.toInt()),
        CategoryEntity(2, "Other", TransactionType.EXPENSE, "more", 0xFF7D8A86.toInt(), isSystem = true, sortOrder = 99),
        CategoryEntity(3, "Salary", TransactionType.INCOME, "work", 0xFF0B8A5F.toInt()),
        CategoryEntity(4, "Other income", TransactionType.INCOME, "more", 0xFF7D8A86.toInt(), isSystem = true, sortOrder = 99),
    )
    private val rule = RecurringRuleEntity(
        id = 7, type = TransactionType.INCOME, amountMinor = 250_000, categoryId = 3, description = "Salary",
        paymentMethod = PaymentMethod.BANK_TRANSFER, notes = "", frequency = Frequency.MONTHLY,
        startEpochDay = 20_000, endEpochDay = null, generatedCount = 2, active = true, createdAt = 1,
    )
    private val data = BackupData(
        settings = listOf(SettingEntity(AppSettings.KEY_CURRENCY, "EUR"), SettingEntity(AppSettings.KEY_OPENING_BALANCE, "-1234")),
        categories = categories,
        recurringRules = listOf(rule),
        transactions = listOf(
            TransactionEntity(10, TransactionType.EXPENSE, 4_599, 1, 20_400, "Groceries \"bio\"", PaymentMethod.CASH, "notes\nline", null, null, 5, 6),
            TransactionEntity(11, TransactionType.INCOME, 250_000, 3, 20_000, "Salary", PaymentMethod.BANK_TRANSFER, "", 7, 0, 5, 5),
            TransactionEntity(12, TransactionType.INCOME, 250_000, 3, 20_031, "Salary", PaymentMethod.BANK_TRANSFER, "", 7, 1, 5, 5),
        ),
        budgets = listOf(CategoryBudgetEntity(1, 1, 30_000)),
    )

    @Test fun roundTripIsLossless() {
        val decoded = BackupCodec.decode(BackupCodec.encode(data, 123))
        assertEquals(data, decoded)
    }

    @Test fun rejectsForeignFiles() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("{\"hello\":1}") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
    }

    @Test fun rejectsInvalidAmountsAndReferences() {
        val badAmount = data.copy(transactions = listOf(data.transactions[0].copy(amountMinor = 0)))
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(BackupCodec.encode(badAmount, 0)) }
        val badCategory = data.copy(transactions = listOf(data.transactions[0].copy(categoryId = 99)))
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(BackupCodec.encode(badCategory, 0)) }
        val wrongType = data.copy(transactions = listOf(data.transactions[0].copy(categoryId = 3)))
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(BackupCodec.encode(wrongType, 0)) }
    }

    @Test fun rejectsDuplicateRecurringOccurrences() {
        val dup = data.copy(transactions = data.transactions + data.transactions[1].copy(id = 13))
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(BackupCodec.encode(dup, 0)) }
    }

    @Test fun settingsParseWithDefaults() {
        val s = AppSettings.from(data.settings)
        assertEquals("EUR", s.currencyCode)
        assertEquals(-1234L, s.openingBalanceMinor)
        assertEquals(null, s.monthlyLimitMinor)
        assertEquals("EUR", AppSettings.from(emptyList()).currencyCode)
    }
}
