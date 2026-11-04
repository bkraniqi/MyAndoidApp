package com.moneta.finance.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneta.finance.MainActivity
import com.moneta.finance.R
import com.moneta.finance.data.db.BudgetAlertEntity
import com.moneta.finance.data.db.MonetaDatabase
import com.moneta.finance.domain.DateRange
import com.moneta.finance.domain.FinanceCalculator
import com.moneta.finance.domain.MoneyFormatter
import com.moneta.finance.domain.TransactionType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Posts a notification the first time a budget crosses 80 % and again when it is exceeded,
 * at most once per level, budget and month.
 */
class BudgetAlertService(
    private val context: Context,
    private val db: MonetaDatabase,
) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_budget_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_budget_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    suspend fun check(today: LocalDate = LocalDate.now()) {
        val settings = AppSettings.from(db.settingsDao().getAll())
        if (!settings.budgetAlertsEnabled || !canNotify()) return
        val month = YearMonth.from(today)
        val range = DateRange.month(month)
        val entries = db.transactionDao().getInRange(range.startEpochDay, range.endEpochDay)
        val formatter = MoneyFormatter(settings.currencyCode)
        val budgetDao = db.budgetDao()

        settings.monthlyLimitMinor?.let { limit ->
            val spent = FinanceCalculator.totals(entries).expenseMinor
            evaluate("overall:$month", spent, limit, "Monthly spending limit", formatter)
        }
        val categories = db.categoryDao().getAll().associateBy { it.id }
        val byCategory = FinanceCalculator.byCategory(entries, TransactionType.EXPENSE, null).associate { it.categoryId to it.amountMinor }
        for (budget in budgetDao.getAll()) {
            val name = categories[budget.categoryId]?.name ?: continue
            evaluate("category:${budget.categoryId}:$month", byCategory[budget.categoryId] ?: 0L, budget.amountMinor, name, formatter)
        }
    }

    private suspend fun evaluate(key: String, spent: Long, limit: Long, name: String, formatter: MoneyFormatter) {
        if (limit <= 0) return
        // Integer comparison: spent >= 80 % of limit  <=>  spent * 5 >= limit * 4
        val level = when {
            spent > limit -> 2
            Math.multiplyExact(spent, 5L) >= Math.multiplyExact(limit, 4L) -> 1
            else -> 0
        }
        if (level == 0) return
        val dao = db.budgetDao()
        val previous = dao.getAlert(key)?.level ?: 0
        if (level <= previous) return
        val title = if (level == 2) "$name exceeded" else "$name almost used"
        val text = if (level == 2) {
            "You have spent ${formatter.format(spent)} of ${formatter.format(limit)} this month."
        } else {
            "You have used ${formatter.format(spent)} of ${formatter.format(limit)} this month."
        }
        if (post(key.hashCode(), title, text)) dao.upsertAlert(BudgetAlertEntity(key, level))
    }

    private fun post(id: Int, title: String, text: String): Boolean {
        if (!canNotify()) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_BUDGETS, true)
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF0B6B4F.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "budget_alerts"
    }
}
