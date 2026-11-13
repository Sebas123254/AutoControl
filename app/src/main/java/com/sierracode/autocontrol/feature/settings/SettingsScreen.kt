package com.sierracode.autocontrol.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.data.preferences.EfficiencyFormat
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.preferences.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore
) : ViewModel() {
    val darkMode = settings.darkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isPro = settings.isPro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val currencyCode = settings.currencyCode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "MXN")
    val unitSystem = settings.unitSystem.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitSystem.METRIC)

    fun setTheme(mode: Boolean?) = viewModelScope.launch { settings.setDark(mode) }
    fun setCurrency(code: String) = viewModelScope.launch { settings.setCurrency(code) }
    fun setUnitSystem(system: UnitSystem) = viewModelScope.launch { settings.setUnitSystem(system) }
    fun setPro(isPro: Boolean) = viewModelScope.launch { settings.setPro(isPro) } // <-- NUEVO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val darkMode by vm.darkMode.collectAsState()
    val isPro by vm.isPro.collectAsState()
    val currency by vm.currencyCode.collectAsState()
    val units by vm.unitSystem.collectAsState()

    var showCurrencyMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- APARIENCIA ---
            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip(icon = Icons.Default.DarkMode, label = stringResource(R.string.settings_theme_system), selected = darkMode == null, onClick = { vm.setTheme(null) }, modifier = Modifier.weight(1f))
                        ThemeChip(icon = Icons.Default.LightMode, label = stringResource(R.string.settings_theme_light), selected = darkMode == false, onClick = { vm.setTheme(false) }, modifier = Modifier.weight(1f))
                        ThemeChip(icon = Icons.Default.DarkMode, label = stringResource(R.string.settings_theme_dark), selected = darkMode == true, onClick = { vm.setTheme(true) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            // --- REGIÓN Y UNIDADES ---
            Text("Región y Unidades", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExposedDropdownMenuBox(expanded = showCurrencyMenu, onExpandedChange = { showCurrencyMenu = !showCurrencyMenu }) {
                        OutlinedTextField(
                            value = currency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Divisa") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencyMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = showCurrencyMenu, onDismissRequest = { showCurrencyMenu = false }) {
                            listOf("MXN", "USD", "EUR", "CAD", "GBP").forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(code) },
                                    onClick = { vm.setCurrency(code); showCurrencyMenu = false }
                                )
                            }
                        }
                    }

                    Text("Sistema de unidades", style = MaterialTheme.typography.bodyLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip(
                            icon = Icons.Default.Info,
                            label = "Métrico (km/L)",
                            selected = units == UnitSystem.METRIC,
                            onClick = { vm.setUnitSystem(UnitSystem.METRIC) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeChip(
                            icon = Icons.Default.Info,
                            label = "Imperial (mi/gal)",
                            selected = units == UnitSystem.US,
                            onClick = { vm.setUnitSystem(UnitSystem.US) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // --- SUSCRIPCIÓN ---
            Text(stringResource(R.string.settings_subscription), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(32.dp), tint = if (isPro) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(if (isPro) R.string.settings_pro_title else R.string.settings_free_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(if (isPro) R.string.settings_pro_desc else R.string.settings_free_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // NUEVO BOTÓN DE SIMULACIÓN
                    Button(
                        onClick = { vm.setPro(!isPro) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPro) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isPro) "Volver a Free (Simulación)" else "Simular Compra PRO")
                    }
                }
            }

            // --- INFORMACIÓN ---
            Text(stringResource(R.string.settings_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.settings_privacy_title), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.settings_privacy_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_dev), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_version, "0.1.0"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ThemeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}