package com.sierracode.autocontrol.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.feature.documents.DocumentEditScreen
import com.sierracode.autocontrol.feature.expenses.ExpenseEditScreen
import com.sierracode.autocontrol.feature.fuel.FuelEditScreen
import com.sierracode.autocontrol.feature.goals.GoalDetailScreen
import com.sierracode.autocontrol.feature.goals.GoalEditScreen
import com.sierracode.autocontrol.feature.main.MainScreen
import com.sierracode.autocontrol.feature.maintenance.MaintenanceEditScreen
import com.sierracode.autocontrol.feature.onboarding.OnboardingScreen
import com.sierracode.autocontrol.feature.settings.SettingsScreen
import com.sierracode.autocontrol.feature.vehicles.VehicleEditScreen
import com.sierracode.autocontrol.feature.vehicles.VehiclesScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import com.sierracode.autocontrol.feature.reminders.RemindersScreen

sealed class Route(val path: String) {
    data object Onboarding : Route("onboarding")
    data object Vehicles : Route("vehicles")
    data object Settings : Route("settings")

    data object Reminders : Route("reminders/{vehicleId}") {
        fun build(id: String) = "reminders/$id"
    }
    data object VehicleEdit : Route("vehicle_edit?vehicleId={vehicleId}") {
        fun build(vehicleId: String?) = "vehicle_edit?vehicleId=${vehicleId ?: ""}"
    }

    data object Main : Route("main/{vehicleId}") {
        fun build(id: String) = "main/$id"
    }

    data object ExpenseEdit : Route("expense_edit/{vehicleId}?expenseId={expenseId}&goalId={goalId}") {
        fun build(vehicleId: String, expenseId: String? = null, goalId: String? = null) =
            "expense_edit/$vehicleId?expenseId=${expenseId ?: ""}&goalId=${goalId ?: ""}"
    }

    data object FuelEdit : Route("fuel_edit/{vehicleId}") {
        fun build(id: String) = "fuel_edit/$id"
    }

    data object MaintenanceEdit : Route("maint_edit/{vehicleId}") {
        fun build(id: String) = "maint_edit/$id"
    }

    data object DocumentEdit : Route("document_edit/{vehicleId}?documentId={documentId}") {
        fun build(vehicleId: String, docId: String? = null) = "document_edit/$vehicleId?documentId=${docId ?: ""}"
    }

    data object GoalEdit : Route("goal_edit/{vehicleId}?goalId={goalId}") {
        fun build(vehicleId: String, goalId: String? = null) = "goal_edit/$vehicleId?goalId=${goalId ?: ""}"
    }

    data object GoalDetail : Route("goal_detail/{vehicleId}/{goalId}") {
        fun build(vehicleId: String, goalId: String) = "goal_detail/$vehicleId/$goalId"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntry {
    fun settings(): SettingsStore
}

@Composable
fun AppNavHost(nav: NavHostController) {
    val context = LocalContext.current
    val settings = remember {
        EntryPointAccessors.fromApplication(context, SettingsEntry::class.java).settings()
    }

    val onboarded by settings.onboarded.collectAsState(initial = null)

    var minTimePassed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1500)
        minTimePassed = true
    }

    // NUEVO: Bandera para controlar la redirección automática inicial
    var autoNavigateToLastVehicle by rememberSaveable { mutableStateOf(true) }

