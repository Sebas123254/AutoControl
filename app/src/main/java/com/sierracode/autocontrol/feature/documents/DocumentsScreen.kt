package com.sierracode.autocontrol.feature.documents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.ui.components.StatusChip
import com.sierracode.autocontrol.core.ui.components.StatusVariant
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

    fun loadVehicle(id: String) { vehicleIdFlow.value = id }

    fun deleteDocument(id: String) = viewModelScope.launch { repo.delete(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    vehicleId: String,
    vehicleName: String,
    onAddClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onDocumentClick: (String) -> Unit,
    vm: DocumentsViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) { vm.loadVehicle(vehicleId) }

    val docs by vm.documents.collectAsState()
    val today = LocalDate.now().toEpochDay()

    var docToDelete by remember { mutableStateOf<DocumentEntity?>(null) }
    var docMenuTarget by remember { mutableStateOf<DocumentEntity?>(null) }

    if (docToDelete != null) {
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("Eliminar documento") },
            text = { Text("¿Estás seguro que deseas eliminar '${docToDelete!!.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDocument(docToDelete!!.id)
                    docToDelete = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { docToDelete = null }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.documents_title), fontWeight = FontWeight.Bold)
                        if (vehicleName.isNotBlank()) {
                            Text(
                                vehicleName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) { Icon(Icons.Default.Add, "Agregar") }
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

                    val variant: StatusVariant
                    val statusIcon: ImageVector
                    val statusText: String
                    when (eval.status) {
                        DocumentStatus.EXPIRED -> {
                            variant = StatusVariant.ERROR
                            statusIcon = Icons.Default.Warning
                            statusText = "Venció hace ${-eval.daysRemaining} días"
                        }
                        DocumentStatus.EXPIRING_SOON -> {
                            variant = StatusVariant.WARNING
                            statusIcon = Icons.Default.Info
                            statusText = "Vence en ${eval.daysRemaining} días"
                        }
                        DocumentStatus.VALID -> {
                            variant = StatusVariant.OK
                            statusIcon = Icons.Default.CheckCircle
                            statusText = "Vigente"
                        }
                    }

                    DocumentCard(
                        doc = doc,
                        variant = variant,
                        statusIcon = statusIcon,
                        statusText = statusText,
                        onClick = { onDocumentClick(doc.id) },
                        onLongClick = { docMenuTarget = doc }
                    )
                }
            }
        }
    }

    if (docMenuTarget != null) {
        AlertDialog(
            onDismissRequest = { docMenuTarget = null },
            title = { Text(docMenuTarget!!.name) },
            text = { Text("¿Qué deseas hacer con este documento?") },
            confirmButton = {
                TextButton(onClick = {
                    onEditClick(docMenuTarget!!.id)
                    docMenuTarget = null
                }) { Text("Editar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        docToDelete = docMenuTarget
                        docMenuTarget = null
                    }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { docMenuTarget = null }) { Text("Cancelar") }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    doc: DocumentEntity,
    variant: StatusVariant,
    statusIcon: ImageVector,
    statusText: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(doc.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusChip(text = statusText, icon = statusIcon, variant = variant)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Vence: ${formatDate(doc.expiryDateEpochDay)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}