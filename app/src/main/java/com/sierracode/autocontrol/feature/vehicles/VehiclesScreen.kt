package com.sierracode.autocontrol.feature.vehicles

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatKm
import com.sierracode.autocontrol.data.backup.BackupManager
import com.sierracode.autocontrol.data.backup.BackupStats
import com.sierracode.autocontrol.data.local.entity.VehicleEntity
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val repo: VehicleRepository,
    private val settings: SettingsStore,
    private val backupManager: BackupManager,
    private val orphanFileSweeper: com.sierracode.autocontrol.core.util.OrphanFileSweeper
) : ViewModel() {
    val vehicles = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isPro = settings.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    fun setActiveVehicle(id: String) = viewModelScope.launch { settings.setActiveVehicle(id) }
    fun setPro(value: Boolean) = viewModelScope.launch { settings.setPro(value) }

    fun deleteVehicles(ids: List<String>) = viewModelScope.launch {
        repo.deleteMultiple(ids)
        val active = settings.activeVehicleId.first()
        if (active != null && ids.contains(active)) {
            settings.setActiveVehicle(null)
        }
    }

    fun exportData(uri: Uri, onResult: (Result<BackupStats>) -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            val result = backupManager.exportToZip(uri)
            _isProcessing.value = false
            onResult(result)
        }
    }

    fun importData(uri: Uri, onResult: (Result<BackupStats>) -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            val result = backupManager.importFromZip(uri)
            if (result.isSuccess) {
                try {
                    orphanFileSweeper.sweep()
                } catch (e: Exception) {
                    android.util.Log.e("VehiclesViewModel", "Error barriendo huérfanos tras restaurar", e)
                }
            }
            _isProcessing.value = false
            onResult(result)
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
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val isPro by vm.isPro.collectAsStateWithLifecycle()
    val isProcessing by vm.isProcessing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showImportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedVehicles by remember { mutableStateOf(setOf<String>()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val proActivatedText = stringResource(R.string.vehicles_pro_activated)

    // Lanzador para Exportar (Crear archivo ZIP)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.exportData(uri) { result ->
            scope.launch {
                val msg = result.fold(
                    onSuccess = { stats ->
                        context.getString(R.string.vehicles_backup_success_full, stats.records, stats.images)
                    },
                    onFailure = { error ->
                        val uiError = com.sierracode.autocontrol.core.ui.UiError.from(error)
                        context.getString(R.string.vehicles_backup_error, uiError.asString(context))
                    }
                )
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    // Lanzador para Importar (Abrir archivo ZIP)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.importData(uri) { result ->
            scope.launch {
                val msg = result.fold(
                    onSuccess = { stats ->
                        context.getString(R.string.vehicles_restore_success_full, stats.records, stats.images)
                    },
                    onFailure = { error ->
                        val uiError = com.sierracode.autocontrol.core.ui.UiError.from(error)
                        context.getString(R.string.vehicles_restore_error, uiError.asString(context))
                    }
                )
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.vehicles_restore_title)) },
            text = { Text(stringResource(R.string.vehicles_restore_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    openDocumentLauncher.launch(arrayOf("application/zip", "application/json"))
                }) { Text(stringResource(R.string.action_yes_restore), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
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
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showProDialog) {
        AlertDialog(
            onDismissRequest = { showProDialog = false },
            icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            title = { Text(stringResource(R.string.vehicles_pro_title)) },
            text = { Text(stringResource(R.string.vehicles_pro_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showProDialog = false
                    vm.setPro(true)
                    scope.launch { snackbarHostState.showSnackbar(proActivatedText) }
                }) {
                    Text(stringResource(R.string.vehicles_pro_activate))
                }
            },
            dismissButton = {
                TextButton(onClick = { showProDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            Icon(
                                Icons.Default.Delete,
                                stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.vehicles_title), fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(
                            onClick = { showImportDialog = true },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.FolderOpen, stringResource(R.string.vehicles_cd_import))
                        }
                        IconButton(
                            onClick = { createDocumentLauncher.launch("AutoControl_Backup.zip") },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.Save, stringResource(R.string.vehicles_cd_export))
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.Settings, stringResource(R.string.vehicles_cd_settings))
                        }
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
        Box(Modifier.fillMaxSize()) {
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
                                    val newSelection = if (isSelected) selectedVehicles - vehicle.id
                                    else selectedVehicles + vehicle.id
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
                            },
                            onEditClick = { onEditClick(vehicle.id) }
                        )
                    }
                }
            }

            // Overlay de progreso (Cargando backup)
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.vehicles_backup_processing), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
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
    onLongClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${vehicle.brand} ${vehicle.model}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${vehicle.year} • ${vehicle.plates ?: stringResource(R.string.vehicles_no_plates)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(formatKm(vehicle.odometer), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = null)
            } else {
                IconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_edit_vehicle)
                    )
                }
            }
        }
    }
}