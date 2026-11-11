package com.sierracode.autocontrol.feature.documents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.data.local.entity.DocumentEntity
import com.sierracode.autocontrol.data.repository.DocumentRepository
import com.sierracode.autocontrol.domain.calc.DocumentStatus
import com.sierracode.autocontrol.domain.calc.evaluateDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val repo: DocumentRepository
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    val documents = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeByVehicle(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadVehicle(id: String) {
        vehicleIdFlow.value = id
    }

    fun deleteDocuments(ids: List<String>) = viewModelScope.launch { repo.deleteMultiple(ids) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    vehicleId: String,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (String) -> Unit,
    vm: DocumentsViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) {
        vm.loadVehicle(vehicleId)
    }

    val docs by vm.documents.collectAsState()
    val today = LocalDate.now().toEpochDay()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedDocs by remember { mutableStateOf(setOf<String>()) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar ${selectedDocs.size} documento(s)") },
            text = { Text("¿Estás seguro que deseas eliminar los documentos seleccionados?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDocuments(selectedDocs.toList())
                    isSelectionMode = false
                    selectedDocs = emptySet()
                    showDeleteDialog = false
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedDocs.size} seleccionados") },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedDocs = emptySet()
                        }) { Icon(Icons.Default.Close, "Cancelar") }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Borrar", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                TopAppBar(
                    title = { Text("Documentos") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.DirectionsCar, "Mis Vehículos")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = onAddClick) { Icon(Icons.Default.Add, "Agregar") }
            }
        }
    ) { padding ->
        if (docs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No hay documentos registrados.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(docs, key = { it.id }) { doc ->
                    val expiry = doc.expiryDateEpochDay ?: today
                    val eval = evaluateDocument(expiry, today, doc.reminderLeadDays)
                    val isSelected = selectedDocs.contains(doc.id)

                    val color = when(eval.status) {
                        DocumentStatus.EXPIRED -> Color(0xFFC0392B)
                        DocumentStatus.EXPIRING_SOON -> Color(0xFFE6A23C)
                        DocumentStatus.VALID -> Color(0xFF2FA39A)
                    }
                    val statusText = when(eval.status) {
                        DocumentStatus.EXPIRED -> "Venció hace ${-eval.daysRemaining} días"
                        DocumentStatus.EXPIRING_SOON -> "Vence en ${eval.daysRemaining} días"
                        DocumentStatus.VALID -> "Vigente"
                    }

                    DocumentCard(
                        doc = doc,
                        color = color,
                        statusText = statusText,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                val newSelection = if (isSelected) selectedDocs - doc.id else selectedDocs + doc.id
                                selectedDocs = newSelection
                                if (newSelection.isEmpty()) isSelectionMode = false
                            } else {
                                onEditClick(doc.id)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedDocs = setOf(doc.id)
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
fun DocumentCard(
    doc: DocumentEntity,
    color: Color,
    statusText: String,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = color)
            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(doc.name, fontWeight = FontWeight.Bold)
                Text("Vence: ${formatDate(doc.expiryDateEpochDay)}", style = MaterialTheme.typography.bodySmall)
                Text(statusText, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            }

            if (isSelectionMode) {
                // AQUÍ ESTÁ LA CORRECCIÓN: onCheckedChange en lugar de onClick
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            }
        }
    }
}