    if (onboarded == null || !minTimePassed) {
        SplashScreen()
    } else {
        NavHost(
            navController = nav,
            startDestination = if (onboarded == true) Route.Vehicles.path else Route.Onboarding.path
        ) {
            composable(Route.Onboarding.path) {
                OnboardingScreen(onDone = { nav.navigate(Route.Vehicles.path) { popUpTo(0) } })
            }

            composable(Route.Vehicles.path) {
                VehiclesScreen(
                    autoNavigateToLast = autoNavigateToLastVehicle,
                    onNavigated = { autoNavigateToLastVehicle = false }, // Se apaga tras el primer salto
                    onAddClick = { nav.navigate(Route.VehicleEdit.build(null)) },
                    onVehicleClick = { id -> nav.navigate(Route.Main.build(id)) },
                    onEditClick = { id -> nav.navigate(Route.VehicleEdit.build(id)) },
                    onOpenSettings = { nav.navigate(Route.Settings.path) }
                )
            }

            composable(Route.Settings.path) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }

            composable(Route.VehicleEdit.path) { back ->
                val vehicleId = back.arguments?.getString("vehicleId")?.takeIf { it.isNotBlank() }
                VehicleEditScreen(vehicleId = vehicleId, onDone = { nav.popBackStack() })
            }

            composable(Route.Main.path) { back ->
                val id = back.arguments!!.getString("vehicleId")!!
                MainScreen(
                    vehicleId = id,
                    onBackToVehicles = { nav.popBackStack() },
                    onRegisterExpense = { nav.navigate(Route.ExpenseEdit.build(id)) },
                    onEditExpense = { expenseId -> nav.navigate(Route.ExpenseEdit.build(id, expenseId)) },
                    onRegisterFuel = { nav.navigate(Route.FuelEdit.build(id)) },
                    onRegisterMaintenance = { nav.navigate(Route.MaintenanceEdit.build(id)) },
                    onAddDocument = { nav.navigate(Route.DocumentEdit.build(id)) },
                    onEditDocument = { docId -> nav.navigate(Route.DocumentEdit.build(id, docId)) },
                    onOpenReminders = { nav.navigate(Route.Reminders.build(id)) },
                    onAddGoal = { nav.navigate(Route.GoalEdit.build(id)) },
                    onGoalClick = { goalId -> nav.navigate(Route.GoalDetail.build(id, goalId)) }
                )
            }

            composable(Route.ExpenseEdit.path) { back ->
                val vId = back.arguments!!.getString("vehicleId")!!
                val eId = back.arguments?.getString("expenseId")?.takeIf { it.isNotBlank() }
                val gId = back.arguments?.getString("goalId")?.takeIf { it.isNotBlank() }
                ExpenseEditScreen(vehicleId = vId, expenseId = eId, goalId = gId, onDone = { nav.popBackStack() })
            }

            composable(Route.FuelEdit.path) { back ->
                val vId = back.arguments!!.getString("vehicleId")!!
                FuelEditScreen(vehicleId = vId, onDone = { nav.popBackStack() })
            }

            composable(Route.MaintenanceEdit.path) { back ->
                val vId = back.arguments!!.getString("vehicleId")!!
                MaintenanceEditScreen(vehicleId = vId, onDone = { nav.popBackStack() })
            }

            composable(Route.DocumentEdit.path) { back ->
                val vId = back.arguments!!.getString("vehicleId")!!
                val dId = back.arguments?.getString("documentId")?.takeIf { it.isNotBlank() }
                DocumentEditScreen(onDone = { nav.popBackStack() })
            }

            composable(Route.Reminders.path) { back ->
                val vId = back.arguments!!.getString("vehicleId")!!
                RemindersScreen(vehicleId = vId, onBack = { nav.popBackStack() })
            }

            composable(Route.GoalEdit.path) { back ->
                GoalEditScreen(onDone = { nav.popBackStack() })
            }

            composable(Route.GoalDetail.path) { back ->
                val vId = back.arguments!!.getString("vehicleId")!!
                val gId = back.arguments!!.getString("goalId")!!
                GoalDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEditClick = { nav.navigate(Route.GoalEdit.build(vId, gId)) },
                    onBuyClick = { nav.navigate(Route.ExpenseEdit.build(vId, null, gId)) }
                )
            }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.autocontrol_ico),
                contentDescription = "AutoControl Logo",
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(32.dp))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "AutoControl",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tu auto. Tus gastos. Bajo control.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}