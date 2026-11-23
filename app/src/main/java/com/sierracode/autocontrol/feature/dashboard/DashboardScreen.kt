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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatCostPerDistance
import com.sierracode.autocontrol.core.format.formatEfficiency
import com.sierracode.autocontrol.core.format.formatKm
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.core.format.formatMoneyCentsWithCode
import com.sierracode.autocontrol.core.ui.components.EmptyStateCard
import com.sierracode.autocontrol.core.ui.components.IconBubble
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

data class SpendingComparison(
    val percentage: Double,
    val isIncrease: Boolean,
    val labelPeriodRes: Int
)

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
    val hintResId: Int?,
    val latestKmL: Double? = null,
    val isOverconsuming: Boolean = false,
    val consumptionDropPercent: Int = 0
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

    val spendingComparison = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(null) else summaryPeriodFlow.flatMapLatest { period ->
            val prevRange = getPreviousPeriodRange(period)
            if (prevRange == null) {
                flowOf(null)
            } else {
                val currentRange = getPeriodRange(period)
                combine(
                    expenseRepo.observeSum(id, currentRange.first, currentRange.second).map { it ?: 0L },
                    expenseRepo.observeSum(id, prevRange.first, prevRange.second).map { it ?: 0L }
                ) { currentSpent, prevSpent ->
                    calculateComparison(currentSpent, prevSpent, period)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
            expenseRepo.observeMinOdometer(id)
        ) { vehicle, total, minOdometer ->
            if (vehicle == null || total == null || total == 0L) return@combine null
            if (minOdometer == null) return@combine null
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

            val hintResId = when {
                points.isEmpty() -> R.string.dashboard_maint_fuel_hint
                points.any { it.odometer == null } -> R.string.dashboard_maint_odo_hint
                points.count { it.fullTank } < 2 -> R.string.dashboard_maint_full_hint
                summary.avgKmPerLiter == null -> R.string.dashboard_maint_consecutive_hint
                else -> null
            }
            FuelStats(
                avgKmL = summary.avgKmPerLiter,
                hintResId = hintResId,
                latestKmL = summary.latestKmPerLiter,
                isOverconsuming = summary.isOverconsuming,
                consumptionDropPercent = summary.consumptionDropPercent
            )
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

    private fun getPreviousPeriodRange(period: SummaryPeriod): Pair<Long, Long>? {
        val today = LocalDate.now()
        return when (period) {
            SummaryPeriod.THIS_MONTH -> {
                val lastMonth = today.minusMonths(1)
                lastMonth.withDayOfMonth(1).toEpochDay() to lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).toEpochDay()
            }
            SummaryPeriod.LAST_MONTH -> {
                val twoMonthsAgo = today.minusMonths(2)
                twoMonthsAgo.withDayOfMonth(1).toEpochDay() to twoMonthsAgo.withDayOfMonth(twoMonthsAgo.lengthOfMonth()).toEpochDay()
            }
            SummaryPeriod.THIS_YEAR -> {
                val lastYear = today.minusYears(1)
                lastYear.withDayOfYear(1).toEpochDay() to lastYear.withDayOfYear(lastYear.lengthOfYear()).toEpochDay()
            }
            SummaryPeriod.ALL_TIME -> null
        }
    }

    private fun calculateComparison(
        currentSpent: Long,
        previousSpent: Long,
        period: SummaryPeriod
    ): SpendingComparison? {
        if (period == SummaryPeriod.ALL_TIME) return null
        if (previousSpent <= 0L) return null
        if (currentSpent == previousSpent) return null

        val diff = currentSpent - previousSpent
        val percentage = (diff.toDouble() / previousSpent.toDouble()) * 100.0

        val labelRes = when (period) {
            SummaryPeriod.THIS_MONTH -> R.string.dashboard_spending_vs_last_month
            SummaryPeriod.LAST_MONTH -> R.string.dashboard_spending_vs_prev_month
            SummaryPeriod.THIS_YEAR -> R.string.dashboard_spending_vs_last_year
            SummaryPeriod.ALL_TIME -> return null
        }

        return SpendingComparison(
            percentage = kotlin.math.abs(percentage),
            isIncrease = diff > 0,
            labelPeriodRes = labelRes
        )
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

    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val allVehicles by vm.allVehicles.collectAsStateWithLifecycle()
    val totalSpent by vm.totalSpent.collectAsStateWithLifecycle()
    val spendingComparison by vm.spendingComparison.collectAsStateWithLifecycle()
    val allTimeSpent by vm.allTimeSpent.collectAsStateWithLifecycle()
    val dueReminders by vm.dueReminders.collectAsStateWithLifecycle()
    val costPerKm by vm.costPerKm.collectAsStateWithLifecycle()
    val fuelStats by vm.fuelStats.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val selectedPeriod by vm.summaryPeriod.collectAsStateWithLifecycle()

    var showAllSchedules by remember { mutableStateOf(false) }
    var scheduleToDelete by remember { mutableStateOf<EvaluatedSchedule?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val hasPendingAlerts = schedules.any { it.urgency == Urgency.OVERDUE || it.urgency == Urgency.SOON } ||
            dueReminders.isNotEmpty()

    val deletedSnackbarTemplate = stringResource(R.string.vehicles_delete_maint_snackbar, "%s")
    val updatedSnackbar = stringResource(R.string.vehicles_maint_updated)
    val errorIntervalMsg = stringResource(R.string.dashboard_maint_err_interval)
    val errorNegativeMsg = stringResource(R.string.dashboard_maint_err_negative)
    val deleteMaintLabel = stringResource(R.string.dashboard_maint_delete)
    val cdReminders = stringResource(R.string.cd_reminders)
    val cdSettings = stringResource(R.string.cd_settings)
    val cdEditVehicle = stringResource(R.string.cd_edit_vehicle)
    val cdSwitchVehicle = stringResource(R.string.cd_switch_vehicle)
    val cdBackToVehicles = stringResource(R.string.cd_back_to_vehicles)

    if (scheduleToDelete != null) {
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text(stringResource(R.string.vehicles_delete_maint_title)) },
            text = { Text(stringResource(R.string.vehicles_delete_maint_desc, scheduleToDelete!!.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val nombre = scheduleToDelete!!.name
                    vm.deleteSchedule(scheduleToDelete!!.id)
                    scheduleToDelete = null
                    scope.launch { snackbarHostState.showSnackbar(deletedSnackbarTemplate.format(nombre)) }
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { scheduleToDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showAllSchedules) {
        AlertDialog(
            onDismissRequest = { showAllSchedules = false },
            title = { Text(stringResource(R.string.vehicles_maint_scheduled)) },
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
                                scope.launch { snackbarHostState.showSnackbar(deletedSnackbarTemplate.format(nombre)) }
                            }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAllSchedules = false }) { Text(stringResource(R.string.action_close)) } }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.vehicles_edit_maint_title, s.name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = intervalKm,
                    onValueChange = { intervalKm = it; errorMsg = null },
                    label = { Text(stringResource(R.string.vehicles_maint_interval_km)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = intervalMonths,
                    onValueChange = { intervalMonths = it; errorMsg = null },
                    label = { Text(stringResource(R.string.vehicles_maint_interval_months)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remindBeforeKm,
                    onValueChange = { remindBeforeKm = it; errorMsg = null },
                    label = { Text(stringResource(R.string.vehicles_maint_remind_km)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remindBeforeDays,
                    onValueChange = { remindBeforeDays = it; errorMsg = null },
                    label = { Text(stringResource(R.string.vehicles_maint_remind_days)) },
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
                        errorMsg = errorIntervalMsg
                        return@Button
                    }
                    if (newRemKm < 0 || newRemDays < 0) {
                        errorMsg = errorNegativeMsg
                        return@Button
                    }

                    vm.updateSchedule(s.copy(
                        intervalKm = newKm,
                        intervalMonths = newMonths,
                        remindBeforeKm = newRemKm,
                        remindBeforeDays = newRemDays
                    ))
                    scope.launch { snackbarHostState.showSnackbar(updatedSnackbar) }
                }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text(stringResource(R.string.action_save))
                }

                TextButton(
                    onClick = {
                        scheduleToDelete = vm.scheduleToEdit
                        vm.openScheduleEditor(null)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(deleteMaintLabel, color = MaterialTheme.colorScheme.error)
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
                                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = cdSwitchVehicle)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = cdBackToVehicles)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenReminders) {
                        BadgedBox(badge = { if (hasPendingAlerts) { Badge(modifier = Modifier.padding(end = 4.dp)) } }) {
                            Icon(Icons.Default.Notifications, contentDescription = cdReminders)
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = cdSettings)
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
                // ============================
                // 1. Tarjeta del vehículo (sin cambios)
                // ============================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(icon = Icons.Default.DirectionsCar, size = 48.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${v.brand} ${v.model}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${formatKm(v.odometer)} • ${v.plates ?: stringResource(R.string.vehicles_no_plates)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onEditVehicle) {
                            Icon(Icons.Default.Edit, contentDescription = cdEditVehicle, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ============================
                // 2. Acciones rápidas (sin cambios)
                // ============================
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionButton(Icons.Default.LocalGasStation, stringResource(R.string.dashboard_action_fuel), onRegisterFuel, Modifier.weight(1f))
                    QuickActionButton(Icons.Default.Receipt, stringResource(R.string.dashboard_action_expense), onRegisterExpense, Modifier.weight(1f))
                    QuickActionButton(Icons.Default.Build, stringResource(R.string.dashboard_action_maint), onRegisterMaintenance, Modifier.weight(1f))
                }

                // ============================
                // 3. RESUMEN (filtros + gasto principal + métricas secundarias)
                // ============================
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = stringResource(R.string.dashboard_summary))

                    // Selector general del resumen (2x2)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedPeriod == SummaryPeriod.THIS_MONTH,
                            onClick = { vm.setSummaryPeriod(SummaryPeriod.THIS_MONTH) },
                            label = { Text(stringResource(R.string.dashboard_period_this_month)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedPeriod == SummaryPeriod.LAST_MONTH,
                            onClick = { vm.setSummaryPeriod(SummaryPeriod.LAST_MONTH) },
                            label = { Text(stringResource(R.string.dashboard_period_last_month)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedPeriod == SummaryPeriod.THIS_YEAR,
                            onClick = { vm.setSummaryPeriod(SummaryPeriod.THIS_YEAR) },
                            label = { Text(stringResource(R.string.dashboard_period_this_year)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedPeriod == SummaryPeriod.ALL_TIME,
                            onClick = { vm.setSummaryPeriod(SummaryPeriod.ALL_TIME) },
                            label = { Text(stringResource(R.string.dashboard_period_all_time)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    ResumenCard(
                        periodLabelRes = when (selectedPeriod) {
                            SummaryPeriod.THIS_MONTH -> R.string.dashboard_period_this_month
                            SummaryPeriod.LAST_MONTH -> R.string.dashboard_period_last_month
                            SummaryPeriod.THIS_YEAR -> R.string.dashboard_period_this_year
                            SummaryPeriod.ALL_TIME -> R.string.dashboard_period_all_time
                        },
                        totalSpent = totalSpent,
                        comparison = spendingComparison,
                        costPerKm = costPerKm,
                        fuelStats = fuelStats
                    )
                }

                // ============================
                // 4. ESTADO DEL VEHÍCULO (resumen de mantenimiento)
                // ============================
                VehicleStatusSection(
                    schedules = schedules,
                    onViewAll = { showAllSchedules = true },
                    onScheduleClick = { vm.openScheduleEditor(it) }
                )

                // ============================
                // 5. COSTO ACUMULADO (información secundaria)
                // ============================
                if (v.purchasePriceCents != null) {
                    TotalCostCard(
                        purchasePriceCents = v.purchasePriceCents,
                        allTimeSpent = allTimeSpent
                    )
                } else {
                    EmptyStateCard(
                        icon = Icons.Default.Savings,
                        title = stringResource(R.string.dashboard_price_missing),
                        description = stringResource(R.string.dashboard_price_desc),
                        actionLabel = stringResource(R.string.dashboard_add_price),
                        onActionClick = onEditVehicle
                    )
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ==========================================================
// Tarjeta principal de Resumen:
// - Gasto del periodo = métrica principal (hero)
// - Costo por km + Rendimiento = métricas secundarias
// - Aviso de sobreconsumo integrado (reutiliza lógica existente)
// ==========================================================
@Composable
fun ResumenCard(
    periodLabelRes: Int,
    totalSpent: Long,
    comparison: SpendingComparison?,
    costPerKm: Double?,
    fuelStats: FuelStats?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gasto del periodo — métrica principal
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(periodLabelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatMoneyCentsWithCode(totalSpent),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (comparison != null) {
                    val formattedPercentage = String.format(java.util.Locale.US, "%.1f", comparison.percentage)
                    val periodName = stringResource(comparison.labelPeriodRes)
                    val comparisonText = if (comparison.isIncrease) {
                        stringResource(R.string.dashboard_spending_increase, formattedPercentage, periodName)
                    } else {
                        stringResource(R.string.dashboard_spending_decrease, formattedPercentage, periodName)
                    }
                    val comparisonColor = if (comparison.isIncrease) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }

                    Text(
                        text = comparisonText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = comparisonColor
                    )
                }
            }

            HorizontalDivider()

            // Métricas secundarias: costo por km + rendimiento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.dashboard_cost_per_km),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        costPerKm?.let { formatCostPerDistance(it) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.dashboard_performance),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        fuelStats?.avgKmL?.let { formatEfficiency(it) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Hint cuando no hay datos de rendimiento (sin inventar valores)
            if (fuelStats?.avgKmL == null) {
                fuelStats?.hintResId?.let { hintRes ->
                    Text(
                        stringResource(hintRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Aviso de sobreconsumo (reutiliza lógica existente)
            if (fuelStats?.isOverconsuming == true && fuelStats.latestKmL != null && fuelStats.avgKmL != null) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.fuel_overconsumption_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            stringResource(
                                R.string.fuel_overconsumption_desc,
                                formatEfficiency(fuelStats.latestKmL),
                                fuelStats.consumptionDropPercent,
                                formatEfficiency(fuelStats.avgKmL)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ==========================================================
// Estado del vehículo:
// - Estado global (derivado del conjunto de schedules existentes)
// - Top 2 mantenimientos usando datos reales de `schedules`
// - No inventa elementos (aceite, llantas, etc.): solo lo que exista
// ==========================================================
@Composable
fun VehicleStatusSection(
    schedules: List<EvaluatedSchedule>,
    onViewAll: () -> Unit,
    onScheduleClick: (EvaluatedSchedule) -> Unit
) {
    if (schedules.isEmpty()) return

    val overallUrgency = when {
        schedules.any { it.urgency == Urgency.OVERDUE } -> Urgency.OVERDUE
        schedules.any { it.urgency == Urgency.SOON } -> Urgency.SOON
        schedules.all { it.urgency == Urgency.OK } -> Urgency.OK
        else -> Urgency.UNKNOWN
    }

    val containerColor = when (overallUrgency) {
        Urgency.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        Urgency.SOON -> MaterialTheme.colorScheme.tertiaryContainer
        Urgency.OK -> MaterialTheme.colorScheme.primaryContainer
        Urgency.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (overallUrgency) {
        Urgency.OVERDUE -> MaterialTheme.colorScheme.onErrorContainer
        Urgency.SOON -> MaterialTheme.colorScheme.onTertiaryContainer
        Urgency.OK -> MaterialTheme.colorScheme.onPrimaryContainer
        Urgency.UNKNOWN -> MaterialTheme.colorScheme.onSurface
    }
    val statusIcon = when (overallUrgency) {
        Urgency.OVERDUE -> Icons.Default.Warning
        Urgency.SOON -> Icons.Default.Info
        Urgency.OK -> Icons.Default.CheckCircle
        Urgency.UNKNOWN -> Icons.Outlined.HelpOutline
    }
    val statusText = when (overallUrgency) {
        Urgency.OVERDUE -> stringResource(R.string.cd_maint_overdue)
        Urgency.SOON -> stringResource(R.string.cd_maint_soon)
        Urgency.OK -> stringResource(R.string.dashboard_vehicle_status_ok)
        Urgency.UNKNOWN -> stringResource(R.string.cd_maint_unknown)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_vehicle_status),
            actionLabel = if (schedules.size > 1) stringResource(R.string.dashboard_view_all) else null,
            onActionClick = onViewAll
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(statusIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                Text(
                    statusText,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        schedules.take(2).forEach { schedule ->
            MaintenanceCard(schedule, onClick = { onScheduleClick(schedule) })
        }
    }
}

// ==========================================================
// Costo acumulado — información secundaria
// Se reduce su protagonismo usando elevación 1 y tipografía menor.
// ==========================================================
@Composable
fun TotalCostCard(
    purchasePriceCents: Long,
    allTimeSpent: Long
) {
    val totalCost = calculateTotalCost(purchasePriceCents, allTimeSpent)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Default.Savings, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        stringResource(R.string.dashboard_total_cost),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.dashboard_purchase_price, formatMoneyCents(purchasePriceCents)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                formatMoneyCents(totalCost),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (subtitle != null) {
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
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (schedule.urgency) {
        Urgency.OVERDUE -> MaterialTheme.colorScheme.onErrorContainer
        Urgency.SOON -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val bubbleContainer = when (schedule.urgency) {
        Urgency.OVERDUE -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        Urgency.SOON -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val bubbleTint = when (schedule.urgency) {
        Urgency.OVERDUE -> MaterialTheme.colorScheme.error
        Urgency.SOON -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val statusText = when (schedule.urgency) {
        Urgency.OVERDUE -> stringResource(R.string.cd_maint_overdue)
        Urgency.SOON -> stringResource(R.string.cd_maint_soon)
        Urgency.OK -> stringResource(R.string.cd_maint_ok)
        Urgency.UNKNOWN -> stringResource(R.string.cd_maint_unknown)
    }
    val statusIcon = when (schedule.urgency) {
        Urgency.OVERDUE -> Icons.Default.Warning
        Urgency.SOON -> Icons.Default.Info
        Urgency.OK -> Icons.Default.CheckCircle
        Urgency.UNKNOWN -> Icons.Outlined.HelpOutline
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconBubble(
                        icon = Icons.Default.Build,
                        size = 40.dp,
                        containerColor = bubbleContainer,
                        contentColor = bubbleTint
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                schedule.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.cd_edit_vehicle),
                                modifier = Modifier.size(16.dp),
                                tint = contentColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                StatusChip(
                    text = statusText,
                    icon = statusIcon,
                    containerColor = contentColor.copy(alpha = 0.12f),
                    contentColor = contentColor
                )
            }
            if (schedule.targetOdometer != null && schedule.remainingKm != null) {
                val progressColor = when (schedule.urgency) {
                    Urgency.OVERDUE -> MaterialTheme.colorScheme.error
                    Urgency.SOON -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { schedule.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = progressColor,
                        trackColor = contentColor.copy(alpha = 0.15f)
                    )
                    Text(
                        text = stringResource(
                            R.string.dashboard_maint_card_faltan,
                            formatKm(schedule.remainingKm),
                            formatKm(schedule.targetOdometer)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}