package com.sierracode.autocontrol.feature.vehicles

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.data.local.entity.VehicleEntity
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.repository.VehicleRepository
import com.sierracode.autocontrol.domain.calc.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class VehicleEditViewModel @Inject constructor(
    private val repo: VehicleRepository,
    private val settingsStore: SettingsStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String? = savedStateHandle["vehicleId"]

    var brand by mutableStateOf("")
    var model by mutableStateOf("")
    var year by mutableStateOf("")
    var plates by mutableStateOf("")
    var odometer by mutableStateOf("")
    var purchasePrice by mutableStateOf("")

    var currencyCode by mutableStateOf("MXN")
    var unitSystem by mutableStateOf("METRIC")

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var hasUnsavedChanges by mutableStateOf(false)
        private set

    fun updateBrand(v: String) { brand = v; hasUnsavedChanges = true }
    fun updateModel(v: String) { model = v; hasUnsavedChanges = true }
    fun updateYear(v: String) { year = v; hasUnsavedChanges = true }
    fun updatePlates(v: String) { plates = v; hasUnsavedChanges = true }
    fun updateOdometer(v: String) { odometer = v; hasUnsavedChanges = true }
    fun updatePurchasePrice(v: String) { purchasePrice = v; hasUnsavedChanges = true; error = null }
    fun updateCurrency(v: String) { currencyCode = v; hasUnsavedChanges = true }
    fun updateUnits(v: String) { unitSystem = v; hasUnsavedChanges = true }

    init {
        if (vehicleId != null && vehicleId.isNotBlank()) {
            viewModelScope.launch {
                repo.get(vehicleId)?.let { v ->
                    brand = v.brand
                    model = v.model
                    year = v.year.toString()
                    plates = v.plates ?: ""

                    unitSystem = v.unitSystem
                    val odoDisplay = if (unitSystem == "METRIC") v.odometer else UnitConverter.kmToMiles(v.odometer).roundToLong()
                    odometer = odoDisplay.toString()

                    currencyCode = v.currencyCode
                    purchasePrice = v.purchasePriceCents?.let { (it / 100.0).toString() } ?: ""
                }
            }
        } else {
            viewModelScope.launch {
                currencyCode = settingsStore.currencyCode.first()
                unitSystem = settingsStore.unitSystem.first().name
            }
        }
    }

    fun save(onDone: () -> Unit) {
        if (brand.isBlank() || model.isBlank() || year.isBlank() || odometer.isBlank()) {
            error = "Por favor llena los campos obligatorios"
            return
        }

        val priceDouble = purchasePrice.toDoubleOrNull()
        if (purchasePrice.isNotBlank() && (priceDouble == null || priceDouble < 0 || priceDouble > 10_000_000.0)) {
            error = "El precio de compra debe ser válido y menor a 10 millones."
            return
        }

        val priceCents = priceDouble?.times(100)?.roundToLong()
        val odoInput = odometer.toDoubleOrNull() ?: 0.0
        val odoKm = if (unitSystem == "METRIC") odoInput.roundToLong() else UnitConverter.milesToKm(odoInput)

        viewModelScope.launch {
            isLoading = true
            try {
                val vehicle = VehicleEntity(
                    id = vehicleId?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
                    brand = brand,
                    model = model,
                    year = year.toIntOrNull() ?: 2024,
                    plates = plates.takeIf { it.isNotBlank() },
                    odometer = odoKm,
                    currencyCode = currencyCode,
                    unitSystem = unitSystem,
                    purchasePriceCents = priceCents
                )
                repo.upsert(vehicle)
                settingsStore.setActiveVehicle(vehicle.id)
                hasUnsavedChanges = false
                onDone()
            } catch (e: Exception) {
                error = "Error al guardar: ${e.message}"
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
    var expandedCurrency by remember { mutableStateOf(false) }
    var expandedUnits by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val isEditing = vehicleId != null

    BackHandler(enabled = vm.hasUnsavedChanges) { showDiscardDialog = true }

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
                title = { Text(if (vehicleId == null) "Nuevo Vehículo" else "Editar Vehículo") },
                navigationIcon = {
                    IconButton(onClick = { if (vm.hasUnsavedChanges) showDiscardDialog = true else onDone() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(value = vm.brand, onValueChange = { vm.updateBrand(it) }, label = { Text("Marca (Ej. Volkswagen) *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vm.model, onValueChange = { vm.updateModel(it) }, label = { Text("Modelo (Ej. Vocho) *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vm.year, onValueChange = { vm.updateYear(it) }, label = { Text("Año (Ej. 1998) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vm.plates, onValueChange = { vm.updatePlates(it) }, label = { Text("Placas (Opcional)") }, modifier = Modifier.fillMaxWidth())

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("¿Cómo registrarás este vehículo? *", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // V1: bloquear el cambio de sistema de unidades al editar (evita corrupción del odómetro).
            ExposedDropdownMenuBox(
                expanded = expandedUnits,
                onExpandedChange = { if (!isEditing) expandedUnits = !expandedUnits }
            ) {
                OutlinedTextField(
                    value = if (vm.unitSystem == "METRIC") "Métrico (km, L)" else "Imperial (mi, gal)",
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isEditing,
                    label = { Text("Sistema de unidades") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnits) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expandedUnits, onDismissRequest = { expandedUnits = false }) {
                    DropdownMenuItem(text = { Text("Métrico (km, L)") }, onClick = { vm.updateUnits("METRIC"); expandedUnits = false })
                    DropdownMenuItem(text = { Text("Imperial (mi, gal)") }, onClick = { vm.updateUnits("US"); expandedUnits = false })
                }
            }

            ExposedDropdownMenuBox(expanded = expandedCurrency, onExpandedChange = { if (!isEditing) expandedCurrency = !expandedCurrency }) {
                OutlinedTextField(
                    value = vm.currencyCode,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isEditing,
                    label = { Text("Divisa") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurrency) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expandedCurrency, onDismissRequest = { expandedCurrency = false }) {
                    listOf("MXN", "USD", "CAD", "EUR", "GBP").forEach { code ->
                        DropdownMenuItem(text = { Text(code) }, onClick = { vm.updateCurrency(code); expandedCurrency = false })
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            OutlinedTextField(
                value = vm.odometer,
                onValueChange = { vm.updateOdometer(it) },
                label = { Text("Odómetro actual *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text(if (vm.unitSystem == "METRIC") "km" else "mi") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.purchasePrice,
                onValueChange = { vm.updatePurchasePrice(it) },
                label = { Text("Precio original o de compra (Opcional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(vm.currencyCode) },
                modifier = Modifier.fillMaxWidth()
            )

            vm.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    vm.save {
                        Toast.makeText(context, "Vehículo guardado correctamente", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Guardar Vehículo")
            }
        }
    }
}