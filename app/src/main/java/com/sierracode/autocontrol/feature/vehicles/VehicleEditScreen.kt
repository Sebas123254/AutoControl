package com.sierracode.autocontrol.feature.vehicles

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.ui.components.CurrencyField
import com.sierracode.autocontrol.data.local.entity.VehicleEntity
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.repository.VehicleRepository
import com.sierracode.autocontrol.domain.calc.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class VehicleEditViewModel @Inject constructor(
    private val repo: VehicleRepository,
    private val settingsStore: SettingsStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String? = savedStateHandle.get<String>("vehicleId")?.takeIf { it.isNotBlank() }

    val brand: StateFlow<String> = savedStateHandle.getStateFlow("brand", "")
    val model: StateFlow<String> = savedStateHandle.getStateFlow("model", "")
    val year: StateFlow<String> = savedStateHandle.getStateFlow("year", "")
    val plates: StateFlow<String> = savedStateHandle.getStateFlow("plates", "")
    val odometer: StateFlow<String> = savedStateHandle.getStateFlow("odometer", "")
    val purchasePrice: StateFlow<String> = savedStateHandle.getStateFlow("purchasePrice", "")
    val currencyCode: StateFlow<String> = savedStateHandle.getStateFlow("currencyCode", "MXN")
    val unitSystem: StateFlow<String> = savedStateHandle.getStateFlow("unitSystem", "METRIC")
    val hasUnsavedChanges: StateFlow<Boolean> = savedStateHandle.getStateFlow("hasUnsavedChanges", false)

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        if (!savedStateHandle.contains("loadedFromDb")) {
            if (vehicleId != null) {
                viewModelScope.launch {
                    repo.get(vehicleId)?.let { v ->
                        savedStateHandle["brand"] = v.brand
                        savedStateHandle["model"] = v.model
                        savedStateHandle["year"] = v.year.toString()
                        savedStateHandle["plates"] = v.plates ?: ""
                        savedStateHandle["unitSystem"] = v.unitSystem
                        val odoDisplay = if (v.unitSystem == "METRIC") v.odometer
                        else UnitConverter.kmToMiles(v.odometer).roundToLong()
                        savedStateHandle["odometer"] = odoDisplay.toString()
                        savedStateHandle["currencyCode"] = v.currencyCode
                        savedStateHandle["purchasePrice"] = v.purchasePriceCents?.let { (it / 100.0).toString() } ?: ""
                    }
                    savedStateHandle["loadedFromDb"] = true
                }
            } else {
                viewModelScope.launch {
                    savedStateHandle["currencyCode"] = settingsStore.currencyCode.first()
                    savedStateHandle["unitSystem"] = settingsStore.unitSystem.first().name
                    savedStateHandle["loadedFromDb"] = true
                }
            }
        }
    }

    fun updateBrand(v: String) { savedStateHandle["brand"] = v; markDirty(); error = null }
    fun updateModel(v: String) { savedStateHandle["model"] = v; markDirty(); error = null }
    fun updateYear(v: String) { savedStateHandle["year"] = v; markDirty(); error = null }
    fun updatePlates(v: String) { savedStateHandle["plates"] = v; markDirty() }
    fun updateOdometer(v: String) { savedStateHandle["odometer"] = v; markDirty(); error = null }
    fun updatePurchasePrice(v: String) { savedStateHandle["purchasePrice"] = v; markDirty(); error = null }
    fun updateCurrency(v: String) { savedStateHandle["currencyCode"] = v; markDirty() }
    fun updateUnits(v: String) { savedStateHandle["unitSystem"] = v; markDirty() }
    private fun markDirty() { savedStateHandle["hasUnsavedChanges"] = true }

    fun save(context: android.content.Context, onDone: () -> Unit) {
        val currentBrand = brand.value
        val currentModel = model.value
        val currentYear = year.value
        val currentOdometer = odometer.value

        if (currentBrand.isBlank() || currentModel.isBlank() || currentYear.isBlank() || currentOdometer.isBlank()) {
            error = context.getString(R.string.vehicles_err_required); return
        }

        val yearInt = currentYear.toIntOrNull()
        val currentYearInt = LocalDate.now().year
        if (yearInt == null || yearInt < 1900 || yearInt > currentYearInt + 1) {
            error = context.getString(R.string.vehicles_err_year, currentYearInt + 1); return
        }

        val priceDouble = purchasePrice.value.toDoubleOrNull()
        if (purchasePrice.value.isNotBlank() && (priceDouble == null || priceDouble < 0 || priceDouble > 10_000_000.0)) {
            error = context.getString(R.string.vehicles_err_price); return
        }

        val priceCents = priceDouble?.times(100)?.roundToLong()
        val odoInput = currentOdometer.toDoubleOrNull() ?: 0.0
        val currentUnitSystem = unitSystem.value
        val odoKm = if (currentUnitSystem == "METRIC") odoInput.roundToLong()
        else UnitConverter.milesToKm(odoInput)

        viewModelScope.launch {
            isLoading = true
            try {
                val vehicle = VehicleEntity(
                    id = vehicleId ?: java.util.UUID.randomUUID().toString(),
                    brand = currentBrand,
                    model = currentModel,
                    year = yearInt,
                    plates = plates.value.takeIf { it.isNotBlank() },
                    odometer = odoKm,
                    currencyCode = currencyCode.value,
                    unitSystem = currentUnitSystem,
                    purchasePriceCents = priceCents
                )
                repo.upsert(vehicle)
                settingsStore.setActiveVehicle(vehicle.id)
                savedStateHandle["hasUnsavedChanges"] = false
                onDone()
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                error = com.sierracode.autocontrol.core.ui.UiError.from(e).asString(context)
            } finally {
                isLoading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditScreen(
    vehicleId: String?,
    onDone: () -> Unit,
    vm: VehicleEditViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val brand by vm.brand.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val year by vm.year.collectAsStateWithLifecycle()
    val plates by vm.plates.collectAsStateWithLifecycle()
    val odometer by vm.odometer.collectAsStateWithLifecycle()
    val purchasePrice by vm.purchasePrice.collectAsStateWithLifecycle()
    val currencyCode by vm.currencyCode.collectAsStateWithLifecycle()
    val unitSystem by vm.unitSystem.collectAsStateWithLifecycle()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsStateWithLifecycle()

    var expandedCurrency by remember { mutableStateOf(false) }
    var expandedUnits by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val isEditing = vehicleId != null

    BackHandler(enabled = hasUnsavedChanges) { showDiscardDialog = true }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_msg)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onDone() }) {
                    Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.action_keep_editing)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vehicleId == null) R.string.vehicles_new_title else R.string.vehicles_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (hasUnsavedChanges) showDiscardDialog = true else onDone() }) {
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
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = brand,
                onValueChange = { vm.updateBrand(it) },
                label = { Text(stringResource(R.string.vehicles_brand_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { vm.updateModel(it) },
                label = { Text(stringResource(R.string.vehicles_model_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = year,
                onValueChange = { vm.updateYear(it) },
                label = { Text(stringResource(R.string.vehicles_year_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = plates,
                onValueChange = { vm.updatePlates(it) },
                label = { Text(stringResource(R.string.vehicles_plates_label)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.vehicles_how_to_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            ExposedDropdownMenuBox(
                expanded = expandedUnits,
                onExpandedChange = { if (!isEditing) expandedUnits = !expandedUnits }
            ) {
                OutlinedTextField(
                    value = if (unitSystem == "METRIC") stringResource(R.string.settings_metric) else stringResource(R.string.settings_imperial),
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isEditing,
                    label = { Text(stringResource(R.string.vehicles_units_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnits) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expandedUnits, onDismissRequest = { expandedUnits = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_metric)) },
                        onClick = { vm.updateUnits("METRIC"); expandedUnits = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_imperial)) },
                        onClick = { vm.updateUnits("US"); expandedUnits = false }
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = expandedCurrency,
                onExpandedChange = { if (!isEditing) expandedCurrency = !expandedCurrency }
            ) {
                OutlinedTextField(
                    value = currencyCode,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isEditing,
                    label = { Text(stringResource(R.string.vehicles_currency_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurrency) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expandedCurrency, onDismissRequest = { expandedCurrency = false }) {
                    listOf("MXN", "USD", "CAD", "EUR", "GBP").forEach { code ->
                        DropdownMenuItem(
                            text = { Text(code) },
                            onClick = { vm.updateCurrency(code); expandedCurrency = false }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            OutlinedTextField(
                value = odometer,
                onValueChange = { vm.updateOdometer(it) },
                label = { Text(stringResource(R.string.vehicles_odometer_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                suffix = { Text(if (unitSystem == "METRIC") "km" else "mi") },
                modifier = Modifier.fillMaxWidth()
            )

            CurrencyField(
                value = purchasePrice,
                onValueChange = { vm.updatePurchasePrice(it) },
                label = stringResource(R.string.vehicles_price_label),
                currencyCode = currencyCode,
                imeAction = ImeAction.Done,
                onImeAction = { focusManager.clearFocus() },
                modifier = Modifier.fillMaxWidth()
            )

            vm.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    vm.save(context) {
                        Toast.makeText(context, context.getString(R.string.vehicles_saved_toast), Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(R.string.vehicles_save_btn))
            }
        }
    }
}