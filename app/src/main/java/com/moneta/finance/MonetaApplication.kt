package com.moneta.finance

import android.app.Application
import com.moneta.finance.data.RecurringWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class MonetaApplication : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.budgetAlerts.createChannel()
        appScope.launch {
            runCatching { container.repository.processRecurring(LocalDate.now()) }
        }
        RecurringWorker.schedule(this)
    }
}
