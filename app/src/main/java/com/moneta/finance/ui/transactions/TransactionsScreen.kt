package com.moneta.finance.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneta.finance.domain.DateRange
import com.moneta.finance.domain.EntryGroup
import com.moneta.finance.domain.PaymentMethod
import com.moneta.finance.domain.SummaryPeriod
import com.moneta.finance.domain.TransactionFilter
import com.moneta.finance.domain.TransactionType
import com.moneta.finance.ui.Dates
import com.moneta.finance.ui.LocalAppContainer
import com.moneta.finance.ui.TransactionItem
import com.moneta.finance.ui.components.AmountText
import com.moneta.finance.ui.components.ConfirmDialog
import com.moneta.finance.ui.components.EmptyState
import com.moneta.finance.ui.components.LabeledAmount
import com.moneta.finance.ui.components.LocalMoneyFormatter
import com.moneta.finance.ui.components.MonetaCard
import com.moneta.finance.ui.components.ScreenScaffold
import com.moneta.finance.ui.theme.Moneta
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(onOpenTransaction: (TransactionItem) -> Unit) {
    val container = LocalAppContainer.current
    val vm: TransactionsViewModel = viewModel { TransactionsViewModel(container.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TransactionItem?>(null) }
    val formatter = LocalMoneyFormatter.current

    ScreenScaffold(
        title = "Transactions",
        actions = {
            IconButton(onClick = { showFilters = true }) {
                BadgedBox(badge = {
                    if (state.filter.activeCount > 0) Badge { Text(state.filter.activeCount.toString()) }
                }) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Filter transactions")
                }
            }
        },
    ) {
        item(key = "search") {
            TextField(
                value = state.filter.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search description, category, amount…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filter.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        item(key = "period") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SummaryPeriod.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = state.period == p,
                        onClick = { vm.setPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, SummaryPeriod.entries.size),
                    ) { Text(p.label) }
                }
            }
        }
        if (state.filter.isActive) {
            item(key = "activeFilters") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.resultCount} of ${state.totalCount} transactions match",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::clearFilters) { Text("Clear filters") }
                }
            }
        }
        if (state.resultCount > 0) {
            item(key = "summary") {
                MonetaCard {
                    Row(Modifier.fillMaxWidth()) {
                        LabeledAmount("Income", state.totals.incomeMinor, Modifier.weight(1f), color = Moneta.colors.income, style = MaterialTheme.typography.titleSmall)
                        LabeledAmount("Expenses", state.totals.expenseMinor, Modifier.weight(1f), color = Moneta.colors.expense, style = MaterialTheme.typography.titleSmall)
                        LabeledAmount("Net", state.totals.netMinor, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, signed = true)
                    }
                }
            }
        }
        if (!state.loading && state.resultCount == 0) {
            item(key = "empty") {
                if (state.totalCount == 0) {
                    EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "No transactions yet", "Use the + button to add income or an expense.")
                } else {
                    EmptyState(Icons.Filled.SearchOff, "Nothing found", "Try another search term or clear the filters.")
                }
            }
        }
        state.groups.forEach { group ->
            item(key = "g-${group.range.startEpochDay}-${state.period}") {
                GroupCard(
                    group = group,
                    period = state.period,
                    onOpen = onOpenTransaction,
                    onLongPress = { pendingDelete = it },
                )
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            initial = state.filter,
            categories = state.categories,
            onApply = { vm.setFilter(it); showFilters = false },
            onDismiss = { showFilters = false },
        )
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "Delete transaction?",
            text = "\"${item.title}\" (${formatter.format(item.amountMinor)}) will be removed and all balances updated.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(item.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun GroupCard(
    group: EntryGroup<TransactionItem>,
    period: SummaryPeriod,
    onOpen: (TransactionItem) -> Unit,
    onLongPress: (TransactionItem) -> Unit,
) {
    val title = when (period) {
        SummaryPeriod.DAY -> Dates.dayHeader(group.range.start)
        SummaryPeriod.WEEK -> "${Dates.short(group.range.start)} – ${Dates.short(group.range.endInclusive)}"
        SummaryPeriod.MONTH -> Dates.month(YearMonth.from(group.range.start))
    }
    MonetaCard(contentPadding = PaddingValues(top = 14.dp, bottom = 6.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            AmountText(group.totals.netMinor, signed = true, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (period != SummaryPeriod.DAY) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                Text("In ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountText(group.totals.incomeMinor, style = MaterialTheme.typography.bodySmall, color = Moneta.colors.income)
                Text("  ·  Out ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountText(group.totals.expenseMinor, style = MaterialTheme.typography.bodySmall, color = Moneta.colors.expense)
                Text("  ·  ${group.items.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        group.items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            TransactionRow(
                item = item,
                onClick = { onOpen(item) },
                onLongClick = { onLongPress(item) },
                showDate = period != SummaryPeriod.DAY,
            )
        }
    }
}

private enum class RangePreset(val label: String) { ALL("All time"), THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_YEAR("This year"), CUSTOM("Custom…") }

private fun presetRange(p: RangePreset, today: LocalDate): DateRange? = when (p) {
    RangePreset.ALL, RangePreset.CUSTOM -> null
    RangePreset.THIS_MONTH -> DateRange.month(YearMonth.from(today))
    RangePreset.LAST_MONTH -> DateRange.month(YearMonth.from(today).minusMonths(1))
    RangePreset.THIS_YEAR -> DateRange.year(today.year)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    initial: TransactionFilter,
    categories: List<com.moneta.finance.data.db.CategoryEntity>,
    onApply: (TransactionFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(initial) }
    var showRangePicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            FilterLabel("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = draft.type == null, onClick = { draft = draft.copy(type = null, categoryIds = emptySet()) }, label = { Text("All") })
                FilterChip(selected = draft.type == TransactionType.INCOME, onClick = {
                    draft = draft.copy(type = TransactionType.INCOME, categoryIds = draft.categoryIds.filter { id -> categories.any { it.id == id && it.type == TransactionType.INCOME } }.toSet())
                }, label = { Text("Income") })
                FilterChip(selected = draft.type == TransactionType.EXPENSE, onClick = {
                    draft = draft.copy(type = TransactionType.EXPENSE, categoryIds = draft.categoryIds.filter { id -> categories.any { it.id == id && it.type == TransactionType.EXPENSE } }.toSet())
                }, label = { Text("Expenses") })
            }

            FilterLabel("Date")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RangePreset.entries.forEach { p ->
                    val selected = when (p) {
                        RangePreset.ALL -> draft.range == null
                        RangePreset.CUSTOM -> draft.range != null && RangePreset.entries.none { it != RangePreset.CUSTOM && it != RangePreset.ALL && presetRange(it, today) == draft.range }
                        else -> draft.range != null && presetRange(p, today) == draft.range
                    }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (p == RangePreset.CUSTOM) showRangePicker = true else draft = draft.copy(range = presetRange(p, today))
                        },
                        label = {
                            Text(if (p == RangePreset.CUSTOM && selected) "${Dates.short(draft.range!!.start)} – ${Dates.short(draft.range!!.endInclusive)}" else p.label)
                        },
                    )
                }
            }

            FilterLabel("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filter { draft.type == null || it.type == draft.type }.forEach { c ->
                    val selected = c.id in draft.categoryIds
                    FilterChip(
                        selected = selected,
                        onClick = { draft = draft.copy(categoryIds = if (selected) draft.categoryIds - c.id else draft.categoryIds + c.id) },
                        label = { Text(c.name) },
                    )
                }
            }

            FilterLabel("Payment method")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.entries.forEach { m ->
                    val selected = m in draft.paymentMethods
                    FilterChip(
                        selected = selected,
                        onClick = { draft = draft.copy(paymentMethods = if (selected) draft.paymentMethods - m else draft.paymentMethods + m) },
                        label = { Text(m.label) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { draft = TransactionFilter(query = draft.query) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Reset")
                }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Apply") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = draft.range?.start?.let { it.toEpochDay() * DAY_MS },
            initialSelectedEndDateMillis = draft.range?.endInclusive?.let { it.toEpochDay() * DAY_MS },
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis?.let(::utcDate)
                        val end = pickerState.selectedEndDateMillis?.let(::utcDate) ?: start
                        if (start != null && end != null) draft = draft.copy(range = DateRange(minOf(start, end), maxOf(start, end)))
                        showRangePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f), title = {
                Text("Select dates", modifier = Modifier.padding(start = 24.dp, top = 16.dp))
            })
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
}

private const val DAY_MS = 86_400_000L

/** Material date pickers work in UTC midnight milliseconds; convert without applying a time zone shift. */
fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

