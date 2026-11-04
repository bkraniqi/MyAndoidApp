package com.moneta.finance.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.finance.data.FinanceRepository
import com.moneta.finance.domain.FinanceCalculator
import com.moneta.finance.domain.MonthSummary
import com.moneta.finance.domain.MonthTotal
import com.moneta.finance.ui.TransactionItem
import com.moneta.finance.ui.buildItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val openingBalanceMinor: Long = 0,
    val totalBalanceMinor: Long = 0,
    val totalIncomeMinor: Long = 0,
    val totalExpenseMinor: Long = 0,
    val netPositionMinor: Long = 0,
    val month: MonthSummary = MonthSummary(YearMonth.now(), 0, 0),
    val monthlyLimitMinor: Long? = null,
    val savingsGoalMinor: Long? = null,
    val chart: List<MonthTotal> = emptyList(),
    val recent: List<TransactionItem> = emptyList(),
    val transactionCount: Int = 0,
)

class HomeViewModel(repository: FinanceRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        repository.transactionsFlow,
        repository.categoriesFlow,
        repository.settingsFlow,
    ) { transactions, categories, settings ->
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val totals = FinanceCalculator.totals(transactions)
        HomeUiState(
            loading = false,
            today = today,
            openingBalanceMinor = settings.openingBalanceMinor,
            totalBalanceMinor = FinanceCalculator.totalBalance(settings.openingBalanceMinor, transactions),
            totalIncomeMinor = totals.incomeMinor,
            totalExpenseMinor = totals.expenseMinor,
            netPositionMinor = totals.netMinor,
            month = FinanceCalculator.monthSummary(transactions, month),
            monthlyLimitMinor = settings.monthlyLimitMinor,
            savingsGoalMinor = settings.savingsGoalMinor,
            chart = FinanceCalculator.recentMonths(transactions, month, 6),
            recent = buildItems(transactions.take(5), categories),
            transactionCount = transactions.size,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

}
