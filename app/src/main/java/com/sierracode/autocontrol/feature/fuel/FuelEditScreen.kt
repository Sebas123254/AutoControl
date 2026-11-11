package com.sierracode.autocontrol.feature.fuel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import com.sierracode.autocontrol.data.local.entity.FuelDetailEntity
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import com.sierracode.autocontrol.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FuelEditViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val vehicleRepo: VehicleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""

    var odometer by mutableStateOf("")
    var liters by mutableStateOf("")
    var pricePerLiter by mutableStateOf("")
    var total by mutableStateOf("")
    var station by mutableStateOf("")
    var isFullTank by mutableStateOf(true)

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // Función para autocalcular el total
    fun onLitersOrPriceChange() {
        val l = liters.toDoubleOrNull()
        val p = pricePerLiter.toDoubleOrNull()
        if (l != null && p != null) {
            total = String.format(java.util.Locale.US, "%.2f", l * p)
        }
    }

    fun save(onDone: () -> Unit) {
        val l = liters.toDoubleOrNull() ?: 0.0
        val t = total.toDoubleOrNull() ?: 0.0
        val p = pricePerLiter.toDoubleOrNull() ?: 0.0
        val odo = odometer.toLongOrNull()

        if (t <= 0 || l <= 0) {
            error = "Ingresa litros y total válidos"
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val expenseId = UUID.randomUUID().toString()
                val expense = ExpenseEntity(
                    id = expenseId,
                    vehicleId = vehicleId,
                    type = "FUEL",
                    categoryId = "fuel_gas", // Por defecto gasolina
                    amountCents = (t * 100).toLong(),
                    dateEpochDay = LocalDate.now().toEpochDay(),
                    odometer = odo,
                    description = "Carga de gasolina"
                )

                val fuelDetail = FuelDetailEntity(
                    expenseId = expenseId,
                    litersMilli = (l * 1000).toLong(),
                    pricePerLiterCents = (p * 100).toLong(),
                    fuelType = "GASOLINA",
                    station = station.takeIf { it.isNotBlank() },
                    fullTank = isFullTank
                )

                expenseRepo.saveWithDetails(expense, fuelDetail, null)

                if (odo != null && odo > 0) {
                    vehicleRepo.bumpOdometerIfHigher(vehicleId, odo)
                }

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
fun FuelEditScreen(
    vehicleId: String,
    onDone: () -> Unit,
    vm: FuelEditViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registrar Gasolina") },
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
                value = vm.odometer,
                onValueChange = { vm.odometer = it },
                label = { Text("Kilometraje actual *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vm.liters,
                    onValueChange = {
                        vm.liters = it
                        vm.onLitersOrPriceChange()
                    },
                    label = { Text("Litros *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = vm.pricePerLiter,
                    onValueChange = {
                        vm.pricePerLiter = it
                        vm.onLitersOrPriceChange()
                    },
                    label = { Text("Precio/L *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = vm.total,
                onValueChange = { vm.total = it },
                label = { Text("Total pagado ($) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.station,
                onValueChange = { vm.station = it },
                label = { Text("Estación (Ej. Pemex)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = vm.isFullTank,
                    onCheckedChange = { vm.isFullTank = it }
                )
                Spacer(Modifier.width(8.dp))
                Text("¿Llené el tanque? (Necesario para calcular km/L)")
            }

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
                    Text("Guardar Carga")
                }
            }
        }
    }
}