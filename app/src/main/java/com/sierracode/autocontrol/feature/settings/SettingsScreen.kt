package com.sierracode.autocontrol.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.BuildConfig
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.data.local.seed.CategorySeed
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.preferences.UnitSystem
import com.sierracode.autocontrol.data.repository.CurrencyRepository
import com.sierracode.autocontrol.data.repository.VehicleRepository
import com.sierracode.autocontrol.data.backup.BackupManager
import com.sierracode.autocontrol.core.util.NumberParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.math.BigDecimal
import java.math.RoundingMode

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val vehicleRepo: VehicleRepository,
    private val currencyRepo: CurrencyRepository,
    private val backupManager: com.sierracode.autocontrol.data.backup.BackupManager,
    private val db: AutoControlDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val darkMode = settings.darkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isPro = settings.isPro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val dynamicColor = settings.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()

    fun setTheme(mode: Boolean?) = viewModelScope.launch { settings.setDark(mode) }

    fun setDynamicColor(v: Boolean) = viewModelScope.launch { settings.setDynamicColor(v) }

    fun setPro(value: Boolean) = viewModelScope.launch { settings.setPro(value) }

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

    private val _isConverting = MutableStateFlow(false)
    val isConverting = _isConverting.asStateFlow()

    fun convertCurrency(newCurrency: String, rate: Double, onDone: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        val activeId = settings.activeVehicleId.first()
        if (activeId == null) {
            settings.setCurrency(newCurrency)
            onDone()
            return@launch
        }
        _isConverting.value = true
        try {
            // 1. Crear respaldo interno de seguridad
            val backupResult = backupManager.createInternalBackup()
            if (backupResult.isFailure) {
                onError(context.getString(R.string.settings_convert_backup_error))
                return@launch
            }

            // 2. Calcular multiplicador con BigDecimal
            val current = currentCurrency.value
            val rateBigDecimal = BigDecimal(rate.toString())
            val multiplier = when {
                newCurrency == "MXN" && current != "MXN" -> rateBigDecimal
                current == "MXN" && newCurrency != "MXN" -> BigDecimal.ONE.divide(rateBigDecimal, 10, RoundingMode.HALF_UP)
                else -> BigDecimal.ONE.divide(rateBigDecimal, 10, RoundingMode.HALF_UP)
            }

            // 3. Convertir de forma atómica
            currencyRepo.convertAll(activeId, newCurrency, multiplier)
            settings.setCurrency(newCurrency)
            onDone()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            onError(context.getString(R.string.settings_convert_error, e.message ?: ""))
        } finally {
            _isConverting.value = false
        }
    }

    fun deleteAllData(onSuccess: () -> Unit, onError: (String) -> Unit) =
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                withContext(Dispatchers.IO) {
                    db.clearAllTables()
                    db.categoryDao().upsertAll(CategorySeed.all())
                    context.filesDir.listFiles()?.forEach { file ->
                        if (file.name.startsWith("img_") || file.name.startsWith("doc_")) {
                            file.delete()
                        }
                    }
                }
                settings.setActiveVehicle(null)
                onSuccess()
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error al borrar todos los datos", e)
                val uiError = com.sierracode.autocontrol.core.ui.UiError.from(e)
                onError(uiError.asString(context))
            } finally {
                _isDeleting.value = false
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val darkMode by vm.darkMode.collectAsStateWithLifecycle()
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
    val currency by vm.currentCurrency.collectAsStateWithLifecycle()
    val units by vm.currentUnitSystem.collectAsStateWithLifecycle()
    val isDeleting by vm.isDeleting.collectAsStateWithLifecycle()
    val isConverting by vm.isConverting.collectAsStateWithLifecycle()


    var showCurrencyMenu by remember { mutableStateOf(false) }
    var showConversionDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var targetCurrency by remember { mutableStateOf("") }
    var rateInput by remember { mutableStateOf("") }
    var ratePrompt by remember { mutableStateOf("") }
    var rateError by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val successConvertText = stringResource(R.string.settings_convert_success, targetCurrency)
    val noConvertSuccessText = stringResource(R.string.settings_convert_no_convert_success, targetCurrency)
    val deleteAllSuccessText = stringResource(R.string.settings_delete_all_success)
    val deleteAllErrorText = stringResource(R.string.settings_delete_all_error)
    val proActivatedText = stringResource(R.string.settings_pro_activated)
    val proDeactivatedText = stringResource(R.string.settings_pro_deactivated)

    val currencyOptions = listOf(
        "MXN" to stringResource(R.string.currency_mxn),
        "USD" to stringResource(R.string.currency_usd),
        "EUR" to stringResource(R.string.currency_eur),
        "CAD" to stringResource(R.string.currency_cad),
        "GBP" to stringResource(R.string.currency_gbp)
    )

    if (showConversionDialog) {
        AlertDialog(
            onDismissRequest = { showConversionDialog = false },
            title = { Text(stringResource(R.string.settings_convert_title, targetCurrency)) },
            text = {
                Column {
                    Text(ratePrompt)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rateInput,
                        onValueChange = { rateInput = it; rateError = false },
                        label = { Text(stringResource(R.string.settings_convert_rate)) },
                        isError = rateError,
                        supportingText = if (rateError) {
                            { Text(stringResource(R.string.settings_convert_rate_invalid)) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_convert_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showConversionDialog = false
                            vm.convertCurrency(
                                newCurrency = targetCurrency,
                                rate = 1.0,
                                onDone = { scope.launch { snackbarHostState.showSnackbar(noConvertSuccessText) } },
                                onError = { errorMsg -> scope.launch { snackbarHostState.showSnackbar(errorMsg) } }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isConverting
                    ) {
                        Text(stringResource(R.string.settings_convert_no_convert))
                    }
                }
            },
            confirmButton = {
                if (isConverting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp))
                } else {
                    TextButton(
                        onClick = {
                            val rate = com.sierracode.autocontrol.core.util.NumberParser.parse(rateInput)
                            if (rate != null && rate > 0) {
                                rateError = false
                                vm.convertCurrency(
                                    newCurrency = targetCurrency,
                                    rate = rate,
                                    onDone = {
                                        showConversionDialog = false
                                        scope.launch { snackbarHostState.showSnackbar(successConvertText) }
                                    },
                                    onError = { errorMsg ->
                                        showConversionDialog = false
                                        scope.launch { snackbarHostState.showSnackbar(errorMsg) }
                                    }
                                )
                            } else {
                                rateError = true
                            }
                        }
                    ) { Text(stringResource(R.string.action_ok)) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { rateError = false; showConversionDialog = false },
                    enabled = !isConverting
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteAllDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.settings_delete_all_title)) },
            text = { Text(stringResource(R.string.settings_delete_all_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAllData(
                            onSuccess = {
                                showDeleteAllDialog = false
                                scope.launch { snackbarHostState.showSnackbar(deleteAllSuccessText) }
                            },
                            onError = {
                                showDeleteAllDialog = false
                                scope.launch { snackbarHostState.showSnackbar(deleteAllErrorText) }
                            }
                        )
                    },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            stringResource(R.string.settings_delete_all_confirm),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isDeleting) showDeleteAllDialog = false },
                    enabled = !isDeleting
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            // APARIENCIA
            Text(
                stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
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

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    // Toggle Material You
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_dynamic_color),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.settings_dynamic_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { vm.setDynamicColor(it) }
                        )
                    }
                }
            }

            // REGIÓN Y UNIDADES
            Text(stringResource(R.string.settings_region_units), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.settings_region_desc),
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
                            label = { Text(stringResource(R.string.settings_currency)) },
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
                                            rateError = false
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

                    Text(stringResource(R.string.settings_units), style = MaterialTheme.typography.bodyLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeChip(
                            icon = Icons.Default.Info,
                            label = stringResource(R.string.settings_metric),
                            selected = units == UnitSystem.METRIC,
                            onClick = { vm.setUnitSystem(UnitSystem.METRIC) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeChip(
                            icon = Icons.Default.Info,
                            label = stringResource(R.string.settings_imperial),
                            selected = units == UnitSystem.US,
                            onClick = { vm.setUnitSystem(UnitSystem.US) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // SUSCRIPCIÓN
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
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.settings_demo_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (isPro) {
                        OutlinedButton(
                            onClick = {
                                vm.setPro(false)
                                scope.launch { snackbarHostState.showSnackbar(proDeactivatedText) }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.settings_pro_deactivate))
                        }
                    } else {
                        Button(
                            onClick = {
                                vm.setPro(true)
                                scope.launch { snackbarHostState.showSnackbar(proActivatedText) }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_pro_activate))
                        }
                    }
                }
            }

            // INFORMACIÓN
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

            // DATOS
            Text(
                stringResource(R.string.settings_data),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.settings_data_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showDeleteAllDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_delete_all))
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
            Spacer(modifier = Modifier.height(4.dp))
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