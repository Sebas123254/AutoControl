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

    var currencyCode by mutableStateOf("MXN")
    var unitSystem by mutableStateOf("METRIC")

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // NUEVO: Rastreo de cambios para advertir al usuario
    var hasUnsavedChanges by mutableStateOf(false)
        private set

    // NUEVOS: Métodos de actualización
    fun updateBrand(v: String) { brand = v; hasUnsavedChanges = true }
    fun updateModel(v: String) { model = v; hasUnsavedChanges = true }
    fun updateYear(v: String) { year = v; hasUnsavedChanges = true }
    fun updatePlates(v: String) { plates = v; hasUnsavedChanges = true }
    fun updateOdometer(v: String) { odometer = v; hasUnsavedChanges = true }
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
                    unitSystem = unitSystem
                )
                repo.upsert(vehicle)

                // FORZAMOS QUE ESTE VEHÍCULO SEA EL ACTIVO
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

    // NUEVO: Interceptar botón Atrás si hay cambios sin guardar
    BackHandler(enabled = vm.hasUnsavedChanges) {
        showDiscardDialog = true
    }

    // NUEVO: Diálogo de descarte
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDone()
                }) { Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error) }
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
                    IconButton(onClick = {
                        if (vm.hasUnsavedChanges) showDiscardDialog = true else onDone()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
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
            OutlinedTextField(
                value = vm.brand,
                onValueChange = { vm.updateBrand(it) },
                label = { Text("Marca (Ej. Volkswagen) *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.model,
                onValueChange = { vm.updateModel(it) },
                label = { Text("Modelo (Ej. Vocho) *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.year,
                onValueChange = { vm.updateYear(it) },
                label = { Text("Año (Ej. 1998) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.plates,
                onValueChange = { vm.updatePlates(it) },
                label = { Text("Placas (Opcional)") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "¿Cómo registrarás este vehículo? *",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Las unidades pueden cambiarse después. La divisa requerirá conversión.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(expanded = expandedUnits, onExpandedChange = { expandedUnits = !expandedUnits }) {
                OutlinedTextField(
                    value = if (vm.unitSystem == "METRIC") "Métrico (km, L)" else "Imperial (mi, gal)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sistema de unidades") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnits) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expandedUnits, onDismissRequest = { expandedUnits = false }) {
                    DropdownMenuItem(
                        text = { Text("Métrico (km, L)") },
                        onClick = { vm.updateUnits("METRIC"); expandedUnits = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Imperial (mi, gal)") },
                        onClick = { vm.updateUnits("US"); expandedUnits = false }
                    )
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
                        DropdownMenuItem(
                            text = { Text(code) },
                            onClick = { vm.updateCurrency(code); expandedCurrency = false }
                        )
                    }
                }
            }

            if (isEditing) {
                Text(
                    text = "El cambio de divisa se hace en Ajustes del vehículo (Próximamente)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Vista previa", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    val unit = if (vm.unitSystem == "METRIC") "km" else "mi"
                    val moneySymbol = when(vm.currencyCode) { "MXN" -> "$"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; else -> "$" }

                    Text(
                        text = "Odómetro: 45,000 $unit · Gasto: $moneySymbol 1,234.50 ${vm.currencyCode}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
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

            vm.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    vm.save {
                        // NUEVO: Mensaje de confirmación antes de salir
                        Toast.makeText(context, "Vehículo guardado correctamente", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Guardar Vehículo")
                }
            }
        }
    }
}