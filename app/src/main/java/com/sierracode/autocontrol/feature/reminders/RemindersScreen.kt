package com.sierracode.autocontrol.feature.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.formatKm
import com.sierracode.autocontrol.core.ui.components.EmptyStateCard
import com.sierracode.autocontrol.core.ui.components.StatusChip
import com.sierracode.autocontrol.core.ui.components.StatusVariant
import com.sierracode.autocontrol.core.ui.components.showUndoSnackbar
import com.sierracode.autocontrol.data.local.entity.ReminderEntity
import com.sierracode.autocontrol.data.repository.DocumentRepository
import com.sierracode.autocontrol.data.repository.ReminderRepository
import com.sierracode.autocontrol.data.repository.ScheduleRepository
import com.sierracode.autocontrol.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Item de UI que combina el [ReminderEntity] con datos del documento/schedule
 * para poder calcular el estado REAL de vencimiento y mostrarlo dinámicamente.
 */
data class ReminderUiItem(
    val reminder: ReminderEntity,
    val documentName: String?,
    val serviceName: String?,
    val expiryEpochDay: Long?,
    val nextDateEpochDay: Long?,
    val nextOdometer: Long?,
    val currentOdometer: Long?
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminderRepo: ReminderRepository,
    private val documentRepo: DocumentRepository,
    private val scheduleRepo: ScheduleRepository,
    private val vehicleRepo: VehicleRepository
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    val reminders = combine(
        vehicleIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else reminderRepo.observeByVehicle(id)
        },
        vehicleIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else documentRepo.observeByVehicle(id)
        },
        vehicleIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else scheduleRepo.observeActive(id)
        },
        vehicleIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(null) else vehicleRepo.observe(id)
        }
    ) { reminderList, docs, schedules, vehicle ->
        reminderList.map { reminder ->
            when (reminder.sourceType) {
                "DOCUMENT" -> {
                    val doc = docs.find { it.id == reminder.sourceId }
                    ReminderUiItem(
                        reminder = reminder,
                        documentName = doc?.name,
                        serviceName = null,
                        expiryEpochDay = doc?.expiryDateEpochDay,
                        nextDateEpochDay = null,
                        nextOdometer = null,
                        currentOdometer = null
                    )
                }
                "MAINTENANCE" -> {
                    val schedule = schedules.find { it.id == reminder.sourceId }
                    val nextDate = schedule?.let { s ->
                        if (s.lastDateEpochDay != null && s.intervalMonths != null) {
                            LocalDate.ofEpochDay(s.lastDateEpochDay)
                                .plusMonths(s.intervalMonths.toLong())
                                .toEpochDay()
                        } else null
                    }
                    val nextOdo = schedule?.let { s ->
                        if (s.lastOdometer != null && s.intervalKm != null) {
                            s.lastOdometer + s.intervalKm
                        } else null
                    }
                    ReminderUiItem(
                        reminder = reminder,
                        documentName = null,
                        serviceName = schedule?.name,
                        expiryEpochDay = null,
                        nextDateEpochDay = nextDate,
                        nextOdometer = nextOdo,
                        currentOdometer = vehicle?.odometer
                    )
                }
                else -> ReminderUiItem(
                    reminder = reminder,
                    documentName = null,
                    serviceName = null,
                    expiryEpochDay = null,
                    nextDateEpochDay = null,
                    nextOdometer = null,
                    currentOdometer = null
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadVehicle(id: String) {
        vehicleIdFlow.value = id
    }

    fun toggleReminder(reminder: ReminderEntity, enabled: Boolean) {
        viewModelScope.launch {
            reminderRepo.upsert(reminder.copy(enabled = enabled))
        }
    }

    suspend fun deleteReminder(id: String) {
        reminderRepo.deleteById(id)
    }

    fun restoreReminder(reminder: ReminderEntity) {
        viewModelScope.launch { reminderRepo.upsert(reminder) }
    }

    fun snoozeReminder(reminder: ReminderEntity, days: Int) {
        viewModelScope.launch {
            val newTrigger = System.currentTimeMillis() + (days * 24L * 60L * 60L * 1000L)
            reminderRepo.upsert(
                reminder.copy(
                    triggerAtEpochMillis = newTrigger,
                    lastNotifiedAt = null,
                    // Resetear el stage para que vuelva a notificar al alcanzar el nuevo trigger.
                    lastNotifiedStage = null
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    vehicleId: String,
    onBack: () -> Unit,
    vm: RemindersViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) { vm.loadVehicle(vehicleId) }

    val items by vm.reminders.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val undoLabel = stringResource(R.string.action_undo)

    val deleteWithUndo: (ReminderEntity) -> Unit = { reminder ->
        scope.launch {
            vm.deleteReminder(reminder.id)
            snackbarHostState.showUndoSnackbar(
                message = context.getString(R.string.reminder_deleted),
                actionLabel = undoLabel
            ) {
                vm.restoreReminder(reminder)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.reminders_empty_title),
                    description = stringResource(R.string.reminders_empty_desc)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.reminder.id }) { item ->
                    ReminderCard(
                        item = item,
                        onToggle = { enabled -> vm.toggleReminder(item.reminder, enabled) },
                        onDelete = { deleteWithUndo(item.reminder) },
                        onSnooze = { days -> vm.snoozeReminder(item.reminder, days) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderCard(
    item: ReminderUiItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    val reminder = item.reminder
    val today = LocalDate.now().toEpochDay()

    // Título neutral basado en el documento/servicio real
    val title = when {
        item.documentName != null -> stringResource(R.string.reminder_label_document, item.documentName)
        item.serviceName != null -> stringResource(R.string.reminder_label_maintenance, item.serviceName)
        else -> reminder.title
    }

    // Calcular estado real
    val (statusText, statusVariant, statusIcon) = computeStatus(item, today)

    // Fecha del disparo programado
    val triggerDateEpochDay = Instant.ofEpochMilli(reminder.triggerAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()
    val triggerDateText = formatDate(triggerDateEpochDay)

    var showSnoozeMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(text = statusText, icon = statusIcon, variant = statusVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.reminders_notice, triggerDateText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(onClick = { showSnoozeMenu = true }) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = stringResource(R.string.reminders_snooze),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = showSnoozeMenu,
                        onDismissRequest = { showSnoozeMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reminders_snooze_1d)) },
                            onClick = { onSnooze(1); showSnoozeMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reminders_snooze_1w)) },
                            onClick = { onSnooze(7); showSnoozeMenu = false }
                        )
                    }
                }
                Switch(checked = reminder.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Calcula el estado real del reminder combinando fecha + km.
 * Devuelve (texto, variant, icono) para el StatusChip.
 */
@Composable
private fun computeStatus(
    item: ReminderUiItem,
    today: Long
): Triple<String, StatusVariant, androidx.compose.ui.graphics.vector.ImageVector> {
    // DOCUMENT: solo por fecha de vencimiento
    if (item.expiryEpochDay != null) {
        val days = item.expiryEpochDay - today
        return when {
            days < 0 -> Triple(
                stringResource(R.string.reminder_status_expired_days, (-days).toInt()),
                StatusVariant.ERROR,
                Icons.Default.Warning
            )
            days == 0L -> Triple(
                stringResource(R.string.reminder_status_expires_today),
                StatusVariant.ERROR,
                Icons.Default.Warning
            )
            days == 1L -> Triple(
                stringResource(R.string.reminder_status_expires_tomorrow),
                StatusVariant.WARNING,
                Icons.Default.Info
            )
            days <= 7 -> Triple(
                stringResource(R.string.reminder_status_expires_in_days, days.toInt()),
                StatusVariant.WARNING,
                Icons.Default.Info
            )
            days <= 30 -> Triple(
                stringResource(R.string.reminder_status_expires_in_days, days.toInt()),
                StatusVariant.INFO,
                Icons.Default.Info
            )
            else -> Triple(
                stringResource(R.string.reminder_status_expires_in_days, days.toInt()),
                StatusVariant.NEUTRAL,
                Icons.Default.CheckCircle
            )
        }
    }

    // MAINTENANCE: puede tener fecha y/o km. Mostramos el más urgente.
    val kmRemaining = if (item.nextOdometer != null && item.currentOdometer != null) {
        item.nextOdometer - item.currentOdometer
    } else null
    val daysRemaining = item.nextDateEpochDay?.let { it - today }

    // Vencido por km
    if (kmRemaining != null && kmRemaining <= 0) {
        return Triple(
            stringResource(R.string.reminder_status_km_overdue),
            StatusVariant.ERROR,
            Icons.Default.Warning
        )
    }
    // Vencido por fecha
    if (daysRemaining != null && daysRemaining < 0) {
        return Triple(
            stringResource(R.string.reminder_status_expired_days, (-daysRemaining).toInt()),
            StatusVariant.ERROR,
            Icons.Default.Warning
        )
    }
    // Próximo por km (<=100 o <=500)
    if (kmRemaining != null && kmRemaining <= 100) {
        return Triple(
            stringResource(R.string.reminder_status_km_remaining, formatKm(kmRemaining)),
            StatusVariant.WARNING,
            Icons.Default.Info
        )
    }
    if (kmRemaining != null && kmRemaining <= 500) {
        return Triple(
            stringResource(R.string.reminder_status_km_remaining, formatKm(kmRemaining)),
            StatusVariant.INFO,
            Icons.Default.Info
        )
    }
    // Próximo por fecha (<=7 o <=30)
    if (daysRemaining != null && daysRemaining <= 7) {
        return Triple(
            stringResource(R.string.reminder_status_expires_in_days, daysRemaining.toInt()),
            StatusVariant.WARNING,
            Icons.Default.Info
        )
    }
    if (daysRemaining != null && daysRemaining <= 30) {
        return Triple(
            stringResource(R.string.reminder_status_expires_in_days, daysRemaining.toInt()),
            StatusVariant.INFO,
            Icons.Default.Info
        )
    }
    // Programado lejos
    if (daysRemaining != null) {
        return Triple(
            stringResource(R.string.reminder_status_expires_in_days, daysRemaining.toInt()),
            StatusVariant.NEUTRAL,
            Icons.Default.CheckCircle
        )
    }
    if (kmRemaining != null) {
        return Triple(
            stringResource(R.string.reminder_status_km_remaining, formatKm(kmRemaining)),
            StatusVariant.NEUTRAL,
            Icons.Default.CheckCircle
        )
    }

    // Fallback (no hay datos)
    return Triple(
        stringResource(R.string.reminder_status_scheduled),
        StatusVariant.NEUTRAL,
        Icons.Default.Info
    )
}