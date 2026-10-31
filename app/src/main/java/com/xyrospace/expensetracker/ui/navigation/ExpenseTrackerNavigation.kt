package com.xyrospace.expensetracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.hilt.navigation.compose.hiltViewModel
import com.xyrospace.expensetracker.ui.home.HomeScreen
import com.xyrospace.expensetracker.ui.home.HomeViewModel
import com.xyrospace.expensetracker.ui.transactions.TransactionDetailScreen
import com.xyrospace.expensetracker.ui.transactions.TransactionFormScreen
import com.xyrospace.expensetracker.ui.transactions.TransactionHistoryScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val ADD = "add"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    const val TRANSACTION_DETAIL = "transaction/{transactionId}"
    const val TRANSACTION_EDIT = "transaction/{transactionId}/edit"

    fun transactionDetail(id: Long) = "transaction/$id"
    fun transactionEdit(id: Long) = "transaction/$id/edit"
}

private data class AppDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val destinations = listOf(
    AppDestination(Routes.HOME, "Home", Icons.Filled.Home),
    AppDestination(Routes.HISTORY, "History", Icons.Filled.History),
    AppDestination(Routes.ADD, "Add", Icons.Filled.Add),
    AppDestination(Routes.REPORTS, "Reports", Icons.Filled.PieChart),
    AppDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun ExpenseTrackerApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val isTransactionRoute = currentRoute?.startsWith("transaction/") == true
    val homeViewModel: HomeViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == destination.route
                    } == true || (isTransactionRoute && destination.route == Routes.HISTORY)
                    NavigationBarItem(
                        selected = selected,
                        modifier = Modifier.testTag("tab_${destination.route}"),
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onViewAll = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
                    viewModel = homeViewModel,
                )
            }
            composable(Routes.HISTORY) {
                TransactionHistoryScreen(
                    onTransactionClick = { id ->
                        navController.navigate(Routes.transactionDetail(id))
                    },
                )
            }
            composable(Routes.ADD) {
                val navigateToHistory = {
                    navController.navigate(Routes.HISTORY) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                TransactionFormScreen(
                    onNavigateBack = navigateToHistory,
                    onSaved = {
                        scope.launch { snackbarHostState.showSnackbar("Transaction saved") }
                        navigateToHistory()
                    },
                )
            }
            composable(
                route = Routes.TRANSACTION_DETAIL,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
            ) {
                TransactionDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Routes.transactionEdit(id)) },
                    onDeleted = {
                        scope.launch { snackbarHostState.showSnackbar("Transaction deleted") }
                        navController.returnToHistory()
                    },
                )
            }
            composable(
                route = Routes.TRANSACTION_EDIT,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
            ) {
                TransactionFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = {
                        scope.launch { snackbarHostState.showSnackbar("Changes saved") }
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.REPORTS) {
                PlaceholderScreen(title = "Reports", testTag = "screen_reports")
            }
            composable(Routes.SETTINGS) {
                PlaceholderScreen(title = "Settings", testTag = "screen_settings")
            }
        }
    }
}

private fun NavHostController.returnToHistory() {
    if (!popBackStack(Routes.HISTORY, inclusive = false)) {
        navigate(Routes.HISTORY) { launchSingleTop = true }
    }
}

@Composable
private fun PlaceholderScreen(title: String, testTag: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(testTag),
        )
    }
}
