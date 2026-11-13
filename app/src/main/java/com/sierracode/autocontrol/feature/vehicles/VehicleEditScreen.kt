package com.sierracode.autocontrol.feature.vehicles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.data.local.entity.VehicleEntity
import com.sierracode.autocontrol.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehicleEditViewModel @Inject constructor(
    private val repo: VehicleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String? = savedStateHandle["vehicleId"]

    var brand by mutableStateOf("")
    var model by mutableStateOf("")
    var year by mutableStateOf("")
    var plates by mutableStateOf("")
    var odometer by mutableStateOf("")

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        if (vehicleId != null && vehicleId.isNotBlank()) {
            viewModelScope.launch {
                repo.get(vehicleId)?.let { v ->
                    brand = v.brand
                    model = v.model
                    year = v.year.toString()
                    plates = v.plates ?: ""
                    odometer = v.odometer.toString()
                }
            }
        }
    }

    fun save(onDone: () -> Unit) {
        if (brand.isBlank() || model.isBlank() || year.isBlank() || odometer.isBlank()) {
            error = "Por favor llena los campos obligatorios"
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val vehicle = VehicleEntity(
                    id = vehicleId?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
                    brand = brand,
                    model = model,
                    year = year.toIntOrNull() ?: 2024,
                    plates = plates.takeIf { it.isNotBlank() },
                    odometer = odometer.toLongOrNull() ?: 0L
                )
                repo.upsert(vehicle)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vehicleId == null) "Nuevo Vehículo" else "Editar Vehículo") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
                onValueChange = { vm.brand = it },
                label = { Text("Marca (Ej. Volkswagen) *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.model,
                onValueChange = { vm.model = it },
                label = { Text("Modelo (Ej. Vocho) *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.year,
                onValueChange = { vm.year = it },
                label = { Text("Año (Ej. 1998) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.plates,
                onValueChange = { vm.plates = it },
                label = { Text("Placas (Opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.odometer,
                onValueChange = { vm.odometer = it },
                label = { Text("Kilometraje actual *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            vm.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { vm.save(onDone) },
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