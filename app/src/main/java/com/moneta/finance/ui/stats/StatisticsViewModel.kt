package com.moneta.finance.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.finance.data.FinanceRepository
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.domain.BalancePoint
import com.moneta.finance.domain.CategoryAmount
import com.moneta.finance.domain.DateRange
import com.moneta.finance.domain.DayTotal
import com.moneta.finance.domain.FinanceCalculator
import com.moneta.finance.domain.MonthTotal
import com.moneta.finance.domain.PeriodTotal
import com.moneta.finance.domain.SpendingSnapshot
import com.moneta.finance.domain.Totals
import com.moneta.finance.domain.TransactionType
import com.moneta.finance.ui.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

enum class StatsMode { MONTH, YEAR }

data class StatsSelection(
    val mode: StatsMode = StatsMode.MONTH,
    val month: YearMonth = YearMonth.now(),
    val breakdownType: TransactionType = TransactionType.EXPENSE,
) {
    val year: Int get() = month.year
    val range: DateRange get() = if (mode == StatsMode.MONTH) DateRange.month(month) else DateRange.year(year)
}

data class StatsUiState(
    val loading: Boolean = true,
    val selection: StatsSelection = StatsSelection(),
    val snapshot: SpendingSnapshot = SpendingSnapshot(0, 0, 0, 0),
    val periodTotals: Totals = Totals(),
    val allTimeTotals: Totals = Totals(),
    val savingsRate: Double? = null,
    val averageDailyExpense: Long = 0,
    val daily: List<DayTotal> = emptyList(),
    val weekly: List<PeriodTotal> = emptyList(),
    val monthly: List<MonthTotal> = emptyList(),
    val breakdown: List<CategoryAmount> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val balanceTrend: List<BalancePoint> = emptyList(),
    val earliestYear: Int = LocalDate.now().year,
)

class StatisticsViewModel(repository: FinanceRepository) : ViewModel() {
    private val selection = MutableStateFlow(StatsSelection())

    val state: StateFlow<StatsUiState> = combine(
        repository.transactionsFlow,
        repository.categoriesFlow,
        repository.settingsFlow,
        selection,
    ) { transactions, categories, settings, sel ->
        val today = LocalDate.now()
        val range = sel.range
        val periodTotals = FinanceCalculator.totals(transactions, range)
        // Average over elapsed days only, so a running month is not diluted by future days.
        val avgRange = if (range.endInclusive.isAfter(today) && !range.start.isAfter(today)) DateRange(range.start, today) else range
        StatsUiState(
            loading = false,
            selection = sel,
            snapshot = FinanceCalculator.spendingSnapshot(transactions, today, Dates.firstDayOfWeek),
            periodTotals = periodTotals,
            allTimeTotals = FinanceCalculator.totals(transactions),
            savingsRate = FinanceCalculator.savingsRate(periodTotals),
            averageDailyExpense = FinanceCalculator.averageDailyExpense(transactions, avgRange),
            daily = if (sel.mode == StatsMode.MONTH) FinanceCalculator.dailyTotals(transactions, range) else emptyList(),
            weekly = if (sel.mode == StatsMode.MONTH) FinanceCalculator.weeklyTotals(transactions, sel.month, Dates.firstDayOfWeek) else emptyList(),
            monthly = FinanceCalculator.monthlyTotals(transactions, sel.year),
            breakdown = FinanceCalculator.byCategory(transactions, sel.breakdownType, range),
            categories = categories.associateBy { it.id },
            balanceTrend = FinanceCalculator.balanceTrend(settings.openingBalanceMinor, transactions, sel.year),
            earliestYear = transactions.minOfOrNull { it.epochDay }?.let { LocalDate.ofEpochDay(it).year }?.coerceAtMost(today.year) ?: today.year,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setMode(mode: StatsMode) = selection.update { it.copy(mode = mode) }
    fun setMonth(month: YearMonth) = selection.update { it.copy(month = month) }
    fun setBreakdownType(type: TransactionType) = selection.update { it.copy(breakdownType = type) }

    fun step(delta: Int) = selection.update {
        if (it.mode == StatsMode.MONTH) it.copy(month = it.month.plusMonths(delta.toLong()))
        else it.copy(month = it.month.plusYears(delta.toLong()))
    }
}
