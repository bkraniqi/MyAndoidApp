package com.moneta.finance.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneta.finance.domain.Frequency
import com.moneta.finance.domain.LedgerEntry
import com.moneta.finance.domain.PaymentMethod
import com.moneta.finance.domain.TransactionType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    /** ARGB colour. */
    val color: Int,
    /** System categories ("Other") receive transactions of deleted categories and cannot be deleted. */
    val isSystem: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId")],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val description: String,
    val paymentMethod: PaymentMethod,
    val notes: String,
    val frequency: Frequency,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    /** Number of occurrences already turned into transactions (occurrence indices 0 until this). */
    val generatedCount: Int = 0,
    val active: Boolean = true,
    val createdAt: Long,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringRuleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("categoryId"),
        Index("epochDay"),
        Index("recurringRuleId"),
        // A recurring occurrence can exist only once: this is what prevents duplicates.
        Index(value = ["recurringRuleId", "occurrenceIndex"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    override val type: TransactionType,
    /** Positive amount in minor units (cents). */
    override val amountMinor: Long,
    override val categoryId: Long,
    /** Calendar date as epoch day; independent of time zones. */
    override val epochDay: Long,
    val description: String,
    override val paymentMethod: PaymentMethod,
    val notes: String,
    val recurringRuleId: Long? = null,
    val occurrenceIndex: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
) : LedgerEntry

@Entity(
    tableName = "category_budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["categoryId"], unique = true)],
)
data class CategoryBudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val amountMinor: Long,
)

/** Highest alert level already notified for a budget in a month (1 = 80 %, 2 = exceeded). */
@Entity(tableName = "budget_alerts")
data class BudgetAlertEntity(
    @PrimaryKey val key: String,
    val level: Int,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
