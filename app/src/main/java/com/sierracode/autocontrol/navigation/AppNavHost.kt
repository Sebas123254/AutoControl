package com.sierracode.autocontrol.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.feature.main.MainScreen
import com.sierracode.autocontrol.feature.onboarding.OnboardingScreen
import com.sierracode.autocontrol.feature.settings.SettingsScreen
import com.sierracode.autocontrol.feature.vehicles.VehicleEditScreen
import com.sierracode.autocontrol.feature.vehicles.VehiclesScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

data class NavIntent(
    val vehicleId: String,
    val sourceType: String,
    val sourceId: String
)

sealed class Route(val path: String) {
    data object Onboarding : Route("onboarding")
    data object Vehicles : Route("vehicles")
    data object Settings : Route("settings")
    data object VehicleEdit : Route("vehicle_edit?vehicleId={vehicleId}") {
        fun build(vehicleId: String?) = "vehicle_edit?vehicleId=${vehicleId ?: ""}"
    }
    data object Main : Route("main/{vehicleId}") {
        fun build(id: String) = "main/$id"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntry {
    fun settings(): SettingsStore
}

@Composable
fun AppNavHost(
    nav: NavHostController,
    navIntent: NavIntent? = null,
    onIntentConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember {
        EntryPointAccessors.fromApplication(context, SettingsEntry::class.java).settings()
    }

    val onboarded by settings.onboarded.collectAsState(initial = null)

    var minTimePassed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        minTimePassed = true
    }

    if (onboarded == null || !minTimePassed) {
        SplashScreen()
    } else {
        LaunchedEffect(navIntent, onboarded, minTimePassed) {
            if (navIntent != null && onboarded == true && minTimePassed) {
                nav.navigate(Route.Main.build(navIntent.vehicleId)) {
                    popUpTo(Route.Vehicles.path) { inclusive = false }
                }
                // Enrutamiento de notificaciones se maneja dentro de MainScreen en este lote
                onIntentConsumed()
            }
        }

        NavHost(
            navController = nav,
            startDestination = if (onboarded == true) Route.Vehicles.path else Route.Onboarding.path
        ) {
            composable(Route.Onboarding.path) {
                OnboardingScreen(onDone = { nav.navigate(Route.Vehicles.path) { popUpTo(0) } })
            }
            composable(Route.Vehicles.path) {
                VehiclesScreen(
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
                LaunchedEffect(id) { settings.setActiveVehicle(id) }
                MainScreen(
                    vehicleId = id,
                    onBackToVehicles = { nav.popBackStack() },
                    onOpenSettings = { nav.navigate(Route.Settings.path) },
                    onEditVehicle = { nav.navigate(Route.VehicleEdit.build(id)) }
                )
            }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.autocontrol_ico),
                contentDescription = "AutoControl Logo",
                modifier = Modifier.size(150.dp).clip(RoundedCornerShape(32.dp))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "AutoControl", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Tu auto. Tus gastos. Bajo control.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}