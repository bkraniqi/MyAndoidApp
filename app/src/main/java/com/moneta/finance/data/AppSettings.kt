package com.moneta.finance.data

import com.moneta.finance.data.db.SettingEntity
import com.moneta.finance.domain.Currencies
import com.moneta.finance.domain.ThemeMode

/** User preferences persisted in the database (and therefore included in backups). */
data class AppSettings(
    val currencyCode: String = Currencies.DEFAULT,
    val openingBalanceMinor: Long = 0L,
    val monthlyLimitMinor: Long? = null,
    val savingsGoalMinor: Long? = null,
    val budgetAlertsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        const val KEY_CURRENCY = "currency"
        const val KEY_OPENING_BALANCE = "opening_balance_minor"
        const val KEY_MONTHLY_LIMIT = "monthly_limit_minor"
        const val KEY_SAVINGS_GOAL = "savings_goal_minor"
        const val KEY_BUDGET_ALERTS = "budget_alerts"
        const val KEY_THEME = "theme_mode"

        val knownKeys = setOf(KEY_CURRENCY, KEY_OPENING_BALANCE, KEY_MONTHLY_LIMIT, KEY_SAVINGS_GOAL, KEY_BUDGET_ALERTS, KEY_THEME)

        fun from(rows: List<SettingEntity>): AppSettings {
            val map = rows.associate { it.key to it.value }
            return AppSettings(
                currencyCode = map[KEY_CURRENCY]?.takeIf { it in Currencies.supported } ?: Currencies.DEFAULT,
                openingBalanceMinor = map[KEY_OPENING_BALANCE]?.toLongOrNull() ?: 0L,
                monthlyLimitMinor = map[KEY_MONTHLY_LIMIT]?.toLongOrNull()?.takeIf { it > 0 },
                savingsGoalMinor = map[KEY_SAVINGS_GOAL]?.toLongOrNull()?.takeIf { it > 0 },
                budgetAlertsEnabled = map[KEY_BUDGET_ALERTS]?.toBooleanStrictOrNull() ?: true,
                themeMode = map[KEY_THEME]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
            )
        }
    }
}
