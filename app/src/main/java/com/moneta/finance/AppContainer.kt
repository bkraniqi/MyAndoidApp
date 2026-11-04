package com.moneta.finance

import android.content.Context
import com.moneta.finance.data.BudgetAlertService
import com.moneta.finance.data.FinanceRepository
import com.moneta.finance.data.db.MonetaDatabase
import com.moneta.finance.security.AppLockManager

/** Manual dependency container living for the whole process. */
class AppContainer(context: Context) {
    val database: MonetaDatabase = MonetaDatabase.build(context)
    val repository = FinanceRepository(database)
    val budgetAlerts = BudgetAlertService(context, database)
    val appLock = AppLockManager(context)

    init {
        repository.onLedgerChanged = { budgetAlerts.check() }
    }
}
