package com.moneta.finance.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.finance.data.FinanceRepository
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.domain.DateRange
import com.moneta.finance.domain.FinanceCalculator
import com.moneta.finance.domain.MonthSummary
import com.moneta.finance.domain.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CategoryBudgetUi(val category: CategoryEntity, val limitMinor: Long, val spentMinor: Long) {
    val remainingMinor: Long get() = Math.subtractExact(limitMinor, spentMinor)
}

data class BudgetsUiState(
    val loading: Boolean = true,
    val month: MonthSummary = MonthSummary(YearMonth.now(), 0, 0),
    val monthlyLimitMinor: Long? = null,
    val savingsGoalMinor: Long? = null,
    val budgets: List<CategoryBudgetUi> = emptyList(),
    val availableCategories: List<CategoryEntity> = emptyList(),
    val daysLeft: Int = 0,
    val alertsEnabled: Boolean = true,
)

class BudgetsViewModel(private val repository: FinanceRepository) : ViewModel() {
    val state: StateFlow<BudgetsUiState> = combine(
        repository.transactionsFlow,
        repository.categoriesFlow,
        repository.budgetsFlow,
        repository.settingsFlow,
    ) { transactions, categories, budgets, settings ->
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val spentByCategory = FinanceCalculator.byCategory(transactions, TransactionType.EXPENSE, DateRange.month(month))
            .associate { it.categoryId to it.amountMinor }
        val byId = categories.associateBy { it.id }
        val budgetUis = budgets.mapNotNull { b ->
            byId[b.categoryId]?.let { CategoryBudgetUi(it, b.amountMinor, spentByCategory[b.categoryId] ?: 0L) }
        }.sortedByDescending { if (it.limitMinor == 0L) 0.0 else it.spentMinor.toDouble() / it.limitMinor }
        val budgeted = budgets.map { it.categoryId }.toSet()
        BudgetsUiState(
            loading = false,
            month = FinanceCalculator.monthSummary(transactions, month),
            monthlyLimitMinor = settings.monthlyLimitMinor,
            savingsGoalMinor = settings.savingsGoalMinor,
            budgets = budgetUis,
            availableCategories = categories.filter { it.type == TransactionType.EXPENSE && it.id !in budgeted },
            daysLeft = (month.lengthOfMonth() - today.dayOfMonth + 1),
            alertsEnabled = settings.budgetAlertsEnabled,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun setMonthlyLimit(amount: Long?) = viewModelScope.launch { repository.setMonthlyLimit(amount) }
    fun setSavingsGoal(amount: Long?) = viewModelScope.launch { repository.setSavingsGoal(amount) }
    fun setCategoryBudget(categoryId: Long, amount: Long?) = viewModelScope.launch { repository.setCategoryBudget(categoryId, amount) }
}
