package com.moneta.finance.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneta.finance.data.db.MonetaDatabase
import com.moneta.finance.data.db.RecurringRuleEntity
import com.moneta.finance.domain.FinanceCalculator
import com.moneta.finance.domain.Frequency
import com.moneta.finance.domain.PaymentMethod
import com.moneta.finance.domain.TransactionType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Exercises the real Room database on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class FinanceRepositoryTest {
    private lateinit var db: MonetaDatabase
    private lateinit var repo: FinanceRepository

    @Before fun setUp() {
        db = MonetaDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        repo = FinanceRepository(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun category(type: TransactionType, name: String) =
        repo.allCategories().first { it.type == type && it.name == name }.id

    @Test fun seedsDefaultCategories() = runBlocking {
        val all = repo.allCategories()
        assertEquals(11, all.count { it.type == TransactionType.EXPENSE })
        assertEquals(6, all.count { it.type == TransactionType.INCOME })
        assertEquals(2, all.count { it.isSystem })
    }

    @Test fun addEditDeleteKeepsTotalsExact() = runBlocking {
        val food = category(TransactionType.EXPENSE, "Food & groceries")
        val salary = category(TransactionType.INCOME, "Salary")
        repo.setOpeningBalance(10_000)
        val inc = repo.saveTransaction(null, TransactionType.INCOME, 250_000, salary, LocalDate.of(2026, 11, 1), "Salary", PaymentMethod.BANK_TRANSFER, "")
        val exp = repo.saveTransaction(null, TransactionType.EXPENSE, 4_599, food, LocalDate.of(2026, 11, 2), "Groceries", PaymentMethod.CASH, "")
        var tx = repo.transactionsFlow.first()
        assertEquals(10_000L + 250_000 - 4_599, FinanceCalculator.totalBalance(repo.settings().openingBalanceMinor, tx))

        repo.saveTransaction(exp, TransactionType.EXPENSE, 5_000, food, LocalDate.of(2026, 11, 2), "Groceries", PaymentMethod.CASH, "")
        tx = repo.transactionsFlow.first()
        assertEquals(2, tx.size)
        assertEquals(10_000L + 250_000 - 5_000, FinanceCalculator.totalBalance(10_000, tx))

        repo.deleteTransaction(inc)
        tx = repo.transactionsFlow.first()
        assertEquals(1, tx.size)
        assertEquals(10_000L - 5_000, FinanceCalculator.totalBalance(10_000, tx))
    }

    @Test fun recurringNeverDuplicates() = runBlocking {
        val rent = category(TransactionType.EXPENSE, "Rent & housing")
        repo.saveRecurringRule(
            RecurringRuleEntity(
                type = TransactionType.EXPENSE, amountMinor = 80_000, categoryId = rent, description = "Rent",
                paymentMethod = PaymentMethod.BANK_TRANSFER, notes = "", frequency = Frequency.MONTHLY,
                startEpochDay = LocalDate.of(2026, 1, 31).toEpochDay(), endEpochDay = null, createdAt = 0,
            )
        )
        val today = LocalDate.of(2026, 11, 4)
        // Run the processor many times concurrently and sequentially.
        (1..5).map { async { repo.processRecurring(today) } }.awaitAll()
        repeat(3) { repo.processRecurring(today) }
        val generated = repo.transactionsFlow.first().filter { it.recurringRuleId != null }
        // 31 Jan through 31 Oct inclusive = 10 occurrences (as of 4 Nov 2026), plus nothing from LocalDate.now() if later.
        val expected = generated.map { it.occurrenceIndex }.toSet().size
        assertEquals(expected, generated.size)
        assertTrue(generated.any { it.epochDay == LocalDate.of(2026, 2, 28).toEpochDay() })
    }

    @Test fun deletingCategoryMovesTransactionsToOther() = runBlocking {
        val coffee = category(TransactionType.EXPENSE, "Restaurants & coffee")
        repo.saveTransaction(null, TransactionType.EXPENSE, 350, coffee, LocalDate.of(2026, 11, 3), "Flat white", PaymentMethod.CASH, "")
        repo.deleteCategory(coffee)
        val other = category(TransactionType.EXPENSE, "Other")
        val tx = repo.transactionsFlow.first()
        assertEquals(1, tx.size)
        assertEquals(other, tx.single().categoryId)
        assertEquals(350L, tx.single().amountMinor)
    }

    @Test fun backupRestoreRoundTrip() = runBlocking {
        val food = category(TransactionType.EXPENSE, "Food & groceries")
        repo.saveTransaction(null, TransactionType.EXPENSE, 1_234, food, LocalDate.of(2026, 11, 3), "Market", PaymentMethod.CASH, "")
        repo.setCategoryBudget(food, 30_000)
        val json = repo.exportBackup()
        repo.saveTransaction(null, TransactionType.EXPENSE, 999, food, LocalDate.of(2026, 11, 4), "Extra", PaymentMethod.CASH, "")
        repo.restoreBackup(json)
        val tx = repo.transactionsFlow.first()
        assertEquals(1, tx.size)
        assertEquals(1_234L, tx.single().amountMinor)
        assertEquals(30_000L, repo.budgetsFlow.first().single().amountMinor)
    }
}
