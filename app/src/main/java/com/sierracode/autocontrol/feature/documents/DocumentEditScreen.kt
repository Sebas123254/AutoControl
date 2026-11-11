package com.sierracode.autocontrol.feature.documents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.data.local.entity.DocumentEntity
import com.sierracode.autocontrol.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DocumentEditViewModel @Inject constructor(
    private val repo: DocumentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""
    private val documentId: String? = savedStateHandle["documentId"]

    var name by mutableStateOf("")
    var type by mutableStateOf("SEGURO")
    var expiryDate by mutableStateOf<LocalDate?>(null)
    var leadDays by mutableStateOf("30")
    var notes by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        if (documentId != null) {
            viewModelScope.launch {
                repo.get(documentId)?.let { doc ->
                    name = doc.name
                    type = doc.type
                    // Cargamos la fecha (si existe)
                    expiryDate = doc.expiryDateEpochDay?.let { LocalDate.ofEpochDay(it) }
                    leadDays = doc.reminderLeadDays.toString()
                    notes = doc.notes ?: ""
                }
            }
        }
    }

    fun save(onDone: () -> Unit) {
        if (name.isBlank() || expiryDate == null) {
            error = "Nombre y fecha de vencimiento son obligatorios"
            return
        }
        viewModelScope.launch {
            isLoading = true
            try {
                val doc = DocumentEntity(
                    id = documentId ?: UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    type = type,
                    name = name,
                    expiryDateEpochDay = expiryDate!!.toEpochDay(),
                    reminderLeadDays = leadDays.toIntOrNull() ?: 30,
                    notes = notes.takeIf { it.isNotBlank() }
                )
                repo.save(doc)
                onDone()
            } catch (e: Exception) {
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditScreen(
    onDone: () -> Unit,
    vm: DocumentEditViewModel = hiltViewModel()
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Documento") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.name = it },
                label = { Text("Nombre (Ej. Póliza GNP) *") },
                modifier = Modifier.fillMaxWidth()
            )

            // CAMPO DE FECHA MEJORADO
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = vm.expiryDate?.let { formatDate(it.toEpochDay()) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Fecha de Vencimiento *") },
                    placeholder = { Text("Toca para elegir fecha") },
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Elegir") },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                // Capa invisible sobre el campo para detectar el clic
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker = true }
                )
            }

            if (showDatePicker) {
                // El calendario inicia en la fecha seleccionada o en la de hoy
                val initialMillis = vm.expiryDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    ?: System.currentTimeMillis()

                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let {
                                vm.expiryDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                            }
                            showDatePicker = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancelar")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            OutlinedTextField(
                value = vm.leadDays,
                onValueChange = { vm.leadDays = it },
                label = { Text("Días de anticipación para avisar") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.notes,
                onValueChange = { vm.notes = it },
                label = { Text("Notas (Opcional)") },
                modifier = Modifier.fillMaxWidth()
            )

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { vm.save(onDone) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Guardar")
            }
        }
    }
}