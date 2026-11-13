package com.sierracode.autocontrol.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.BuildConfig
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.preferences.UnitSystem
import com.sierracode.autocontrol.data.repository.CurrencyRepository
import com.sierracode.autocontrol.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val vehicleRepo: VehicleRepository,
    private val currencyRepo: CurrencyRepository
) : ViewModel() {
    val darkMode = settings.darkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isPro = settings.isPro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val activeVehicle = settings.activeVehicleId
        .combine(vehicleRepo.observeAll()) { id, vehicles -> vehicles.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentCurrency = activeVehicle.combine(settings.currencyCode) { vehicle, globalCurr ->
        vehicle?.currencyCode ?: globalCurr
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "MXN")

    val currentUnitSystem = activeVehicle.combine(settings.unitSystem) { vehicle, globalUnit ->
        if (vehicle != null) {
            if (vehicle.unitSystem == "METRIC") UnitSystem.METRIC else UnitSystem.US
        } else {
            globalUnit
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitSystem.METRIC)

    fun setTheme(mode: Boolean?) = viewModelScope.launch { settings.setDark(mode) }

    fun setUnitSystem(system: UnitSystem) = viewModelScope.launch {
        settings.setUnitSystem(system)
        val activeId = settings.activeVehicleId.first()
        if (activeId != null) {
            vehicleRepo.get(activeId)?.let { v ->
                val newUnit = if (system == UnitSystem.METRIC) "METRIC" else "US"
                vehicleRepo.upsert(v.copy(unitSystem = newUnit))
            }
        }
    }

    fun convertCurrency(newCurrency: String, inputRate: Double, onDone: () -> Unit) = viewModelScope.launch {
        settings.setCurrency(newCurrency)
        val activeId = settings.activeVehicleId.first()
        if (activeId != null) {
            val current = currentCurrency.value
            val multiplier = when {
                newCurrency == "MXN" && current != "MXN" -> inputRate
                current == "MXN" && newCurrency != "MXN" -> 1.0 / inputRate
                else -> 1.0 / inputRate
            }
            currencyRepo.convertAll(activeId, newCurrency, multiplier)
        }
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val darkMode by vm.darkMode.collectAsState()
    val isPro by vm.isPro.collectAsState()
    val currency by vm.currentCurrency.collectAsState()
    val units by vm.currentUnitSystem.collectAsState()

    var showCurrencyMenu by remember { mutableStateOf(false) }

    var showConversionDialog by remember { mutableStateOf(false) }
    var targetCurrency by remember { mutableStateOf("") }
    var rateInput by remember { mutableStateOf("") }
    var ratePrompt by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // SE9: mapa de divisas con nombres
    val currencyOptions = remember {
        listOf(
            "MXN" to "Peso mexicano",
            "USD" to "Dólar estadounidense",
            "EUR" to "Euro",
            "CAD" to "Dólar canadiense",
            "GBP" to "Libra esterlina"
        )
    }

    if (showConversionDialog) {
        AlertDialog(
            onDismissRequest = { showConversionDialog = false },
            title = { Text("Cambiar divisa a $targetCurrency") },
            text = {
                Column {
                    Text(ratePrompt)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rateInput,
                        onValueChange = { rateInput = it },
                        label = { Text("Tasa de cambio") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Todos los gastos históricos de este vehículo se convertirán a la nueva moneda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showConversionDialog = false
                            vm.convertCurrency(targetCurrency, 1.0) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Divisa cambiada a $targetCurrency (montos sin convertir)"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("No convertir, solo cambiar etiqueta")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val rate = rateInput.toDoubleOrNull()
                        if (rate != null && rate > 0) {
                            showConversionDialog = false
                            vm.convertCurrency(targetCurrency, rate) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Divisa convertida a $targetCurrency"
                                    )
                                }
                            }
                        }
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showConversionDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- APARIENCIA ---
            Text(
                stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeChip(
                            icon = Icons.Default.DarkMode,
                            label = stringResource(R.string.settings_theme_system),
                            selected = darkMode == null,
                            onClick = { vm.setTheme(null) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeChip(
                            icon = Icons.Default.LightMode,
                            label = stringResource(R.string.settings_theme_light),
                            selected = darkMode == false,
                            onClick = { vm.setTheme(false) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeChip(
                            icon = Icons.Default.DarkMode,
                            label = stringResource(R.string.settings_theme_dark),
                            selected = darkMode == true,
                            onClick = { vm.setTheme(true) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // --- REGIÓN Y UNIDADES ---
            Text("Región y Unidades", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Text(
                text = "Valores predeterminados para autos nuevos. Si hay un auto activo, se aplicará a él.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, bottom = 4.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = showCurrencyMenu,
                        onExpandedChange = { showCurrencyMenu = !showCurrencyMenu }
                    ) {
                        val currentOption = currencyOptions.find { it.first == currency }
                        OutlinedTextField(
                            value = currentOption?.let { "${it.first} · ${it.second}" } ?: currency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Divisa") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencyMenu)
                            },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = showCurrencyMenu,
                            onDismissRequest = { showCurrencyMenu = false }
                        ) {
                            currencyOptions.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text("$code · $name") },
                                    onClick = {
                                        showCurrencyMenu = false
                                        if (code != currency) {
                                            targetCurrency = code
                                            if (code == "MXN") {
                                                rateInput = if (currency == "USD") "17.50" else "18.50"
                                                ratePrompt = "1 $currency = ? MXN"
                                            } else if (currency == "MXN") {
                                                rateInput = if (code == "USD") "17.50" else "18.50"
                                                ratePrompt = "1 $code = ? MXN"
                                            } else {
                                                rateInput = "1.0"
                                                ratePrompt = "1 $code = ? $currency"
                                            }
                                            showConversionDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Text("Sistema de unidades", style = MaterialTheme.typography.bodyLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
            Text(
                stringResource(R.string.settings_subscription),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = if (isPro) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(if (isPro) R.string.settings_pro_title else R.string.settings_free_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(if (isPro) R.string.settings_pro_desc else R.string.settings_free_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- INFORMACIÓN ---
            Text(
                stringResource(R.string.settings_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.settings_privacy_title), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.settings_privacy_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.settings_dev),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // SE6: Versión dinámica
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}