package com.sierracode.autocontrol.feature.maintenance

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
import com.sierracode.autocontrol.data.local.dao.ScheduleDao
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import com.sierracode.autocontrol.data.local.entity.MaintenanceDetailEntity
import com.sierracode.autocontrol.data.local.entity.MaintenanceScheduleEntity
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import com.sierracode.autocontrol.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MaintenanceEditViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val vehicleRepo: VehicleRepository,
    private val scheduleDao: ScheduleDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""

    var serviceName by mutableStateOf("")
    var amount by mutableStateOf("")
    var odometer by mutableStateOf("")
    var workshop by mutableStateOf("")
    var nextOdometer by mutableStateOf("")

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun save(onDone: () -> Unit) {
        val amt = amount.toDoubleOrNull() ?: 0.0
        val odo = odometer.toLongOrNull()

        if (serviceName.isBlank() || amt <= 0) {
            error = "Ingresa un nombre y un monto válido"
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val expenseId = UUID.randomUUID().toString()
                val expense = ExpenseEntity(
                    id = expenseId,
                    vehicleId = vehicleId,
                    type = "MAINTENANCE",
                    categoryId = "maint_general", // Categoría general
                    amountCents = (amt * 100).toLong(),
                    dateEpochDay = LocalDate.now().toEpochDay(),
                    odometer = odo,
                    description = serviceName
                )

                val nextOdo = nextOdometer.toLongOrNull()
                val detail = MaintenanceDetailEntity(
                    expenseId = expenseId,
                    serviceName = serviceName,
                    workshop = workshop.takeIf { it.isNotBlank() },
                    nextOdometer = nextOdo
                )

                expenseRepo.saveWithDetails(expense, null, detail)

                // Si el usuario puso un "próximo kilometraje", creamos una regla automática
                if (nextOdo != null && odo != null) {
                    val schedule = MaintenanceScheduleEntity(
                        vehicleId = vehicleId,
                        name = serviceName,
                        intervalKm = nextOdo - odo, // Calculamos el intervalo
                        lastOdometer = odo,
                        lastDateEpochDay = LocalDate.now().toEpochDay()
                    )
                    scheduleDao.upsert(schedule)
                }

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
fun MaintenanceEditScreen(
    vehicleId: String,
    onDone: () -> Unit,
    vm: MaintenanceEditViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registrar Mantenimiento") },
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
                value = vm.serviceName,
                onValueChange = { vm.serviceName = it },
                label = { Text("Servicio (Ej. Cambio de aceite) *") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.amount,
                onValueChange = { vm.amount = it },
                label = { Text("Costo total ($) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.odometer,
                onValueChange = { vm.odometer = it },
                label = { Text("Kilometraje actual *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.workshop,
                onValueChange = { vm.workshop = it },
                label = { Text("Taller (Opcional)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.nextOdometer,
                onValueChange = { vm.nextOdometer = it },
                label = { Text("Próximo servicio en este km (Opcional)") },
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
                    Text("Guardar Mantenimiento")
                }
            }
        }
    }
}