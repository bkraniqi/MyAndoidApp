package com.moneta.finance.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.moneta.finance.AppContainer
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.data.db.TransactionEntity
import com.moneta.finance.domain.LedgerEntry
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** A transaction joined with its category for display. */
data class TransactionItem(
    val tx: TransactionEntity,
    val category: CategoryEntity?,
) : LedgerEntry by tx {
    val id: Long get() = tx.id
    val title: String get() = tx.description.ifBlank { category?.name ?: "Transaction" }
    val date: LocalDate get() = LocalDate.ofEpochDay(tx.epochDay)
    val isRecurring: Boolean get() = tx.recurringRuleId != null
    val searchText: String get() = listOf(tx.description, tx.notes, category?.name.orEmpty(), tx.paymentMethod.label).joinToString(" ")
}

fun buildItems(transactions: List<TransactionEntity>, categories: List<CategoryEntity>): List<TransactionItem> {
    val byId = categories.associateBy { it.id }
    return transactions.map { TransactionItem(it, byId[it.categoryId]) }
}

object Dates {
    private val locale: Locale get() = Locale.getDefault()

    val firstDayOfWeek get() = WeekFields.of(locale).firstDayOfWeek

    fun medium(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) {
            date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        } else {
            date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale))
        }
    }

    fun short(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", locale))

    fun month(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { it.titlecase(locale) }

    fun monthShort(month: YearMonth): String = month.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
        .replaceFirstChar { it.titlecase(locale) }

    fun monthNarrow(month: YearMonth): String = month.month.getDisplayName(TextStyle.NARROW_STANDALONE, locale)

    fun weekday(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))

    fun percent(value: Double?): String = if (value == null) "—" else String.format(locale, "%.1f %%", value)
}
