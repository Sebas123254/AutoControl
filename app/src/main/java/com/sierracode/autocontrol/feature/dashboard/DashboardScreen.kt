package com.sierracode.autocontrol.feature.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatCostPerDistance
import com.sierracode.autocontrol.core.format.formatEfficiency
import com.sierracode.autocontrol.core.format.formatKm
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.data.local.entity.MaintenanceScheduleEntity
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.repository.*
import com.sierracode.autocontrol.domain.calc.FuelPoint
import com.sierracode.autocontrol.domain.calc.Urgency
import com.sierracode.autocontrol.domain.calc.evaluateSchedule
import com.sierracode.autocontrol.domain.calc.summarizeFuel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class EvaluatedSchedule(
    val id: String,
    val name: String,
    val urgency: Urgency,
    val remainingKm: Long?,
    val remainingDays: Long?,
    val originalSchedule: MaintenanceScheduleEntity
)

data class FuelStats(val avgKmL: Double?, val hint: String?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository,
    private val expenseRepo: ExpenseRepository,
    private val fuelRepo: FuelRepository,
    private val scheduleRepo: ScheduleRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    val vehicle = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(null) else vehicleRepo.observe(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val totalSpent = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(0L) else expenseRepo.observeSum(id, 0L, Long.MAX_VALUE).map { it ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val costMetrics = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(null) else combine(
            vehicleRepo.observe(id),
            expenseRepo.observeSum(id, 0L, Long.MAX_VALUE),
            expenseRepo.observeByVehicle(id)
        ) { vehicle, total, expenses ->
            if (vehicle == null || total == null) return@combine null
            val minOdometer = expenses.minOfOrNull { it.odometer ?: vehicle.odometer } ?: vehicle.odometer
            val kmRecorridos = vehicle.odometer - minOdometer
            val costPerKm = if (kmRecorridos > 0) total.toDouble() / kmRecorridos else null
            val firstDateEpochDay = expenses.minOfOrNull { it.dateEpochDay }
            val months = if (firstDateEpochDay != null) {
                val start = LocalDate.ofEpochDay(firstDateEpochDay)
                val now = LocalDate.now()
                ChronoUnit.MONTHS.between(start, now).coerceAtLeast(1)
            } else 1L
            Pair(costPerKm, total / months)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val schedules = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else combine(
            scheduleRepo.observeActive(id),
            vehicleRepo.observe(id)
        ) { schedules, vehicle ->
            val today = LocalDate.now().toEpochDay()
            schedules.mapNotNull { s ->
                if (vehicle == null) null else {
                    val status = evaluateSchedule(
                        currentOdometer = vehicle.odometer,
                        todayEpochDay = today,
                        lastOdometer = s.lastOdometer,
                        intervalKm = s.intervalKm,
                        lastDateEpochDay = s.lastDateEpochDay,
                        intervalMonths = s.intervalMonths,
                        remindBeforeKm = s.remindBeforeKm,
                        remindBeforeDays = s.remindBeforeDays
                    )
                    EvaluatedSchedule(s.id, s.name, status.urgency, status.byKm, status.byDate, s)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fuelStats = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(null) else fuelRepo.observeFuel(id).map { fuelList ->
            val points = fuelList.reversed().map { f ->
                FuelPoint(
                    odometer = f.odometer,
                    liters = f.litersMilli / 1000.0,
                    fullTank = f.fullTank,
                    amountCents = f.amountCents,
                    epochDay = f.dateEpochDay
                )
            }
            val summary = summarizeFuel(points)
            val hint = when {
                points.isEmpty() -> "Registra tu primera carga de gasolina."
                points.any { it.odometer == null } -> "Falta el odómetro en algunas cargas. Ingrésalo para calcular."
                points.count { it.fullTank } < 2 -> "Necesitas al menos 2 cargas de 'Tanque lleno'."
                summary.avgKmPerLiter == null -> "Asegúrate de que las cargas con tanque lleno sean consecutivas."
                else -> null
            }
            FuelStats(summary.avgKmPerLiter, hint)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val allVehicles = vehicleRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var scheduleToEdit by mutableStateOf<EvaluatedSchedule?>(null)
        private set
    var scheduleMenuTarget by mutableStateOf<EvaluatedSchedule?>(null)
        private set

    fun loadVehicle(id: String) {
        vehicleIdFlow.value = id
        // Aseguramos que se guarde como activo
        viewModelScope.launch { settingsStore.setActiveVehicle(id) }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch { scheduleRepo.delete(id) }
    }

    fun openScheduleEditor(schedule: EvaluatedSchedule?) { scheduleToEdit = schedule }
    fun openScheduleMenu(schedule: EvaluatedSchedule?) { scheduleMenuTarget = schedule }

    fun updateSchedule(schedule: MaintenanceScheduleEntity) {
        viewModelScope.launch {
            scheduleRepo.upsert(schedule)
            scheduleToEdit = null
        }
    }

    fun setActiveVehicle(id: String) {
        viewModelScope.launch { settingsStore.setActiveVehicle(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    vehicleId: String,
    vehicleName: String,
    onBack: () -> Unit,
    onRegisterExpense: () -> Unit,
    onRegisterFuel: () -> Unit,
    onRegisterMaintenance: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchVehicle: (String) -> Unit,
    onEditVehicle: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) {
        vm.loadVehicle(vehicleId)
    }

    val vehicle by vm.vehicle.collectAsState()
    val totalSpent by vm.totalSpent.collectAsState()
    val fuelStats by vm.fuelStats.collectAsState()
    val costMetrics by vm.costMetrics.collectAsState()
    val schedules by vm.schedules.collectAsState()
    val allVehicles by vm.allVehicles.collectAsState()

    var scheduleToDelete by remember { mutableStateOf<EvaluatedSchedule?>(null) }

    // ... (Los diálogos de borrar y editar mantenimiento se mantienen igual)
    if (scheduleToDelete != null) {
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text("Eliminar recordatorio") },
            text = { Text("¿Deseas eliminar el recordatorio de '${scheduleToDelete!!.name}'? Esto no borrará el gasto histórico, solo detendrá las alertas futuras.") },
            confirmButton = { TextButton(onClick = { vm.deleteSchedule(scheduleToDelete!!.id); scheduleToDelete = null }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { scheduleToDelete = null }) { Text("Cancelar") } }
        )
    }

    if (vm.scheduleMenuTarget != null) {
        AlertDialog(
            onDismissRequest = { vm.openScheduleMenu(null) },
            title = { Text(vm.scheduleMenuTarget!!.name) },
            text = { Text("¿Qué deseas hacer con este mantenimiento programado?") },
            confirmButton = { TextButton(onClick = { vm.openScheduleEditor(vm.scheduleMenuTarget); vm.openScheduleMenu(null) }) { Text("Editar") } },
            dismissButton = { Row { TextButton(onClick = { scheduleToDelete = vm.scheduleMenuTarget; vm.openScheduleMenu(null) }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = { vm.openScheduleMenu(null) }) { Text("Cancelar") } } }
        )
    }

    if (vm.scheduleToEdit != null) {
        val s = vm.scheduleToEdit!!.originalSchedule
        var intervalKm by remember { mutableStateOf(s.intervalKm?.toString() ?: "") }
        var intervalMonths by remember { mutableStateOf(s.intervalMonths?.toString() ?: "") }
        var remindBeforeKm by remember { mutableStateOf(s.remindBeforeKm.toString()) }
        var remindBeforeDays by remember { mutableStateOf(s.remindBeforeDays.toString()) }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(onDismissRequest = { vm.openScheduleEditor(null) }, sheetState = sheetState) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Editar ${s.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = intervalKm, onValueChange = { intervalKm = it }, label = { Text("Intervalo en kilómetros (Opcional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = intervalMonths, onValueChange = { intervalMonths = it }, label = { Text("Intervalo en meses (Opcional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = remindBeforeKm, onValueChange = { remindBeforeKm = it }, label = { Text("Avisar antes de (km)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = remindBeforeDays, onValueChange = { remindBeforeDays = it }, label = { Text("Avisar antes de (días)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val newKm = intervalKm.toLongOrNull()
                    val newMonths = intervalMonths.toIntOrNull()
                    val newRemKm = remindBeforeKm.toLongOrNull() ?: 500L
                    val newRemDays = remindBeforeDays.toIntOrNull() ?: 15
                    if (newKm != null && newKm <= 0) return@Button
                    if (newMonths != null && newMonths <= 0) return@Button
                    vm.updateSchedule(s.copy(intervalKm = newKm, intervalMonths = newMonths, remindBeforeKm = newRemKm, remindBeforeDays = newRemDays))
                }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Guardar cambios") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    var showSwitchMenu by remember { mutableStateOf(false) }
                    val canSwitch = allVehicles.size > 1
                    Box {
                        Row(
                            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(enabled = canSwitch) { showSwitchMenu = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = vehicleName.ifBlank { stringResource(R.string.dashboard_title) }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (canSwitch) {
                                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_switch_vehicle))
                            }
                        }
                        DropdownMenu(expanded = showSwitchMenu, onDismissRequest = { showSwitchMenu = false }) {
                            allVehicles.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text("${v.brand} ${v.model}") },
                                    onClick = {
                                        showSwitchMenu = false
                                        if (v.id != vehicleId) {
                                            vm.setActiveVehicle(v.id)
                                            onSwitchVehicle(v.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back_to_vehicles)) } },
                actions = {
                    IconButton(onClick = onOpenReminders) { Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.reminders_title)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title)) }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options)) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.cd_edit_vehicle)) }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }, onClick = { showMenu = false; onEditVehicle() })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onRegisterExpense, icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text(stringResource(R.string.dashboard_action_expense)) })
        }
    ) { padding ->
        if (vehicle == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val v = vehicle!!
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("${v.brand} ${v.model}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${v.year} • ${v.plates ?: stringResource(R.string.vehicles_no_plates)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                if (schedules.isNotEmpty()) {
                    Text(stringResource(R.string.dashboard_scheduled), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    schedules.forEach { schedule ->
                        val color = when (schedule.urgency) { Urgency.OVERDUE -> MaterialTheme.colorScheme.errorContainer; Urgency.SOON -> MaterialTheme.colorScheme.tertiaryContainer; Urgency.OK -> MaterialTheme.colorScheme.surfaceVariant; Urgency.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant }
                        val cdText = when (schedule.urgency) { Urgency.OVERDUE -> stringResource(R.string.cd_maint_overdue); Urgency.SOON -> stringResource(R.string.cd_maint_soon); Urgency.OK -> stringResource(R.string.cd_maint_ok); Urgency.UNKNOWN -> stringResource(R.string.cd_maint_unknown) }
                        Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { vm.openScheduleEditor(schedule) }, onLongClick = { vm.openScheduleMenu(schedule) }), colors = CardDefaults.cardColors(containerColor = color)) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = cdText, tint = if (schedule.urgency == Urgency.OK) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(schedule.name, fontWeight = FontWeight.Bold)
                                    val text = when (schedule.urgency) { Urgency.OVERDUE -> stringResource(R.string.dashboard_status_overdue); Urgency.SOON -> stringResource(R.string.dashboard_status_soon); Urgency.OK -> stringResource(R.string.dashboard_status_ok); Urgency.UNKNOWN -> stringResource(R.string.dashboard_status_unknown) }
                                    Text(text, style = MaterialTheme.typography.bodySmall)
                                    if (schedule.remainingKm != null && schedule.remainingKm > 0) {
                                        Text(stringResource(R.string.dashboard_remaining_km, formatKm(schedule.remainingKm)), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { vm.openScheduleEditor(schedule) }) { Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Aquí usa el nuevo string "Odómetro"
                    InfoCard(title = stringResource(R.string.dashboard_mileage), value = formatKm(v.odometer), modifier = Modifier.weight(1f))
                    if (totalSpent > 0L) { InfoCard(title = stringResource(R.string.dashboard_total_spent), value = formatMoneyCents(totalSpent), modifier = Modifier.weight(1f)) } else { InfoCard(title = stringResource(R.string.dashboard_total_spent), value = stringResource(R.string.dashboard_no_expenses), modifier = Modifier.weight(1f)) }
                }

                if (costMetrics != null) {
                    val (costPerKm, costPerMonth) = costMetrics!!
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.dashboard_real_cost), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.dashboard_cost_per_km), style = MaterialTheme.typography.bodyMedium)
                                Text(formatCostPerDistance(costPerKm), fontWeight = FontWeight.Bold) // <-- Cambiará a /mi si es Imperial
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.dashboard_cost_per_month), style = MaterialTheme.typography.bodyMedium)
                                Text(formatMoneyCents(costPerMonth), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.dashboard_avg_performance), style = MaterialTheme.typography.labelMedium)
                        Text(text = if (fuelStats?.avgKmL != null) formatEfficiency(fuelStats!!.avgKmL) else stringResource(R.string.dashboard_no_data), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (fuelStats?.hint != null) { Spacer(modifier = Modifier.height(4.dp)); Text(fuelStats!!.hint!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }

                Text(stringResource(R.string.dashboard_quick_actions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRegisterFuel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dashboard_action_fuel)) }
                    Button(onClick = onRegisterMaintenance, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dashboard_action_maint)) }
                }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = if (value == stringResource(R.string.dashboard_no_expenses)) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}