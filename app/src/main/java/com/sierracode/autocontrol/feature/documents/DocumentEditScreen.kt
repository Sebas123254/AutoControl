package com.sierracode.autocontrol.feature.documents

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.util.ImageUtils
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
    private val documentId: String? = savedStateHandle.get<String>("documentId")?.takeIf { it.isNotBlank() }

    var name by mutableStateOf("")
    var type by mutableStateOf("")
    var issueDate by mutableStateOf<LocalDate?>(null)
    var expiryDate by mutableStateOf<LocalDate?>(null)
    var leadDays by mutableStateOf("30")
    var notes by mutableStateOf("")
    var existingFileUri by mutableStateOf<String?>(null)
    var selectedFileUri by mutableStateOf<Uri?>(null)

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var hasUnsavedChanges by mutableStateOf(false)
        private set

    fun updateName(newName: String) { name = newName; hasUnsavedChanges = true; error = null }
    fun updateType(newType: String) { type = newType; hasUnsavedChanges = true; error = null }
    fun updateIssueDate(newDate: LocalDate?) { issueDate = newDate; hasUnsavedChanges = true; error = null }
    fun updateExpiryDate(newDate: LocalDate?) { expiryDate = newDate; hasUnsavedChanges = true; error = null }
    fun updateLeadDays(newDays: String) { leadDays = newDays; hasUnsavedChanges = true; error = null }
    fun updateNotes(newNotes: String) { notes = newNotes; hasUnsavedChanges = true }
    fun updateFileUri(newUri: Uri?) { selectedFileUri = newUri; hasUnsavedChanges = true }

    init {
        if (documentId != null) {
            viewModelScope.launch {
                repo.get(documentId)?.let { doc ->
                    name = doc.name
                    type = doc.type
                    issueDate = doc.issueDateEpochDay?.let { LocalDate.ofEpochDay(it) }
                    expiryDate = doc.expiryDateEpochDay?.let { LocalDate.ofEpochDay(it) }
                    leadDays = doc.reminderLeadDays.toString()
                    notes = doc.notes ?: ""
                    existingFileUri = doc.fileUri
                }
            }
        }
    }

    fun save(context: android.content.Context, onDone: () -> Unit) {
        if (name.isBlank() || expiryDate == null) {
            error = "Nombre y fecha de vencimiento son obligatorios"
            return
        }
        if (type.isBlank()) {
            error = "Selecciona un tipo de documento"
            return
        }

        val leadDaysInt = leadDays.toIntOrNull() ?: 30
        if (leadDaysInt < 1 || leadDaysInt > 90) {
            error = "Los días de aviso deben estar entre 1 y 90."
            return
        }

        if (issueDate != null && expiryDate!!.isBefore(issueDate)) {
            error = "La fecha de vencimiento no puede ser menor a la de expedición."
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                var finalFileUri = existingFileUri

                if (selectedFileUri != null) {
                    val mimeType = context.contentResolver.getType(selectedFileUri!!)
                    ImageUtils.deleteLocalFile(existingFileUri)
                    if (mimeType == "application/pdf") {
                        finalFileUri = ImageUtils.copyToInternalStorage(context, selectedFileUri!!, "pdf")?.toString()
                    } else {
                        finalFileUri = ImageUtils.compressAndSave(context, selectedFileUri!!)?.toString()
                    }
                }

                val doc = DocumentEntity(
                    id = documentId ?: UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    type = type,
                    name = name,
                    issueDateEpochDay = issueDate?.toEpochDay(),
                    expiryDateEpochDay = expiryDate!!.toEpochDay(),
                    reminderLeadDays = leadDaysInt,
                    fileUri = finalFileUri,
                    notes = notes.takeIf { it.isNotBlank() }
                )
                repo.save(doc)

                hasUnsavedChanges = false
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
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showExpiryPicker by remember { mutableStateOf(false) }
    var showIssuePicker by remember { mutableStateOf(false) }
    var expandedType by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { vm.save(context, onDone) }

    BackHandler(enabled = vm.hasUnsavedChanges) { showDiscardDialog = true }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_msg)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onDone() }) {
                    Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.action_keep_editing)) } }
        )
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) vm.updateFileUri(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documento") },
                navigationIcon = {
                    IconButton(onClick = { if (vm.hasUnsavedChanges) showDiscardDialog = true else onDone() }) {
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
                onValueChange = { vm.updateName(it) },
                label = { Text("Nombre (Ej. Póliza GNP) *") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = !expandedType }) {
                OutlinedTextField(
                    value = if (vm.type.isBlank()) "" else vm.type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo de documento *") },
                    placeholder = { Text("Selecciona un tipo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                    listOf("SEGURO", "CIRCULACION", "VERIFICACION", "FACTURA", "OTRO").forEach { t ->
                        DropdownMenuItem(text = { Text(t) }, onClick = { vm.updateType(t); expandedType = false })
                    }
                }
            }

            // CAMBIO: Fecha expedición accesible (readOnly en lugar de enabled = false)
            OutlinedTextField(
                value = vm.issueDate?.let { formatDate(it.toEpochDay()) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Fecha de Expedición (Opcional)") },
                placeholder = { Text("Toca para elegir fecha") },
                trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Elegir") },
                modifier = Modifier.fillMaxWidth().clickable { showIssuePicker = true }
            )

            if (showIssuePicker) {
                val initialMillis = vm.issueDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
                val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                DatePickerDialog(
                    onDismissRequest = { showIssuePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let { vm.updateIssueDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }
                            showIssuePicker = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = { TextButton(onClick = { showIssuePicker = false }) { Text("Cancelar") } }
                ) { DatePicker(state = state) }
            }

            // CAMBIO: Fecha vencimiento accesible
            OutlinedTextField(
                value = vm.expiryDate?.let { formatDate(it.toEpochDay()) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Fecha de Vencimiento *") },
                placeholder = { Text("Toca para elegir fecha") },
                trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Elegir") },
                modifier = Modifier.fillMaxWidth().clickable { showExpiryPicker = true }
            )

            if (showExpiryPicker) {
                val initialMillis = vm.expiryDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
                val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                DatePickerDialog(
                    onDismissRequest = { showExpiryPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let { vm.updateExpiryDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }
                            showExpiryPicker = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = { TextButton(onClick = { showExpiryPicker = false }) { Text("Cancelar") } }
                ) { DatePicker(state = state) }
            }

            OutlinedTextField(
                value = vm.leadDays,
                onValueChange = { vm.updateLeadDays(it) },
                label = { Text("Días de anticipación para avisar (1-90)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (vm.selectedFileUri != null || vm.existingFileUri != null) "Cambiar archivo adjunto" else "Adjuntar archivo (Opcional)")
            }

            if (vm.selectedFileUri != null || vm.existingFileUri != null) {
                Text("Archivo seleccionado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            OutlinedTextField(
                value = vm.notes,
                onValueChange = { vm.updateNotes(it) },
                label = { Text("Notas (Opcional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                            vm.expiryDate != null
                    if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.save(context, onDone)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Guardar")
            }
        }
    }
}