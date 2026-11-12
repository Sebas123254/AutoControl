package com.sierracode.autocontrol.feature.vehicles

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatKm
import com.sierracode.autocontrol.data.backup.BackupManager
import com.sierracode.autocontrol.data.local.entity.VehicleEntity
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val repo: VehicleRepository,
    private val settings: SettingsStore,
    private val backupManager: BackupManager
) : ViewModel() {
    val vehicles = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isPro = settings.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setActiveVehicle(id: String) = viewModelScope.launch { settings.setActiveVehicle(id) }

    fun deleteVehicles(ids: List<String>) = viewModelScope.launch {
        repo.deleteMultiple(ids)
        val active = settings.activeVehicleId.first()
        if (active != null && ids.contains(active)) {
            settings.setActiveVehicle(null)
        }
    }

    suspend fun getActiveVehicleId(): String? {
        val activeId = settings.activeVehicleId.first() ?: return null
        val exists = vehicles.value.any { it.id == activeId }
        return if (exists) activeId else null
    }

    fun exportData(uri: Uri, context: android.content.Context, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { backupManager.exportToJson(it) }
                onResult(true)
            } catch (e: Exception) { onResult(false) }
        }
    }

    fun importData(uri: Uri, context: android.content.Context, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    val result = backupManager.importFromJson(it)
                    onResult(result.isSuccess)
                }
            } catch (e: Exception) { onResult(false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    onAddClick: () -> Unit,
    onVehicleClick: (String) -> Unit,
    onEditClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: VehiclesViewModel = hiltViewModel()
) {
    val vehicles by vm.vehicles.collectAsState()
    val isPro by vm.isPro.collectAsState()
    val context = LocalContext.current

    var hasRedirected by remember { mutableStateOf(false) }
    LaunchedEffect(vehicles) {
        if (!hasRedirected && vehicles.isNotEmpty()) {
            val activeId = vm.getActiveVehicleId()
            if (activeId != null) {
                hasRedirected = true
                onVehicleClick(activeId)
            }
        }
    }

    var showImportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedVehicles by remember { mutableStateOf(setOf<String>()) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let { vm.exportData(it, context) { } } }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { vm.importData(it, context) { } } }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.vehicles_restore_title)) },
            text = { Text(stringResource(R.string.vehicles_restore_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    openDocumentLauncher.launch(arrayOf("application/json"))
                }) { Text(stringResource(R.string.action_yes_restore), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showImportDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.vehicles_delete_title, selectedVehicles.size)) },
            text = { Text(stringResource(R.string.vehicles_delete_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteVehicles(selectedVehicles.toList())
                    isSelectionMode = false
                    selectedVehicles = emptySet()
                    showDeleteDialog = false
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showProDialog) {
        AlertDialog(
            onDismissRequest = { showProDialog = false },
            icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            title = { Text(stringResource(R.string.vehicles_pro_title)) },
            text = { Text(stringResource(R.string.vehicles_pro_desc)) },
            confirmButton = { TextButton(onClick = { showProDialog = false }) { Text(stringResource(R.string.action_understood)) } }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.vehicles_selected, selectedVehicles.size)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedVehicles = emptySet()
                        }) { Icon(Icons.Default.Close, stringResource(R.string.vehicles_cancel_selection)) }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.vehicles_title), fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showImportDialog = true }) { Icon(Icons.Default.FolderOpen, stringResource(R.string.vehicles_cd_import)) }
                        IconButton(onClick = { createDocumentLauncher.launch("AutoControl_Backup.json") }) { Icon(Icons.Default.Save, stringResource(R.string.vehicles_cd_export)) }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, stringResource(R.string.vehicles_cd_settings)) }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = {
                    if (!isPro && vehicles.isNotEmpty()) showProDialog = true else onAddClick()
                }) { Icon(Icons.Default.Add, stringResource(R.string.vehicles_cd_add)) }
            }
        }
    ) { padding ->
        if (vehicles.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.vehicles_empty_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.vehicles_empty_subtitle), textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onAddClick) { Text(stringResource(R.string.vehicles_add_first)) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vehicles, key = { it.id }) { vehicle ->
                    val isSelected = selectedVehicles.contains(vehicle.id)
                    VehicleCard(
                        vehicle = vehicle,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                val newSelection = if (isSelected) selectedVehicles - vehicle.id else selectedVehicles + vehicle.id
                                selectedVehicles = newSelection
                                if (newSelection.isEmpty()) isSelectionMode = false
                            } else {
                                vm.setActiveVehicle(vehicle.id)
                                onVehicleClick(vehicle.id)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedVehicles = setOf(vehicle.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VehicleCard(
    vehicle: VehicleEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${vehicle.year} • ${vehicle.plates ?: stringResource(R.string.vehicles_no_plates)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatKm(vehicle.odometer), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            }
        }
    }
}