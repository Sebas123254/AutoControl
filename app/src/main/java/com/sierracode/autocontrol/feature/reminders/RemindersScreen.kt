package com.sierracode.autocontrol.feature.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.ui.components.EmptyStateCard
import com.sierracode.autocontrol.core.ui.components.showUndoSnackbar
import com.sierracode.autocontrol.data.local.entity.ReminderEntity
import com.sierracode.autocontrol.data.repository.ReminderRepository
import com.sierracode.autocontrol.domain.reminder.ReminderResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminderRepo: ReminderRepository
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    val reminders = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else reminderRepo.observeByVehicle(id)
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
                    lastNotifiedAt = null
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
    LaunchedEffect(vehicleId) {
        vm.loadVehicle(vehicleId)
    }

    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
        if (reminders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
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
                items(reminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onToggle = { enabled -> vm.toggleReminder(reminder, enabled) },
                        onDelete = { deleteWithUndo(reminder) },
                        onSnooze = { days -> vm.snoozeReminder(reminder, days) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderCard(
    reminder: ReminderEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    val context = LocalContext.current

    // Resolver title/body con los templates del idioma actual.
    val displayTitle = ReminderResolver.resolveTitle(context, reminder)
    val displayBody = ReminderResolver.resolveBody(context, reminder)

    val dateEpochDay = Instant.ofEpochMilli(reminder.triggerAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()
    val dateText = formatDate(dateEpochDay)

    var showSnoozeMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = displayBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.reminders_notice, dateText),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

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
                        onClick = {
                            onSnooze(1)
                            showSnoozeMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reminders_snooze_1w)) },
                        onClick = {
                            onSnooze(7)
                            showSnoozeMenu = false
                        }
                    )
                }
            }

            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggle
            )

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