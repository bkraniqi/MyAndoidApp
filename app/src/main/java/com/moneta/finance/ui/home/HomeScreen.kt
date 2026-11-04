package com.moneta.finance.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneta.finance.domain.Money
import com.moneta.finance.domain.TransactionType
import com.moneta.finance.ui.Dates
import com.moneta.finance.ui.LocalAppContainer
import com.moneta.finance.ui.TransactionItem
import com.moneta.finance.ui.components.AmountText
import com.moneta.finance.ui.components.BarChart
import com.moneta.finance.ui.components.BudgetProgressBar
import com.moneta.finance.ui.components.EmptyState
import com.moneta.finance.ui.components.LabeledAmount
import com.moneta.finance.ui.components.MonetaCard
import com.moneta.finance.ui.components.ScreenScaffold
import com.moneta.finance.ui.components.SectionTitle
import com.moneta.finance.ui.theme.Moneta
import com.moneta.finance.ui.transactions.TransactionRow

@Composable
fun HomeScreen(
    onAdd: (TransactionType) -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    onSeeAll: () -> Unit,
    onOpenBudgets: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container.repository) }
    val state by vm.state.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Moneta") {
        item(key = "date") {
            Text(
                Dates.weekday(state.today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        item(key = "hero") { BalanceHero(state) }
        item(key = "quick") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { onAdd(TransactionType.EXPENSE) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Expense")
                }
                FilledTonalButton(
                    onClick = { onAdd(TransactionType.INCOME) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Income")
                }
            }
        }
        item(key = "month") { MonthOverview(state, onOpenBudgets) }
        item(key = "chart") { IncomeExpenseCard(state) }
        item(key = "recentTitle") {
            SectionTitle("Recent transactions") {
                if (state.transactionCount > 0) TextButton(onClick = onSeeAll) { Text("See all") }
            }
        }
        item(key = "recent") {
            MonetaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                if (!state.loading && state.recent.isEmpty()) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "No transactions yet",
                        body = "Tap + to record your first income or expense. Everything stays on this phone.",
                    )
                } else {
                    state.recent.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        TransactionRow(item, onClick = { onOpenTransaction(item) }, showDate = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHero(state: HomeUiState) {
    val c = Moneta.colors
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)))
            .padding(24.dp),
    ) {
        Column {
            Text("Total balance", style = MaterialTheme.typography.titleSmall, color = c.onHero.copy(alpha = 0.8f),
                modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            AmountText(
                state.totalBalanceMinor,
                style = MaterialTheme.typography.displaySmall,
                color = c.onHero,
                modifier = Modifier.animateContentSize(),
            )
            if (state.openingBalanceMinor != 0L) {
                Spacer(Modifier.height(2.dp))
                Row {
                    Text("Includes opening balance ", style = MaterialTheme.typography.bodySmall, color = c.onHero.copy(alpha = 0.7f))
                    AmountText(state.openingBalanceMinor, style = MaterialTheme.typography.bodySmall, color = c.onHero.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.onHero.copy(alpha = 0.15f)))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroStat("Income", state.totalIncomeMinor, c.gold, Modifier.weight(1f))
                HeroStat("Expenses", state.totalExpenseMinor, c.onHero, Modifier.weight(1f))
                HeroStat("Net position", state.netPositionMinor, c.onHero, Modifier.weight(1f), signed = true)
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, minor: Long, color: Color, modifier: Modifier, signed: Boolean = false) {
    LabeledAmount(
        label = label,
        minor = minor,
        modifier = modifier,
        color = color,
        labelColor = Moneta.colors.onHero.copy(alpha = 0.7f),
        style = MaterialTheme.typography.titleSmall,
        signed = signed,
    )
}

@Composable
private fun MonthOverview(state: HomeUiState, onOpenBudgets: () -> Unit) {
    val m = state.month
    val c = Moneta.colors
    MonetaCard(onClick = onOpenBudgets) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Dates.month(m.month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    m.spentPercent?.let { "${Dates.percent(it)} spent" } ?: "No income yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            LabeledAmount("Income", m.incomeMinor, Modifier.weight(1f), color = c.income)
            LabeledAmount("Expenses", m.expenseMinor, Modifier.weight(1f), color = c.expense)
            LabeledAmount("Remaining", m.remainingMinor, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        if (m.incomeMinor > 0) {
            BudgetProgressBar(m.expenseMinor, m.incomeMinor)
            Spacer(Modifier.height(6.dp))
            Text(
                "${Dates.percent(m.spentPercent)} of this month's income spent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.monthlyLimitMinor?.let { limit ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                Spacer(Modifier.width(8.dp))
                Text("Spending limit", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                AmountText(m.expenseMinor, style = MaterialTheme.typography.bodyMedium)
                Text(" / ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountText(limit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            BudgetProgressBar(m.expenseMinor, limit, height = 6.dp)
        }
        state.savingsGoalMinor?.let { goal ->
            Spacer(Modifier.height(14.dp))
            val saved = m.remainingMinor.coerceAtLeast(0)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.income))
                Spacer(Modifier.width(8.dp))
                Text("Savings goal", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    Dates.percent(Money.percent(saved, goal)?.coerceAtMost(999.9)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IncomeExpenseCard(state: HomeUiState) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val c = Moneta.colors
    val chart = state.chart
    MonetaCard {
        Text("Income vs expenses", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        val focus = selected?.let { chart.getOrNull(it) } ?: chart.lastOrNull()
        if (focus != null) {
            Text(Dates.month(focus.month), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                LabeledAmount("Income", focus.incomeMinor, Modifier.weight(1f), color = c.income, style = MaterialTheme.typography.titleSmall)
                LabeledAmount("Expenses", focus.expenseMinor, Modifier.weight(1f), color = c.expense, style = MaterialTheme.typography.titleSmall)
                LabeledAmount("Net", focus.netMinor, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, signed = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        BarChart(
            series = listOf(chart.map { it.incomeMinor }, chart.map { it.expenseMinor }),
            colors = listOf(c.income, c.expense),
            labels = chart.map { Dates.monthShort(it.month) },
            selectedIndex = selected,
            onSelect = { selected = it },
            description = "Income and expenses for the last six months",
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend("Income", c.income)
            Spacer(Modifier.width(16.dp))
            Legend("Expenses", c.expense)
        }
    }
}

@Composable
fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
