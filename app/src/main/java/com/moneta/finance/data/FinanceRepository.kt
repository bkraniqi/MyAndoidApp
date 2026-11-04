package com.moneta.finance.data

import androidx.room.withTransaction
import com.moneta.finance.data.db.CategoryBudgetEntity
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.data.db.MonetaDatabase
import com.moneta.finance.data.db.RecurringRuleEntity
import com.moneta.finance.data.db.SettingEntity
import com.moneta.finance.data.db.TransactionEntity
import com.moneta.finance.domain.DateRange
import com.moneta.finance.domain.Money
import com.moneta.finance.domain.Recurrence
import com.moneta.finance.domain.ThemeMode
import com.moneta.finance.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Single entry point for all reads and writes of financial data. */
class FinanceRepository(
    private val db: MonetaDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val categories = db.categoryDao()
    private val transactions = db.transactionDao()
    private val recurring = db.recurringDao()
    private val budgets = db.budgetDao()
    private val settingsDao = db.settingsDao()

    /** Invoked after any change that can affect budgets (set by the app container). */
    var onLedgerChanged: (suspend () -> Unit)? = null

    val transactionsFlow: Flow<List<TransactionEntity>> = transactions.observeAll()
    val categoriesFlow: Flow<List<CategoryEntity>> = categories.observeAll()
    val recurringFlow: Flow<List<RecurringRuleEntity>> = recurring.observeAll()
    val budgetsFlow: Flow<List<CategoryBudgetEntity>> = budgets.observeAll()
    val settingsFlow: Flow<AppSettings> = settingsDao.observeAll().map { AppSettings.from(it) }.distinctUntilChanged()

    suspend fun settings(): AppSettings = AppSettings.from(settingsDao.getAll())

    // ---------------------------------------------------------------- transactions

    suspend fun getTransaction(id: Long): TransactionEntity? = transactions.getById(id)

    suspend fun saveTransaction(
        id: Long?,
        type: TransactionType,
        amountMinor: Long,
        categoryId: Long,
        date: LocalDate,
        description: String,
        paymentMethod: com.moneta.finance.domain.PaymentMethod,
        notes: String,
    ): Long {
        validateAmount(amountMinor)
        val now = clock()
        val savedId = db.withTransaction {
            val category = categories.getById(categoryId) ?: error("Category no longer exists")
            require(category.type == type) { "Category does not match the transaction type" }
            if (id == null) {
                transactions.insert(
                    TransactionEntity(
                        type = type, amountMinor = amountMinor, categoryId = categoryId,
                        epochDay = date.toEpochDay(), description = description.trim(),
                        paymentMethod = paymentMethod, notes = notes.trim(),
                        createdAt = now, updatedAt = now,
                    )
                )
            } else {
                val existing = transactions.getById(id) ?: error("Transaction no longer exists")
                transactions.update(
                    existing.copy(
                        type = type, amountMinor = amountMinor, categoryId = categoryId,
                        epochDay = date.toEpochDay(), description = description.trim(),
                        paymentMethod = paymentMethod, notes = notes.trim(), updatedAt = now,
                    )
                )
                id
            }
        }
        onLedgerChanged?.invoke()
        return savedId
    }

    suspend fun deleteTransaction(id: Long): Boolean {
        val deleted = transactions.deleteById(id) > 0
        if (deleted) onLedgerChanged?.invoke()
        return deleted
    }

    suspend fun transactionsInRange(range: DateRange?): List<TransactionEntity> =
        if (range == null) transactions.getAll() else transactions.getInRange(range.startEpochDay, range.endEpochDay)

    suspend fun allCategories(): List<CategoryEntity> = categories.getAll()

    // ---------------------------------------------------------------- categories

    suspend fun saveCategory(category: CategoryEntity): Long {
        val name = category.name.trim()
        require(name.isNotEmpty()) { "Enter a category name" }
        return db.withTransaction {
            if (category.id == 0L) {
                val order = categories.maxSortOrder(category.type.name).coerceAtMost(98) + 1
                categories.insert(category.copy(name = name, isSystem = false, sortOrder = order))
            } else {
                val existing = categories.getById(category.id) ?: error("Category no longer exists")
                // Type and system flag are fixed: transactions already reference this category.
                categories.update(existing.copy(name = name, iconKey = category.iconKey, color = category.color))
                category.id
            }
        }
    }

    suspend fun categoryUsage(categoryId: Long): Int = transactions.countForCategory(categoryId)

    /** Deletes a category, moving its transactions and recurring rules to the type's "Other" category. */
    suspend fun deleteCategory(categoryId: Long) {
        db.withTransaction {
            val category = categories.getById(categoryId) ?: return@withTransaction
            require(!category.isSystem) { "This category cannot be deleted" }
            val fallback = categories.getSystemCategory(category.type.name) ?: error("Missing default category")
            transactions.reassignCategory(categoryId, fallback.id)
            recurring.reassignCategory(categoryId, fallback.id)
            budgets.deleteForCategory(categoryId)
            categories.deleteById(categoryId)
            Unit
        }
        onLedgerChanged?.invoke()
    }

    // ---------------------------------------------------------------- budgets & settings

    suspend fun setCategoryBudget(categoryId: Long, amountMinor: Long?) {
        db.withTransaction {
            val existing = budgets.getForCategory(categoryId)
            when {
                amountMinor == null -> budgets.deleteForCategory(categoryId)
                existing == null -> {
                    validateAmount(amountMinor)
                    val category = categories.getById(categoryId) ?: error("Category no longer exists")
                    require(category.type == TransactionType.EXPENSE) { "Budgets apply to expense categories" }
                    budgets.insert(CategoryBudgetEntity(categoryId = categoryId, amountMinor = amountMinor))
                }
                else -> {
                    validateAmount(amountMinor)
                    budgets.update(existing.copy(amountMinor = amountMinor))
                }
            }
        }
        onLedgerChanged?.invoke()
    }

    suspend fun setMonthlyLimit(amountMinor: Long?) {
        amountMinor?.let { validateAmount(it) }
        putSetting(AppSettings.KEY_MONTHLY_LIMIT, amountMinor?.toString())
        onLedgerChanged?.invoke()
    }

    suspend fun setSavingsGoal(amountMinor: Long?) {
        amountMinor?.let { validateAmount(it) }
        putSetting(AppSettings.KEY_SAVINGS_GOAL, amountMinor?.toString())
    }

    suspend fun setCurrency(code: String) = putSetting(AppSettings.KEY_CURRENCY, code)
    suspend fun setOpeningBalance(amountMinor: Long) = putSetting(AppSettings.KEY_OPENING_BALANCE, amountMinor.toString())
    suspend fun setBudgetAlerts(enabled: Boolean) = putSetting(AppSettings.KEY_BUDGET_ALERTS, enabled.toString())
    suspend fun setThemeMode(mode: ThemeMode) = putSetting(AppSettings.KEY_THEME, mode.name)

    private suspend fun putSetting(key: String, value: String?) {
        if (value == null) settingsDao.delete(key) else settingsDao.upsert(SettingEntity(key, value))
    }

    // ---------------------------------------------------------------- recurring

    suspend fun getRecurringRule(id: Long): RecurringRuleEntity? = recurring.getById(id)

    suspend fun saveRecurringRule(rule: RecurringRuleEntity): Long {
        validateAmount(rule.amountMinor)
        if (rule.endEpochDay != null) require(rule.endEpochDay >= rule.startEpochDay) { "End date must be after the start date" }
        val id = db.withTransaction {
            val category = categories.getById(rule.categoryId) ?: error("Category no longer exists")
            val existingRule = if (rule.id == 0L) null else recurring.getById(rule.id)
            val effectiveType = if (existingRule != null && existingRule.generatedCount > 0) existingRule.type else rule.type
            require(category.type == effectiveType) { "Category does not match the transaction type" }
            if (rule.id == 0L) {
                recurring.insert(rule.copy(generatedCount = 0, createdAt = clock(), description = rule.description.trim(), notes = rule.notes.trim()))
            } else {
                val existing = existingRule ?: error("Recurring transaction no longer exists")
                // Once occurrences exist, schedule fields are locked so dates can never shift or repeat.
                val locked = existing.generatedCount > 0
                recurring.update(
                    existing.copy(
                        type = if (locked) existing.type else rule.type,
                        amountMinor = rule.amountMinor,
                        categoryId = rule.categoryId,
                        description = rule.description.trim(),
                        paymentMethod = rule.paymentMethod,
                        notes = rule.notes.trim(),
                        frequency = if (locked) existing.frequency else rule.frequency,
                        startEpochDay = if (locked) existing.startEpochDay else rule.startEpochDay,
                        endEpochDay = rule.endEpochDay,
                        active = rule.active,
                    )
                )
                rule.id
            }
        }
        processRecurring(LocalDate.now())
        return id
    }

    suspend fun deleteRecurringRule(id: Long) {
        recurring.deleteById(id) // generated transactions are kept (rule id is set to null)
    }

    /**
     * Turns every due occurrence of active rules into a transaction. Runs in one database
     * transaction; the unique (rule, occurrence) index guarantees an occurrence is never
     * recorded twice even if this runs concurrently or repeatedly.
     */
    suspend fun processRecurring(today: LocalDate): Int {
        val inserted = db.withTransaction {
            var count = 0
            for (rule in recurring.getActive()) {
                val due = Recurrence.dueOccurrences(
                    start = LocalDate.ofEpochDay(rule.startEpochDay),
                    end = rule.endEpochDay?.let(LocalDate::ofEpochDay),
                    frequency = rule.frequency,
                    alreadyGenerated = rule.generatedCount,
                    today = today,
                )
                if (due.isEmpty()) continue
                val now = clock()
                for (occ in due) {
                    val rowId = transactions.insertIgnoringDuplicates(
                        TransactionEntity(
                            type = rule.type, amountMinor = rule.amountMinor, categoryId = rule.categoryId,
                            epochDay = occ.date.toEpochDay(), description = rule.description,
                            paymentMethod = rule.paymentMethod, notes = rule.notes,
                            recurringRuleId = rule.id, occurrenceIndex = occ.index,
                            createdAt = now, updatedAt = now,
                        )
                    )
                    if (rowId != -1L) count++
                }
                recurring.setGeneratedCount(rule.id, due.last().index + 1)
            }
            count
        }
        if (inserted > 0) onLedgerChanged?.invoke()
        return inserted
    }

    fun nextOccurrence(rule: RecurringRuleEntity): LocalDate? = Recurrence.nextOccurrence(
        LocalDate.ofEpochDay(rule.startEpochDay),
        rule.endEpochDay?.let(LocalDate::ofEpochDay),
        rule.frequency,
        rule.generatedCount,
    )

    // ---------------------------------------------------------------- backup

    suspend fun exportBackup(): String {
        val data = db.withTransaction {
            BackupData(
                settings = settingsDao.getAll(),
                categories = categories.getAll(),
                recurringRules = recurring.getAll(),
                transactions = transactions.getAll(),
                budgets = budgets.getAll(),
            )
        }
        return BackupCodec.encode(data, clock())
    }

    /** Replaces all data with the backup contents. Throws [BackupFormatException] for invalid files. */
    suspend fun restoreBackup(json: String): BackupData {
        val data = BackupCodec.decode(json)
        db.withTransaction {
            transactions.deleteAll()
            budgets.deleteAll()
            budgets.deleteAllAlerts()
            recurring.deleteAll()
            categories.deleteAll()
            settingsDao.deleteAll()
            categories.insertAll(data.categories)
            recurring.insertAll(data.recurringRules)
            transactions.insertAll(data.transactions)
            budgets.insertAll(data.budgets)
            settingsDao.insertAll(data.settings)
        }
        onLedgerChanged?.invoke()
        return data
    }

    private fun validateAmount(amountMinor: Long) {
        require(amountMinor > 0 && amountMinor <= Money.MAX_AMOUNT_MINOR) { "Enter an amount greater than zero" }
    }

}
