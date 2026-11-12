package com.sierracode.autocontrol.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDouble
import com.sierracode.autocontrol.core.format.formatKm
import com.sierracode.autocontrol.core.format.formatMoneyCents
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class EvaluatedSchedule(
    val name: String,
    val urgency: Urgency,
    val remainingKm: Long?,
    val remainingDays: Long?
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
    private val scheduleRepo: ScheduleRepository
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
            val costPerMonth = total / months

            Pair(costPerKm, costPerMonth)
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
                    EvaluatedSchedule(s.name, status.urgency, status.byKm, status.byDate)
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

    fun loadVehicle(id: String) {
        vehicleIdFlow.value = id
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vehicleId: String,
    onBack: () -> Unit,
    onRegisterExpense: () -> Unit,
    onRegisterFuel: () -> Unit,
    onRegisterMaintenance: () -> Unit,
    onOpenReminders: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = stringResource(R.string.vehicles_title))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRegisterExpense,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.dashboard_action_expense)) }
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                        val color = when (schedule.urgency) {
                            Urgency.OVERDUE -> MaterialTheme.colorScheme.errorContainer
                            Urgency.SOON -> MaterialTheme.colorScheme.tertiaryContainer
                            Urgency.OK -> MaterialTheme.colorScheme.surfaceVariant
                            Urgency.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val cdText = when (schedule.urgency) {
                            Urgency.OVERDUE -> stringResource(R.string.cd_maint_overdue)
                            Urgency.SOON -> stringResource(R.string.cd_maint_soon)
                            Urgency.OK -> stringResource(R.string.cd_maint_ok)
                            Urgency.UNKNOWN -> stringResource(R.string.cd_maint_unknown)
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = color)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = cdText, // MEJORA DE ACCESIBILIDAD
                                    tint = if (schedule.urgency == Urgency.OK) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(schedule.name, fontWeight = FontWeight.Bold)
                                    val text = when (schedule.urgency) {
                                        Urgency.OVERDUE -> stringResource(R.string.dashboard_status_overdue)
                                        Urgency.SOON -> stringResource(R.string.dashboard_status_soon)
                                        Urgency.OK -> stringResource(R.string.dashboard_status_ok)
                                        Urgency.UNKNOWN -> stringResource(R.string.dashboard_status_unknown)
                                    }
                                    Text(text, style = MaterialTheme.typography.bodySmall)

                                    if (schedule.remainingKm != null && schedule.remainingKm > 0) {
                                        Text(stringResource(R.string.dashboard_remaining_km, formatKm(schedule.remainingKm)), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoCard(title = stringResource(R.string.dashboard_mileage), value = formatKm(v.odometer), modifier = Modifier.weight(1f))
                    if (totalSpent > 0L) {
                        InfoCard(title = stringResource(R.string.dashboard_total_spent), value = formatMoneyCents(totalSpent), modifier = Modifier.weight(1f))
                    } else {
                        InfoCard(title = stringResource(R.string.dashboard_total_spent), value = stringResource(R.string.dashboard_no_expenses), modifier = Modifier.weight(1f))
                    }
                }

                if (costMetrics != null) {
                    val (costPerKm, costPerMonth) = costMetrics!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.dashboard_real_cost), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.dashboard_cost_per_km), style = MaterialTheme.typography.bodyMedium)
                                Text(costPerKm?.let { "$${"%.2f".format(it / 100)}/km" } ?: "—", fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.dashboard_cost_per_month), style = MaterialTheme.typography.bodyMedium)
                                Text(formatMoneyCents(costPerMonth), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.dashboard_avg_performance), style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = if (fuelStats?.avgKmL != null) formatDouble(fuelStats!!.avgKmL, 2, " km/L") else stringResource(R.string.dashboard_no_data),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (fuelStats?.hint != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(fuelStats!!.hint!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Text(stringResource(R.string.dashboard_quick_actions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRegisterFuel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dashboard_action_fuel)) }
                    Button(onClick = onRegisterMaintenance, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dashboard_action_maint)) }
                }
                OutlinedButton(onClick = onOpenReminders, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dashboard_action_reminders))
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
            Text(
                value,
                style = if (value == stringResource(R.string.dashboard_no_expenses)) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}