package com.sierracode.autocontrol.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sierracode.autocontrol.feature.dashboard.DashboardScreen
import com.sierracode.autocontrol.feature.documents.DocumentsScreen
import com.sierracode.autocontrol.feature.expenses.ExpensesScreen
import com.sierracode.autocontrol.feature.goals.GoalsScreen
import com.sierracode.autocontrol.feature.stats.StatsScreen

sealed class BottomNavItem(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String) {
    data object Dashboard : BottomNavItem("dashboard/{vehicleId}", Icons.Default.Home, "Inicio")
    data object Expenses : BottomNavItem("expenses/{vehicleId}", Icons.Default.Receipt, "Gastos")
    data object Goals : BottomNavItem("goals/{vehicleId}", Icons.Default.Flag, "Objetivos")
    data object Stats : BottomNavItem("stats/{vehicleId}", Icons.Default.BarChart, "Stats")
    data object Documents : BottomNavItem("documents/{vehicleId}", Icons.Default.Folder, "Docs")
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
    onOpenReminders: () -> Unit,
    onAddDocument: () -> Unit,
    onAddGoal: () -> Unit,
    onGoalClick: (String) -> Unit
) {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Expenses,
        BottomNavItem.Goals,
        BottomNavItem.Stats,
        BottomNavItem.Documents
    )

    // Lógica del botón Atrás para las pestañas inferiores
    BackHandler(enabled = true) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val dashboardRoute = BottomNavItem.Dashboard.route.replace("{vehicleId}", vehicleId)

        if (currentRoute == dashboardRoute) {
            // Si está en el Dashboard, sale al listado de vehículos
            onBackToVehicles()
        } else {
            // Si está en otra pestaña, regresa al Dashboard
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
                    onBack = onBackToVehicles,
                    onRegisterExpense = onRegisterExpense,
                    onRegisterFuel = onRegisterFuel,
                    onRegisterMaintenance = onRegisterMaintenance,
                    onOpenReminders = onOpenReminders
                )
            }
            composable(BottomNavItem.Expenses.route) { backStackEntry ->
                val vId = backStackEntry.arguments?.getString("vehicleId") ?: vehicleId
                ExpensesScreen(
                    vehicleId = vId,
                    onBack = { /* No hace falta */ },
                    onEditClick = onEditExpense
                )
            }
            composable(BottomNavItem.Goals.route) { backStackEntry ->
                val vId = backStackEntry.arguments?.getString("vehicleId") ?: vehicleId
                GoalsScreen(
                    vehicleId = vId,
                    onBack = { /* No hace falta */ },
                    onAddClick = onAddGoal,
                    onGoalClick = onGoalClick
                )
            }
            composable(BottomNavItem.Stats.route) { backStackEntry ->
                val vId = backStackEntry.arguments?.getString("vehicleId") ?: vehicleId
                StatsScreen(
                    vehicleId = vId,
                    onBack = { /* No hace falta */ }
                )
            }
            composable(BottomNavItem.Documents.route) { backStackEntry ->
                val vId = backStackEntry.arguments?.getString("vehicleId") ?: vehicleId
                DocumentsScreen(
                    vehicleId = vId,
                    onBack = { /* No hace falta */ },
                    onAddClick = onAddDocument,
                    onEditClick = onEditDocument
                )
            }
        }
    }
}