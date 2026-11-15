package com.sierracode.autocontrol.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.data.repository.VehicleRepository
import com.sierracode.autocontrol.feature.dashboard.DashboardScreen
import com.sierracode.autocontrol.feature.documents.DocumentDetailScreen
import com.sierracode.autocontrol.feature.documents.DocumentEditScreen
import com.sierracode.autocontrol.feature.documents.DocumentsScreen
import com.sierracode.autocontrol.feature.expenses.ExpenseDetailScreen
import com.sierracode.autocontrol.feature.expenses.ExpenseEditScreen
import com.sierracode.autocontrol.feature.expenses.ExpensesScreen
import com.sierracode.autocontrol.feature.fuel.FuelEditScreen
import com.sierracode.autocontrol.feature.goals.GoalDetailScreen
import com.sierracode.autocontrol.feature.goals.GoalEditScreen
import com.sierracode.autocontrol.feature.goals.GoalsScreen
import com.sierracode.autocontrol.feature.maintenance.MaintenanceEditScreen
import com.sierracode.autocontrol.feature.reminders.RemindersScreen
import com.sierracode.autocontrol.feature.stats.StatsScreen
import com.sierracode.autocontrol.navigation.NavIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class BottomNavItem(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val labelResId: Int) {
    data object Dashboard : BottomNavItem("dashboard", Icons.Default.Home, R.string.nav_dashboard)
    data object Expenses : BottomNavItem("expenses", Icons.Default.Receipt, R.string.nav_expenses)
    data object Goals : BottomNavItem("goals", Icons.Default.Flag, R.string.nav_goals)
    data object Stats : BottomNavItem("stats", Icons.Default.BarChart, R.string.nav_stats)
    data object Documents : BottomNavItem("documents", Icons.Default.Folder, R.string.nav_documents)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow("")

    val vehicleName = vehicleIdFlow.flatMapLatest { id ->
        if (id.isBlank()) flowOf("") else vehicleRepo.observe(id).map { v ->
            v?.let { "${it.brand} ${it.model}" } ?: ""
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun loadVehicle(id: String) { vehicleIdFlow.value = id }
}

@Composable
fun MainScreen(
    vehicleId: String,
    navIntent: NavIntent? = null,
    onIntentConsumed: () -> Unit = {},
    onBackToVehicles: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditVehicle: () -> Unit,
    onSwitchVehicle: (String) -> Unit
) {
    val vm: MainViewModel = hiltViewModel()
    LaunchedEffect(vehicleId) { vm.loadVehicle(vehicleId) }
    val vehicleName by vm.vehicleName.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val tabs = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Expenses,
        BottomNavItem.Goals,
        BottomNavItem.Stats,
        BottomNavItem.Documents
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val showBottomBar = tabs.any { it.route == currentRoute }

    BackHandler(enabled = currentRoute == BottomNavItem.Dashboard.route) {
        onBackToVehicles()
    }

    LaunchedEffect(navIntent, vehicleId) {
        if (navIntent != null && navIntent.vehicleId == vehicleId) {
            val target = when {
                navIntent.action == "new_expense" -> "expense_edit/$vehicleId?expenseId=&goalId="
                navIntent.action == "new_fuel" -> "fuel_edit/$vehicleId"
                navIntent.action == "new_maintenance" -> "maint_edit/$vehicleId"
                navIntent.sourceType == "DOCUMENT" -> "document_detail/${navIntent.sourceId}"
                navIntent.sourceType == "MAINTENANCE" -> "reminders"
                else -> null
            }
            if (target != null) {
                navController.navigate(target)
            }
            onIntentConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = stringResource(id = item.labelResId)) },
                            label = { Text(stringResource(id = item.labelResId)) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Dashboard.route) {
                DashboardScreen(
                    vehicleId = vehicleId,
                    onBack = onBackToVehicles,
                    onEditVehicle = onEditVehicle,
                    onSwitchVehicle = { newId -> onSwitchVehicle(newId) },
                    onRegisterExpense = { navController.navigate("expense_edit/$vehicleId?expenseId=&goalId=") },
                    onRegisterFuel = { navController.navigate("fuel_edit/$vehicleId") },
                    onRegisterMaintenance = { navController.navigate("maint_edit/$vehicleId") },
                    onOpenReminders = { navController.navigate("reminders") },
                    onOpenSettings = onOpenSettings
                )
            }
            composable(BottomNavItem.Expenses.route) {
                ExpensesScreen(
                    vehicleId = vehicleId,
                    vehicleName = vehicleName,
                    onDetailClick = { id -> navController.navigate("expense_detail/$id") },
                    onAddClick = { navController.navigate("expense_edit/$vehicleId?expenseId=&goalId=") }
                )
            }
            composable(BottomNavItem.Goals.route) {
                GoalsScreen(
                    vehicleId = vehicleId,
                    vehicleName = vehicleName,
                    onAddClick = { navController.navigate("goal_edit/$vehicleId?goalId=") },
                    onGoalClick = { id -> navController.navigate("goal_detail/$id") }
                )
            }
            composable(BottomNavItem.Stats.route) {
                StatsScreen(
                    vehicleId = vehicleId,
                    vehicleName = vehicleName,
                    onOpenExpenses = {
                        navController.navigate(BottomNavItem.Expenses.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(BottomNavItem.Documents.route) {
                DocumentsScreen(
                    vehicleId = vehicleId,
                    vehicleName = vehicleName,
                    onAddClick = { navController.navigate("document_edit/$vehicleId?documentId=") },
                    onEditClick = { id -> navController.navigate("document_edit/$vehicleId?documentId=$id") },
                    onDocumentClick = { id -> navController.navigate("document_detail/$id") }
                )
            }

            // --- Detalle de gasto ---
            composable(
                "expense_detail/{expenseId}",
                arguments = listOf(navArgument("expenseId") { type = NavType.StringType })
            ) { back ->
                val eId = back.arguments!!.getString("expenseId")!!
                ExpenseDetailScreen(
                    expenseId = eId,
                    vehicleId = vehicleId,
                    onBack = { navController.popBackStack() },
                    onEditClick = { navController.navigate("expense_edit/$vehicleId?expenseId=$eId&goalId=") },
                    onOpenSettings = onOpenSettings
                )
            }

            composable(
                "expense_edit/{vehicleId}?expenseId={expenseId}&goalId={goalId}",
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.StringType },
                    navArgument("expenseId") { defaultValue = "" },
                    navArgument("goalId") { defaultValue = "" }
                )
            ) { back ->
                val vId = back.arguments?.getString("vehicleId") ?: vehicleId
                val eId = back.arguments?.getString("expenseId")?.takeIf { it.isNotBlank() }
                val gId = back.arguments?.getString("goalId")?.takeIf { it.isNotBlank() }
                ExpenseEditScreen(
                    vehicleId = vId,
                    expenseId = eId,
                    goalId = gId,
                    onDone = { navController.popBackStack() },
                    onOpenSettings = onOpenSettings
                )
            }
            composable(
                "fuel_edit/{vehicleId}",
                arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })
            ) { back ->
                val vId = back.arguments?.getString("vehicleId") ?: vehicleId
                FuelEditScreen(vehicleId = vId, onDone = { navController.popBackStack() })
            }
            composable(
                "maint_edit/{vehicleId}",
                arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })
            ) { back ->
                val vId = back.arguments?.getString("vehicleId") ?: vehicleId
                MaintenanceEditScreen(vehicleId = vId, onDone = { navController.popBackStack() })
            }
            composable("reminders") {
                RemindersScreen(vehicleId = vehicleId, onBack = { navController.popBackStack() })
            }
            composable(
                "document_edit/{vehicleId}?documentId={documentId}",
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.StringType },
                    navArgument("documentId") { defaultValue = "" }
                )
            ) {
                DocumentEditScreen(onDone = { navController.popBackStack() })
            }
            composable("document_detail/{documentId}") {
                DocumentDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEditClick = { id -> navController.navigate("document_edit/$vehicleId?documentId=$id") }
                )
            }
            composable(
                "goal_edit/{vehicleId}?goalId={goalId}",
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.StringType },
                    navArgument("goalId") { defaultValue = "" }
                )
            ) {
                GoalEditScreen(onDone = { navController.popBackStack() })
            }
            composable("goal_detail/{goalId}") { back ->
                val gId = back.arguments!!.getString("goalId")!!
                GoalDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEditClick = { navController.navigate("goal_edit/$vehicleId?goalId=$gId") },
                    onBuyClick = { navController.navigate("expense_edit/$vehicleId?expenseId=&goalId=$gId") }
                )
            }
        }
    }
}