package com.moneta.finance.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneta.finance.domain.TransactionType
import com.moneta.finance.ui.Dates
import com.moneta.finance.ui.LocalAppContainer
import com.moneta.finance.ui.components.AmountText
import com.moneta.finance.ui.components.BarChart
import com.moneta.finance.ui.components.CategoryBadge
import com.moneta.finance.ui.components.DonutChart
import com.moneta.finance.ui.components.DonutSlice
import com.moneta.finance.ui.components.LabeledAmount
import com.moneta.finance.ui.components.LineChart
import com.moneta.finance.ui.components.LocalMoneyFormatter
import com.moneta.finance.ui.components.MonetaCard
import com.moneta.finance.ui.components.ScreenScaffold
import com.moneta.finance.ui.components.SectionTitle
import com.moneta.finance.ui.home.Legend
import com.moneta.finance.ui.theme.Moneta
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen() {
    val container = LocalAppContainer.current
    val vm: StatisticsViewModel = viewModel { StatisticsViewModel(container.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val sel = state.selection
    var showPicker by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Statistics") {
        item(key = "snapshotTitle") { SectionTitle("Spending at a glance") }
        item(key = "snapshot") {
            val s = state.snapshot
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SnapshotTile("Today", s.todayMinor, Modifier.weight(1f))
                    SnapshotTile("This week", s.weekMinor, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SnapshotTile("This month", s.monthMinor, Modifier.weight(1f))
                    SnapshotTile("This year", s.yearMinor, Modifier.weight(1f))
                }
            }
        }
        item(key = "selector") {
            Column(Modifier.padding(top = 8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StatsMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = sel.mode == m,
                            onClick = { vm.setMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, StatsMode.entries.size),
                        ) { Text(if (m == StatsMode.MONTH) "Month" else "Year") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.step(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period") }
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { showPicker = true }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (sel.mode == StatsMode.MONTH) Dates.month(sel.month) else sel.year.toString(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Icon(Icons.Filled.ExpandMore, contentDescription = "Choose period")
                    }
                    IconButton(onClick = { vm.step(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period") }
                }
            }
        }
        item(key = "summary") { SummaryCard(state) }
        item(key = "activity") { ActivityChartCard(state) }
        if (sel.mode == StatsMode.MONTH) {
            item(key = "weekly") { WeeklyCard(state) }
        }
        item(key = "breakdown") { BreakdownCard(state, onType = vm::setBreakdownType) }
        item(key = "trend") { TrendCard(state) }
        item(key = "allTime") {
            MonetaCard {
                Text("All time", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    LabeledAmount("Total income", state.allTimeTotals.incomeMinor, Modifier.weight(1f), color = Moneta.colors.income, style = MaterialTheme.typography.titleSmall)
                    LabeledAmount("Total expenses", state.allTimeTotals.expenseMinor, Modifier.weight(1f), color = Moneta.colors.expense, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    LabeledAmount("Net", state.allTimeTotals.netMinor, Modifier.weight(1f), signed = true, style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.weight(1f)) {
                        Text("Savings rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(Dates.percent(com.moneta.finance.domain.FinanceCalculator.savingsRate(state.allTimeTotals)), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }

    if (showPicker) {
        PeriodPickerDialog(
            mode = sel.mode,
            initial = sel.month,
            earliestYear = minOf(state.earliestYear, LocalDate.now().year - 1),
            onPick = { vm.setMonth(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun SnapshotTile(label: String, minor: Long, modifier: Modifier) {
    MonetaCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        AmountText(minor, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SummaryCard(state: StatsUiState) {
    val t = state.periodTotals
    val c = Moneta.colors
    MonetaCard {
        Row(Modifier.fillMaxWidth()) {
            LabeledAmount("Income", t.incomeMinor, Modifier.weight(1f), color = c.income)
            LabeledAmount("Expenses", t.expenseMinor, Modifier.weight(1f), color = c.expense)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            LabeledAmount("Balance", t.netMinor, Modifier.weight(1f), signed = true)
            Column(Modifier.weight(1f)) {
                Text("Savings rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(
                    Dates.percent(state.savingsRate),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        state.savingsRate == null -> MaterialTheme.colorScheme.onSurface
                        state.savingsRate < 0 -> c.expense
                        else -> c.income
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        LabeledAmount("Average daily spending", state.averageDailyExpense, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ActivityChartCard(state: StatsUiState) {
    val sel = state.selection
    val c = Moneta.colors
    var selected by rememberSaveable(sel.mode, sel.month) { mutableStateOf<Int?>(null) }
    MonetaCard {
        if (sel.mode == StatsMode.MONTH) {
            Text("Daily spending", style = MaterialTheme.typography.titleMedium)
            val focus = selected?.let { state.daily.getOrNull(it) }
            Text(
                focus?.let { Dates.medium(it.date) } ?: "Tap a bar for details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (focus != null) {
                Spacer(Modifier.height(8.dp))
                Row {
                    LabeledAmount("Spent", focus.expenseMinor, Modifier.weight(1f), color = c.expense, style = MaterialTheme.typography.titleSmall)
                    LabeledAmount("Received", focus.incomeMinor, Modifier.weight(1f), color = c.income, style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.height(14.dp))
            BarChart(
                series = listOf(state.daily.map { it.expenseMinor }),
                colors = listOf(c.expense),
                labels = state.daily.map { it.date.dayOfMonth.toString() },
                selectedIndex = selected,
                onSelect = { selected = it },
                labelEvery = 5,
                description = "Daily spending in ${Dates.month(sel.month)}",
            )
        } else {
            Text("Monthly income and expenses", style = MaterialTheme.typography.titleMedium)
            val focus = selected?.let { state.monthly.getOrNull(it) }
            Text(
                focus?.let { Dates.month(it.month) } ?: "Tap a month for details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (focus != null) {
                Spacer(Modifier.height(8.dp))
                Row {
                    LabeledAmount("Income", focus.incomeMinor, Modifier.weight(1f), color = c.income, style = MaterialTheme.typography.titleSmall)
                    LabeledAmount("Expenses", focus.expenseMinor, Modifier.weight(1f), color = c.expense, style = MaterialTheme.typography.titleSmall)
                    LabeledAmount("Net", focus.netMinor, Modifier.weight(1f), signed = true, style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.height(14.dp))
            BarChart(
                series = listOf(state.monthly.map { it.incomeMinor }, state.monthly.map { it.expenseMinor }),
                colors = listOf(c.income, c.expense),
                labels = state.monthly.map { Dates.monthNarrow(it.month) },
                selectedIndex = selected,
                onSelect = { selected = it },
                description = "Monthly income and expenses in ${sel.year}",
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Legend("Income", c.income)
                Spacer(Modifier.width(16.dp))
                Legend("Expenses", c.expense)
            }
        }
    }
}

@Composable
private fun WeeklyCard(state: StatsUiState) {
    val max = state.weekly.maxOfOrNull { it.expenseMinor }?.coerceAtLeast(1L) ?: 1L
    MonetaCard {
        Text("Weekly spending", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        state.weekly.forEach { w ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${Dates.short(w.range.start)} – ${Dates.short(w.range.endInclusive)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(120.dp),
                )
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(
                        Modifier
                            .fillMaxWidth((w.expenseMinor.toDouble() / max.toDouble()).toFloat())
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Moneta.colors.expense),
                    )
                }
                Spacer(Modifier.width(12.dp))
                AmountText(w.expenseMinor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp).padding(start = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownCard(state: StatsUiState, onType: (TransactionType) -> Unit) {
    val type = state.selection.breakdownType
    var selected by remember(state.breakdown) { mutableStateOf<Int?>(null) }
    val formatter = LocalMoneyFormatter.current
    val total = state.breakdown.sumOf { it.amountMinor }
    MonetaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("By category", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = type == TransactionType.EXPENSE, onClick = { onType(TransactionType.EXPENSE) }, label = { Text("Expenses") })
                FilterChip(selected = type == TransactionType.INCOME, onClick = { onType(TransactionType.INCOME) }, label = { Text("Income") })
            }
        }
        if (state.breakdown.isEmpty()) {
            Text(
                "No ${if (type == TransactionType.EXPENSE) "expenses" else "income"} in this period.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            return@MonetaCard
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                slices = state.breakdown.map { DonutSlice(it.amountMinor, Color(state.categories[it.categoryId]?.color ?: 0xFF7D8A86.toInt())) },
                selectedIndex = selected,
                size = 200.dp,
            ) {
                val focus = selected?.let { state.breakdown.getOrNull(it) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
                    Text(
                        focus?.let { state.categories[it.categoryId]?.name } ?: "Total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatter.format(focus?.amountMinor ?: total),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (focus != null) Text(Dates.percent(focus.sharePercent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        state.breakdown.forEachIndexed { i, ca ->
            val cat = state.categories[ca.categoryId]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (selected == i) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                    .clickable { selected = if (selected == i) null else i }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(cat?.iconKey ?: "more", cat?.color ?: 0xFF7D8A86.toInt(), size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat?.name ?: "Unknown", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (i < 3 && type == TransactionType.EXPENSE) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                Text("#${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                    Text(Dates.percent(ca.sharePercent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AmountText(ca.amountMinor, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun TrendCard(state: StatsUiState) {
    val year = state.selection.year
    val currentMonth = YearMonth.now()
    // Months after the current one have no data yet; show only months that have happened.
    val points = state.balanceTrend.filter { !it.month.isAfter(currentMonth) }
    var selected by rememberSaveable(year) { mutableIntStateOf(-1) }
    LaunchedEffect(points.size) { if (selected >= points.size) selected = -1 }
    MonetaCard {
        Text("Balance trend $year", style = MaterialTheme.typography.titleMedium)
        Text("Total balance at the end of each month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (points.isEmpty()) {
            Text("This year has not started yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            return@MonetaCard
        }
        val focus = points.getOrNull(selected) ?: points.last()
        val monthly = state.monthly.firstOrNull { it.month == focus.month }
        Spacer(Modifier.height(10.dp))
        Row {
            LabeledAmount("Balance · ${Dates.monthShort(focus.month)}", focus.balanceMinor, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            LabeledAmount("Monthly balance", monthly?.netMinor ?: 0L, Modifier.weight(1f), signed = true, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(14.dp))
        LineChart(
            values = points.map { it.balanceMinor },
            labels = points.map { Dates.monthNarrow(it.month) },
            color = MaterialTheme.colorScheme.primary,
            selectedIndex = selected.takeIf { it >= 0 },
            onSelect = { selected = it ?: -1 },
            description = "Balance trend for $year",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodPickerDialog(
    mode: StatsMode,
    initial: YearMonth,
    earliestYear: Int,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember { mutableIntStateOf(initial.year) }
    val thisYear = LocalDate.now().year
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == StatsMode.MONTH) "Choose month" else "Choose year") },
        text = {
            if (mode == StatsMode.MONTH) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous year") }
                        Text(year.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year") }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(maxItemsInEachRow = 3, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..12).forEach { m ->
                            val ym = YearMonth.of(year, m)
                            val selected = ym == initial
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { onPick(ym) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    Dates.monthShort(ym),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            } else {
                FlowRow(maxItemsInEachRow = 3, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (earliestYear..thisYear + 1).reversed().forEach { y ->
                        val selected = y == initial.year
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.medium)
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onPick(YearMonth.of(y, initial.monthValue)) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(y.toString(), color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

