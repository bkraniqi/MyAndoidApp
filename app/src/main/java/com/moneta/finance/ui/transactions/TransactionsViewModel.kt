package com.moneta.finance.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.finance.data.FinanceRepository
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.domain.EntryGroup
import com.moneta.finance.domain.FinanceCalculator
import com.moneta.finance.domain.SummaryPeriod
import com.moneta.finance.domain.Totals
import com.moneta.finance.domain.TransactionFilter
import com.moneta.finance.domain.TransactionGrouping
import com.moneta.finance.ui.Dates
import com.moneta.finance.ui.TransactionItem
import com.moneta.finance.ui.buildItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionsUiState(
    val loading: Boolean = true,
    val filter: TransactionFilter = TransactionFilter(),
    val period: SummaryPeriod = SummaryPeriod.DAY,
    val groups: List<EntryGroup<TransactionItem>> = emptyList(),
    val totals: Totals = Totals(),
    val resultCount: Int = 0,
    val totalCount: Int = 0,
    val categories: List<CategoryEntity> = emptyList(),
)

class TransactionsViewModel(private val repository: FinanceRepository) : ViewModel() {
    private val filter = MutableStateFlow(TransactionFilter())
    private val period = MutableStateFlow(SummaryPeriod.DAY)

    val state: StateFlow<TransactionsUiState> = combine(
        repository.transactionsFlow,
        repository.categoriesFlow,
        filter,
        period,
    ) { transactions, categories, f, p ->
        val items = buildItems(transactions, categories).filter { f.matches(it, it.searchText) }
        TransactionsUiState(
            loading = false,
            filter = f,
            period = p,
            groups = TransactionGrouping.group(items, p, Dates.firstDayOfWeek),
            totals = FinanceCalculator.totals(items),
            resultCount = items.size,
            totalCount = transactions.size,
            categories = categories,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }
    fun setFilter(newFilter: TransactionFilter) = filter.update { newFilter.copy(query = it.query) }
    fun clearFilters() = filter.update { TransactionFilter(query = it.query) }
    fun setPeriod(p: SummaryPeriod) { period.value = p }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }
}
