package com.moneta.finance.ui.budgets

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.domain.Money
import com.moneta.finance.ui.Dates
import com.moneta.finance.ui.LocalAppContainer
import com.moneta.finance.ui.components.AmountInputDialog
import com.moneta.finance.ui.components.AmountText
import com.moneta.finance.ui.components.BudgetProgressBar
import com.moneta.finance.ui.components.CategoryBadge
import com.moneta.finance.ui.components.EmptyState
import com.moneta.finance.ui.components.LabeledAmount
import com.moneta.finance.ui.components.LocalMoneyFormatter
import com.moneta.finance.ui.components.MonetaCard
import com.moneta.finance.ui.components.ScreenScaffold
import com.moneta.finance.ui.components.SectionTitle
import com.moneta.finance.ui.theme.Moneta

private sealed interface BudgetDialog {
    data object MonthlyLimit : BudgetDialog
    data object SavingsGoal : BudgetDialog
    data object PickCategory : BudgetDialog
    data class Category(val category: CategoryEntity, val current: Long?) : BudgetDialog
}

@Composable
fun BudgetsScreen() {
    val container = LocalAppContainer.current
    val vm: BudgetsViewModel = viewModel { BudgetsViewModel(container.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<BudgetDialog?>(null) }
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(container.budgetAlerts.canNotify()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = container.budgetAlerts.canNotify()
    }
    val hasAnyBudget = state.monthlyLimitMinor != null || state.budgets.isNotEmpty()

    ScreenScaffold(title = "Budgets") {
        item(key = "month") {
            Text(
                "${Dates.month(state.month.month)} · ${state.daysLeft} ${if (state.daysLeft == 1) "day" else "days"} left",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (hasAnyBudget && state.alertsEnabled && !notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item(key = "notify") {
                MonetaCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Allow notifications to be warned when you reach 80 % of a budget or exceed it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }) { Text("Allow notifications") }
                }
            }
        }
        item(key = "limit") {
            MonetaCard(onClick = { dialog = BudgetDialog.MonthlyLimit }) {
                Text("Monthly spending limit", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val limit = state.monthlyLimitMinor
                if (limit == null) {
                    Text("Set a limit for all expenses this month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { dialog = BudgetDialog.MonthlyLimit }) { Text("Set limit") }
                } else {
                    val spent = state.month.expenseMinor
                    Row(verticalAlignment = Alignment.Bottom) {
                        AmountText(spent, style = MaterialTheme.typography.headlineMedium)
                        Text(" of ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AmountText(limit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    BudgetProgressBar(spent, limit, height = 12.dp)
                    Spacer(Modifier.height(10.dp))
                    RemainingLine(Math.subtractExact(limit, spent), state.daysLeft)
                }
            }
        }
        item(key = "goal") {
            MonetaCard(onClick = { dialog = BudgetDialog.SavingsGoal }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(10.dp))
                    Text("Monthly savings goal", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                val goal = state.savingsGoalMinor
                if (goal == null) {
                    Text("Decide how much of your income you want to keep each month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { dialog = BudgetDialog.SavingsGoal }) { Text("Set goal") }
                } else {
                    val saved = state.month.remainingMinor
                    Row {
                        LabeledAmount("Saved this month", saved, Modifier.weight(1f), color = if (saved >= goal) Moneta.colors.income else Color.Unspecified)
                        LabeledAmount("Goal", goal, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    GoalProgress(saved.coerceAtLeast(0), goal)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (saved >= goal) "Goal reached — well done." else "${LocalMoneyFormatter.current.format(Math.subtractExact(goal, saved.coerceAtLeast(0)))} to go",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "catTitle") {
            SectionTitle("Category budgets") {
                if (state.availableCategories.isNotEmpty()) {
                    TextButton(onClick = { dialog = BudgetDialog.PickCategory }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }
        }
        if (!state.loading && state.budgets.isEmpty()) {
            item(key = "catEmpty") {
                MonetaCard {
                    EmptyState(Icons.Filled.Savings, "No category budgets", "Add a budget to keep an eye on groceries, restaurants or anything else.")
                }
            }
        }
        state.budgets.forEach { b ->
            item(key = "b-${b.category.id}") {
                MonetaCard(onClick = { dialog = BudgetDialog.Category(b.category, b.limitMinor) }, contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(b.category.iconKey, b.category.color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.category.name, style = MaterialTheme.typography.bodyLarge)
                            Row {
                                AmountText(b.spentMinor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(" of ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AmountText(b.limitMinor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            AmountText(
                                b.remainingMinor,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (b.remainingMinor < 0) Moneta.colors.expense else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(if (b.remainingMinor < 0) "over" else "left", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    BudgetProgressBar(b.spentMinor, b.limitMinor, height = 8.dp)
                }
            }
        }
    }

    when (val d = dialog) {
        BudgetDialog.MonthlyLimit -> AmountInputDialog(
            title = "Monthly spending limit",
            message = "You'll be notified at 80 % and when the limit is exceeded.",
            initialMinor = state.monthlyLimitMinor,
            onConfirm = { vm.setMonthlyLimit(it) },
            onDismiss = { dialog = null },
            clearLabel = "Remove",
        )
        BudgetDialog.SavingsGoal -> AmountInputDialog(
            title = "Monthly savings goal",
            message = "Savings are this month's income minus expenses.",
            initialMinor = state.savingsGoalMinor,
            onConfirm = { vm.setSavingsGoal(it) },
            onDismiss = { dialog = null },
            clearLabel = "Remove",
        )
        BudgetDialog.PickCategory -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Choose a category") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(state.availableCategories, key = { it.id }) { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { dialog = BudgetDialog.Category(c, null) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryBadge(c.iconKey, c.color, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        is BudgetDialog.Category -> AmountInputDialog(
            title = "Budget for ${d.category.name}",
            message = "Monthly limit for this category.",
            initialMinor = d.current,
            onConfirm = { vm.setCategoryBudget(d.category.id, it) },
            onDismiss = { dialog = null },
            clearLabel = "Remove budget",
        )
        null -> Unit
    }
}

@Composable
private fun RemainingLine(remaining: Long, daysLeft: Int) {
    val f = LocalMoneyFormatter.current
    val text = if (remaining >= 0) {
        val perDay = if (daysLeft > 0) remaining / daysLeft else remaining
        "${f.format(remaining)} left · about ${f.format(perDay)} per day"
    } else {
        "${f.format(-remaining)} over the limit"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (remaining < 0) Moneta.colors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GoalProgress(saved: Long, goal: Long) {
    // Reuse the bar but invert semantics: reaching the goal is good, so colour it as income.
    val pct = Money.percent(saved, goal) ?: 0.0
    androidx.compose.material3.LinearProgressIndicator(
        progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(10.dp),
        color = Moneta.colors.income,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}
