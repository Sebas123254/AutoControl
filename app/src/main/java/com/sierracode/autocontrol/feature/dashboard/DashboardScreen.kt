package com.sierracode.autocontrol.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.sierracode.autocontrol.core.ui.components.EmptyStateCard
import com.sierracode.autocontrol.core.ui.components.StatusChip
import com.sierracode.autocontrol.data.local.entity.MaintenanceScheduleEntity
import com.sierracode.autocontrol.data.repository.*
import com.sierracode.autocontrol.domain.calc.FuelPoint
import com.sierracode.autocontrol.domain.calc.Urgency
import com.sierracode.autocontrol.domain.calc.calculateTotalCost
import com.sierracode.autocontrol.domain.calc.evaluateSchedule
import com.sierracode.autocontrol.domain.calc.summarizeFuel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class SummaryPeriod { THIS_MONTH, LAST_MONTH, THIS_YEAR, ALL_TIME }

data class EvaluatedSchedule(
    val id: String,
    val name: String,
    val urgency: Urgency,
    val remainingKm: Long?,
    val remainingDays: Long?,
    val progress: Float,
    val targetOdometer: Long?,
    val originalSchedule: MaintenanceScheduleEntity
)

data class FuelStats(
    val avgKmL: Double?,
    val hint: String?
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val vehicleRepo: VehicleRepository,
    private val expenseRepo: ExpenseRepository,
    private val fuelRepo: FuelRepository,
    private val scheduleRepo: ScheduleRepository,
    private val reminderRepo: ReminderRepository
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)
    private val summaryPeriodFlow = MutableStateFlow(SummaryPeriod.THIS_MONTH)

    val summaryPeriod = summaryPeriodFlow.asStateFlow()
    fun setSummaryPeriod(period: SummaryPeriod) { summaryPeriodFlow.value = period }

    val vehicle = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(null) else vehicleRepo.observe(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val allVehicles = vehicleRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalSpent = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(0L) else summaryPeriodFlow.flatMapLatest { period ->
            val (start, end) = getPeriodRange(period)
            expenseRepo.observeSum(id, start, end).map { it ?: 0L }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val allTimeSpent = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(0L) else expenseRepo.observeSum(id, 0L, Long.MAX_VALUE).map { it ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val dueReminders = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else reminderRepo.observeByVehicle(id).map { list ->
            val horizon = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
            list.filter { it.enabled && it.triggerAtEpochMillis <= horizon }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val costPerKm = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(null) else combine(
            vehicleRepo.observe(id),
            expenseRepo.observeSum(id, 0L, Long.MAX_VALUE),
            expenseRepo.observeByVehicle(id)
        ) { vehicle, total, expenses ->
            if (vehicle == null || total == null || total == 0L) return@combine null
            val minOdometer = expenses.minOfOrNull { it.odometer ?: vehicle.odometer } ?: vehicle.odometer
            val kmRecorridos = vehicle.odometer - minOdometer
            if (kmRecorridos > 0) total.toDouble() / kmRecorridos else null
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
                    val target = if (s.lastOdometer != null && s.intervalKm != null) s.lastOdometer + s.intervalKm else null
                    val progress = if (target != null && s.lastOdometer != null && target > s.lastOdometer) {
                        val avanzado = (vehicle.odometer - s.lastOdometer).toFloat()
                        val total = (target - s.lastOdometer).toFloat()
                        (avanzado / total).coerceIn(0f, 1f)
                    } else 0f

                    EvaluatedSchedule(s.id, s.name, status.urgency, status.byKm, status.byDate, progress, target, s)
                }
            }.sortedBy {
                when(it.urgency) { Urgency.OVERDUE -> 0; Urgency.SOON -> 1; Urgency.OK -> 2; Urgency.UNKNOWN -> 3 }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fuelStats = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(null) else fuelRepo.observeFuel(id).map { fuelList ->
            val points = fuelList.reversed().map { f ->
                FuelPoint(f.odometer, f.litersMilli / 1000.0, f.fullTank, f.amountCents, f.dateEpochDay)
            }
            val summary = summarizeFuel(points)
            val hint = when {
                points.isEmpty() -> "Registra tu primera carga de gasolina."
                points.any { it.odometer == null } -> "Falta el odómetro en algunas cargas."
                points.count { it.fullTank } < 2 -> "Necesitas al menos 2 cargas de 'Tanque lleno'."
                summary.avgKmPerLiter == null -> "Asegúrate de que las cargas con tanque lleno sean consecutivas."
                else -> null
            }
            FuelStats(summary.avgKmPerLiter, hint)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var scheduleToEdit by mutableStateOf<EvaluatedSchedule?>(null)
        private set

    fun loadVehicle(id: String) { vehicleIdFlow.value = id }
    fun openScheduleEditor(schedule: EvaluatedSchedule?) { scheduleToEdit = schedule }

    fun deleteSchedule(id: String) {
        viewModelScope.launch { scheduleRepo.delete(id) }
    }

    fun updateSchedule(schedule: MaintenanceScheduleEntity) {
        viewModelScope.launch {
            scheduleRepo.upsert(schedule)
            scheduleToEdit = null
        }
    }

    private fun getPeriodRange(period: SummaryPeriod): Pair<Long, Long> {
        val today = LocalDate.now()
        return when (period) {
            SummaryPeriod.THIS_MONTH -> today.withDayOfMonth(1).toEpochDay() to today.toEpochDay()
            SummaryPeriod.LAST_MONTH -> {
                val lastMonth = today.minusMonths(1)
                lastMonth.withDayOfMonth(1).toEpochDay() to lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).toEpochDay()
            }
            SummaryPeriod.THIS_YEAR -> today.withDayOfYear(1).toEpochDay() to today.toEpochDay()
            SummaryPeriod.ALL_TIME -> 0L to today.toEpochDay()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vehicleId: String,
    onBack: () -> Unit,
    onEditVehicle: () -> Unit,
    onSwitchVehicle: (String) -> Unit,
    onRegisterExpense: () -> Unit,
    onRegisterFuel: () -> Unit,
    onRegisterMaintenance: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) { vm.loadVehicle(vehicleId) }

    val vehicle by vm.vehicle.collectAsState()
    val allVehicles by vm.allVehicles.collectAsState()
    val totalSpent by vm.totalSpent.collectAsState()
    val allTimeSpent by vm.allTimeSpent.collectAsState()
    val dueReminders by vm.dueReminders.collectAsState()
    val costPerKm by vm.costPerKm.collectAsState()
    val fuelStats by vm.fuelStats.collectAsState()
    val schedules by vm.schedules.collectAsState()
    val selectedPeriod by vm.summaryPeriod.collectAsState()

    var showAllSchedules by remember { mutableStateOf(false) }
    var scheduleToDelete by remember { mutableStateOf<EvaluatedSchedule?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val hasPendingAlerts = schedules.any { it.urgency == Urgency.OVERDUE || it.urgency == Urgency.SOON } ||
            dueReminders.isNotEmpty()

    if (scheduleToDelete != null) {
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text("Eliminar mantenimiento") },
            text = { Text("¿Deseas eliminar la programación de '${scheduleToDelete!!.name}'? El historial de gastos no se borrará.") },
            confirmButton = {
                TextButton(onClick = {
                    val nombre = scheduleToDelete!!.name
                    vm.deleteSchedule(scheduleToDelete!!.id)
                    scheduleToDelete = null
                    scope.launch { snackbarHostState.showSnackbar("Mantenimiento '$nombre' eliminado") }
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { scheduleToDelete = null }) { Text("Cancelar") } }
        )
    }

    if (showAllSchedules) {
        AlertDialog(
            onDismissRequest = { showAllSchedules = false },
            title = { Text("Mantenimientos programados") },
            text = {
                Column {
                    schedules.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.name)
                            TextButton(onClick = {
                                val nombre = s.name
                                vm.deleteSchedule(s.id)
                                showAllSchedules = false
                                scope.launch { snackbarHostState.showSnackbar("Mantenimiento '$nombre' eliminado") }
                            }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAllSchedules = false }) { Text("Cerrar") } }
        )
    }

    if (vm.scheduleToEdit != null) {
        val s = vm.scheduleToEdit!!.originalSchedule
        var intervalKm by remember { mutableStateOf(s.intervalKm?.toString() ?: "") }
        var intervalMonths by remember { mutableStateOf(s.intervalMonths?.toString() ?: "") }
        var remindBeforeKm by remember { mutableStateOf(s.remindBeforeKm.toString()) }
        var remindBeforeDays by remember { mutableStateOf(s.remindBeforeDays.toString()) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(onDismissRequest = { vm.openScheduleEditor(null) }, sheetState = sheetState) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Editar: ${s.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = intervalKm,
                    onValueChange = { intervalKm = it; errorMsg = null },
                    label = { Text("Intervalo en kilómetros (Opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = intervalMonths,
                    onValueChange = { intervalMonths = it; errorMsg = null },
                    label = { Text("Intervalo en meses (Opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remindBeforeKm,
                    onValueChange = { remindBeforeKm = it; errorMsg = null },
                    label = { Text("Avisar antes de (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remindBeforeDays,
                    onValueChange = { remindBeforeDays = it; errorMsg = null },
                    label = { Text("Avisar antes de (días)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Button(onClick = {
                    val newKm = intervalKm.toLongOrNull()
                    val newMonths = intervalMonths.toIntOrNull()
                    val newRemKm = remindBeforeKm.toLongOrNull() ?: 500L
                    val newRemDays = remindBeforeDays.toIntOrNull() ?: 15

                    if ((newKm != null && newKm <= 0) || (newMonths != null && newMonths <= 0)) {
                        errorMsg = "Los intervalos deben ser mayores a 0."
                        return@Button
                    }
                    if (newRemKm < 0 || newRemDays < 0) {
                        errorMsg = "Los avisos no pueden ser negativos."
                        return@Button
                    }

                    vm.updateSchedule(s.copy(
                        intervalKm = newKm,
                        intervalMonths = newMonths,
                        remindBeforeKm = newRemKm,
                        remindBeforeDays = newRemDays
                    ))
                    scope.launch { snackbarHostState.showSnackbar("Mantenimiento actualizado") }
                }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Guardar cambios")
                }

                TextButton(
                    onClick = {
                        scheduleToDelete = vm.scheduleToEdit
                        vm.openScheduleEditor(null)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Eliminar mantenimiento", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    var showSwitchMenu by remember { mutableStateOf(false) }
                    val canSwitch = allVehicles.size > 1
                    Box {
                        Row(
                            modifier = Modifier.clip(MaterialTheme.shapes.small)
                                .clickable(enabled = canSwitch) { showSwitchMenu = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = vehicle?.let { "${it.brand} ${it.model}" } ?: stringResource(R.string.dashboard_title),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (canSwitch) {
                                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_switch_vehicle))
                            }
                        }
                        DropdownMenu(expanded = showSwitchMenu, onDismissRequest = { showSwitchMenu = false }) {
                            allVehicles.forEach { v ->
                                DropdownMenuItem(text = { Text("${v.brand} ${v.model}") }, onClick = {
                                    showSwitchMenu = false
                                    if (v.id != vehicleId) onSwitchVehicle(v.id)
                                })
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a Mis vehículos")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenReminders) {
                        BadgedBox(badge = { if (hasPendingAlerts) { Badge(modifier = Modifier.padding(end = 4.dp)) } }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Recordatorios")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        }
    ) { padding ->
        if (vehicle == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val v = vehicle!!
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${v.brand} ${v.model}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${formatKm(v.odometer)} • ${v.plates ?: "Sin placas"}", style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = onEditVehicle) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar vehículo", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionButton(Icons.Default.LocalGasStation, "Gasolina", onRegisterFuel, Modifier.weight(1f))
                    QuickActionButton(Icons.Default.Receipt, "Gasto", onRegisterExpense, Modifier.weight(1f))
                    QuickActionButton(Icons.Default.Build, "Mantenimiento", onRegisterMaintenance, Modifier.weight(1f))
                }

                val topSchedule = schedules.firstOrNull()
                if (topSchedule != null) {
                    SectionHeader("Próximo mantenimiento", if (schedules.size > 1) "Ver todos" else null) {
                        if (schedules.size > 1) showAllSchedules = true
                    }
                    MaintenanceCard(topSchedule, onClick = { vm.openScheduleEditor(topSchedule) })
                }

                SectionHeader("Resumen financiero")

                // FILTROS DEL DASHBOARD
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedPeriod == SummaryPeriod.THIS_MONTH,
                        onClick = { vm.setSummaryPeriod(SummaryPeriod.THIS_MONTH) },
                        label = { Text("Este mes") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedPeriod == SummaryPeriod.LAST_MONTH,
                        onClick = { vm.setSummaryPeriod(SummaryPeriod.LAST_MONTH) },
                        label = { Text("Mes pasado") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedPeriod == SummaryPeriod.THIS_YEAR,
                        onClick = { vm.setSummaryPeriod(SummaryPeriod.THIS_YEAR) },
                        label = { Text("Este año") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedPeriod == SummaryPeriod.ALL_TIME,
                        onClick = { vm.setSummaryPeriod(SummaryPeriod.ALL_TIME) },
                        label = { Text("Histórico") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricCard(
                        title = when(selectedPeriod) {
                            SummaryPeriod.THIS_MONTH -> "Gasto del mes"
                            SummaryPeriod.LAST_MONTH -> "Gasto mes pasado"
                            SummaryPeriod.THIS_YEAR -> "Gasto del año"
                            SummaryPeriod.ALL_TIME -> "Gasto histórico"
                        },
                        value = formatMoneyCents(totalSpent),
                        modifier = Modifier.weight(1f)
                    )
                    if (costPerKm != null) {
                        MetricCard(
                            title = "Costo por km histórico",
                            value = formatCostPerDistance(costPerKm),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        MetricCard(
                            title = "Costo por km histórico",
                            value = "—",
                            modifier = Modifier.weight(1f),
                            subtitle = "Necesitas 2 gastos con kilometraje"
                        )
                    }
                }

                if (v.purchasePriceCents != null) {
                    val totalCost = calculateTotalCost(v.purchasePriceCents, allTimeSpent)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text("Costo total acumulado (histórico)", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "Precio de compra: ${formatMoneyCents(v.purchasePriceCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                formatMoneyCents(totalCost),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    EmptyStateCard(
                        title = "Precio de compra no registrado",
                        description = "Agrega el precio original para conocer el costo total acumulado del vehículo.",
                        actionLabel = "Agregar precio",
                        onActionClick = onEditVehicle
                    )
                }

                SectionHeader("Rendimiento")
                if (fuelStats?.avgKmL != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(formatEfficiency(fuelStats!!.avgKmL), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Promedio histórico", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                } else {
                    EmptyStateCard(
                        title = "Sin datos suficientes",
                        description = fuelStats?.hint ?: "Registra al menos 2 cargas de tanque lleno.",
                        actionLabel = "Registrar carga",
                        onActionClick = onRegisterFuel
                    )
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun QuickActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(64.dp), contentPadding = PaddingValues(0.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onActionClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (actionLabel != null) { TextButton(onClick = onActionClick) { Text(actionLabel) } }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MaintenanceCard(schedule: EvaluatedSchedule, onClick: () -> Unit) {
    val containerColor = when (schedule.urgency) {
        Urgency.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        Urgency.SOON -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (schedule.urgency) {
        Urgency.OVERDUE -> MaterialTheme.colorScheme.onErrorContainer
        Urgency.SOON -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (schedule.urgency) {
        Urgency.OVERDUE -> "Vencido"
        Urgency.SOON -> "Próximo"
        Urgency.OK -> "Al día"
        Urgency.UNKNOWN -> "Sin datos"
    }
    val statusIcon = when (schedule.urgency) {
        Urgency.OVERDUE -> Icons.Default.Warning
        Urgency.SOON -> Icons.Default.Info
        Urgency.OK -> Icons.Default.CheckCircle
        Urgency.UNKNOWN -> Icons.Outlined.HelpOutline
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(schedule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(16.dp), tint = contentColor)
                }
                StatusChip(
                    text = statusText,
                    icon = statusIcon,
                    containerColor = contentColor.copy(alpha = 0.1f),
                    contentColor = contentColor
                )
            }
            Spacer(Modifier.height(8.dp))
            if (schedule.targetOdometer != null && schedule.remainingKm != null) {
                LinearProgressIndicator(
                    progress = { schedule.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Faltan ${formatKm(schedule.remainingKm)} · a los ${formatKm(schedule.targetOdometer)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
        }
    }
}