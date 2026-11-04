package com.moneta.finance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moneta.finance.domain.TransactionType
import com.moneta.finance.ui.budgets.BudgetsScreen
import com.moneta.finance.ui.categories.CategoriesScreen
import com.moneta.finance.ui.edit.EditTransactionScreen
import com.moneta.finance.ui.home.HomeScreen
import com.moneta.finance.ui.recurring.RecurringEditScreen
import com.moneta.finance.ui.recurring.RecurringScreen
import com.moneta.finance.ui.settings.SettingsScreen
import com.moneta.finance.ui.stats.StatisticsScreen
import com.moneta.finance.ui.transactions.TransactionsScreen

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val STATISTICS = "statistics"
    const val BUDGETS = "budgets"
    const val SETTINGS = "settings"
    const val EDIT = "edit?type={type}&id={id}"
    const val CATEGORIES = "categories?type={type}"
    const val RECURRING = "recurring"
    const val RECURRING_EDIT = "recurring/edit?id={id}"

    fun edit(type: TransactionType, id: Long? = null) = "edit?type=${type.name}&id=${id ?: -1L}"
    fun categories(type: TransactionType = TransactionType.EXPENSE) = "categories?type=${type.name}"
    fun recurringEdit(id: Long? = null) = "recurring/edit?id=${id ?: -1L}"
}

private data class TopLevel(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TopLevel(Routes.TRANSACTIONS, "Transactions", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    TopLevel(Routes.STATISTICS, "Statistics", Icons.Filled.Insights, Icons.Outlined.Insights),
    TopLevel(Routes.BUDGETS, "Budgets", Icons.Filled.Savings, Icons.Outlined.Savings),
    TopLevel(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun MonetaNavigation(openBudgetsRequest: Int) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTopLevel = topLevel.any { it.route == route }
    val showFab = isTopLevel && route != Routes.SETTINGS

    LaunchedEffect(openBudgetsRequest) {
        if (openBudgetsRequest > 0) nav.switchTab(Routes.BUDGETS)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = isTopLevel,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                    topLevel.forEach { item ->
                        val selected = route == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) nav.switchTab(item.route) },
                            icon = { Icon(if (selected) item.selected else item.unselected, contentDescription = null) },
                            label = { Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = showFab, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                FloatingActionButton(
                    onClick = { nav.navigate(Routes.edit(TransactionType.EXPENSE)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAdd = { type -> nav.navigate(Routes.edit(type)) },
                    onOpenTransaction = { item -> nav.navigate(Routes.edit(item.type, item.id)) },
                    onSeeAll = { nav.switchTab(Routes.TRANSACTIONS) },
                    onOpenBudgets = { nav.switchTab(Routes.BUDGETS) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(onOpenTransaction = { item -> nav.navigate(Routes.edit(item.type, item.id)) })
            }
            composable(Routes.STATISTICS) { StatisticsScreen() }
            composable(Routes.BUDGETS) { BudgetsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenCategories = { nav.navigate(Routes.categories()) },
                    onOpenRecurring = { nav.navigate(Routes.RECURRING) },
                )
            }
            composable(
                Routes.EDIT,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType; defaultValue = TransactionType.EXPENSE.name },
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                ),
                enterTransition = { slideInVertically(tween(300)) { it / 6 } + fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(150)) },
                popExitTransition = { slideOutVertically(tween(250)) { it / 6 } + fadeOut(tween(250)) },
            ) { entry ->
                val type = TransactionType.valueOf(entry.arguments?.getString("type") ?: TransactionType.EXPENSE.name)
                val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
                EditTransactionScreen(
                    initialType = type,
                    transactionId = id,
                    onClose = { nav.popBackStack() },
                    onManageCategories = { t -> nav.navigate(Routes.categories(t)) },
                )
            }
            composable(
                Routes.CATEGORIES,
                arguments = listOf(navArgument("type") { type = NavType.StringType; defaultValue = TransactionType.EXPENSE.name }),
                enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(250)) },
            ) { entry ->
                val type = TransactionType.valueOf(entry.arguments?.getString("type") ?: TransactionType.EXPENSE.name)
                CategoriesScreen(initialType = type, onBack = { nav.popBackStack() })
            }
            composable(
                Routes.RECURRING,
                enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(250)) },
            ) {
                RecurringScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { id -> nav.navigate(Routes.recurringEdit(id)) },
                )
            }
            composable(
                Routes.RECURRING_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                enterTransition = { slideInVertically(tween(300)) { it / 6 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutVertically(tween(250)) { it / 6 } + fadeOut(tween(250)) },
            ) { entry ->
                RecurringEditScreen(
                    ruleId = entry.arguments?.getLong("id")?.takeIf { it > 0 },
                    onClose = { nav.popBackStack() },
                    onManageCategories = { t -> nav.navigate(Routes.categories(t)) },
                )
            }
        }
    }
}
