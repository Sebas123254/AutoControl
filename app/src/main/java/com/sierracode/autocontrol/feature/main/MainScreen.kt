package com.sierracode.autocontrol.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sierracode.autocontrol.data.repository.VehicleRepository
import com.sierracode.autocontrol.feature.dashboard.DashboardScreen
import com.sierracode.autocontrol.feature.documents.DocumentsScreen
import com.sierracode.autocontrol.feature.expenses.ExpensesScreen
import com.sierracode.autocontrol.feature.goals.GoalsScreen
import com.sierracode.autocontrol.feature.stats.StatsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class BottomNavItem(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String) {
    data object Dashboard : BottomNavItem("dashboard/{vehicleId}", Icons.Default.Home, "Inicio")
    data object Expenses : BottomNavItem("expenses/{vehicleId}", Icons.Default.Receipt, "Gastos")
    data object Goals : BottomNavItem("goals/{vehicleId}", Icons.Default.Flag, "Objetivos")
    data object Stats : BottomNavItem("stats/{vehicleId}", Icons.Default.BarChart, "Stats")
    data object Documents : BottomNavItem("documents/{vehicleId}", Icons.Default.Folder, "Docs")
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
    onBackToVehicles: () -> Unit,
    onRegisterExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    onRegisterFuel: () -> Unit,
    onRegisterMaintenance: () -> Unit,
    onEditDocument: (String) -> Unit,
    onDocumentClick: (String) -> Unit, // <-- PARÁMETRO AÑADIDO
    onOpenReminders: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddDocument: () -> Unit,
    onAddGoal: () -> Unit,
    onGoalClick: (String) -> Unit,
    onSwitchVehicle: (String) -> Unit,
    onEditVehicle: () -> Unit
) {
    val vm: MainViewModel = hiltViewModel()
    LaunchedEffect(vehicleId) { vm.loadVehicle(vehicleId) }
    val vehicleName by vm.vehicleName.collectAsState()

    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Expenses,
        BottomNavItem.Goals,
        BottomNavItem.Stats,
        BottomNavItem.Documents
    )

    BackHandler(enabled = true) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val dashboardRoute = BottomNavItem.Dashboard.route.replace("{vehicleId}", vehicleId)

        if (currentRoute == dashboardRoute) {
            onBackToVehicles()
        } else {
            navController.navigate(dashboardRoute) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { item ->
                    val actualRoute = item.route.replace("{vehicleId}", vehicleId)

                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(actualRoute) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Dashboard.route.replace("{vehicleId}", vehicleId),
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Dashboard.route) { backStackEntry ->
                val vId = backStackEntry.arguments?.getString("vehicleId") ?: vehicleId
                DashboardScreen(
                    vehicleId = vId,
                    vehicleName = vehicleName,
                    onBack = onBackToVehicles,
                    onRegisterExpense = onRegisterExpense,
                    onRegisterFuel = onRegisterFuel,
                    onRegisterMaintenance = onRegisterMaintenance,
                    onOpenReminders = onOpenReminders,
                    onOpenSettings = onOpenSettings,
                    onSwitchVehicle = onSwitchVehicle,
                    onEditVehicle = onEditVehicle
                )
            }
            composable(BottomNavItem.Expenses.route) { backStackEntry ->
                val vId = backStackEntry.arguments?.getString("vehicleId") ?: vehicleId
                ExpensesScreen(
                    vehicleId = vId,
                    vehicleName = vehicleName,
                    onEditClick = onEditExpense
                )
            }
            composable(BottomNavItem.Goals.route) { backStackEntry ->
                val vId = backStackEntry.arguments?.getString("vehicleId") ?: vehicleId
                GoalsScreen(
                    vehicleId = vId,
                    vehicleName = vehicleName,
                    onAddClick = onAddGoal,
                    onGoalClick = onGoalClick
                )
            }
            composable(BottomNavItem.Stats.route) { backStackEntry ->
                val vId = backStackEntry.arguments?.getString("vehicleId") ?: vehicleId
                StatsScreen(
                    vehicleId = vId,
                    vehicleName = vehicleName,
                    onOpenSettings = onOpenSettings
                )
            }
            composable(BottomNavItem.Documents.route) { backStackEntry ->
                val vId = backStackEntry.arguments?.getString("vehicleId") ?: vehicleId
                DocumentsScreen(
                    vehicleId = vId,
                    vehicleName = vehicleName,
                    onAddClick = onAddDocument,
                    onEditClick = onEditDocument,
                    onDocumentClick = onDocumentClick // <-- PASADO A DOCUMENTS SCREEN
                )
            }
        }
    }
}