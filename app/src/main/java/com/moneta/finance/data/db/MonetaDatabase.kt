package com.moneta.finance.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        RecurringRuleEntity::class,
        TransactionEntity::class,
        CategoryBudgetEntity::class,
        BudgetAlertEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MonetaDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringDao(): RecurringDao
    abstract fun budgetDao(): BudgetDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "moneta.db"

        fun build(context: Context): MonetaDatabase =
            Room.databaseBuilder(context.applicationContext, MonetaDatabase::class.java, NAME)
                .addCallback(SeedCallback)
                .build()

        fun buildInMemory(context: Context): MonetaDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, MonetaDatabase::class.java)
                .addCallback(SeedCallback)
                .build()
    }

    /** Inserts the default categories when the database file is first created. */
    private object SeedCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            DefaultCategories.all().forEach { c ->
                db.execSQL(
                    "INSERT INTO categories (name, type, iconKey, color, isSystem, sortOrder) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(c.name, c.type.name, c.iconKey, c.color, if (c.isSystem) 1 else 0, c.sortOrder),
                )
            }
        }
    }
}
