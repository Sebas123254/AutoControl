package com.sierracode.autocontrol.feature.documents

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.util.ImageUtils
import com.sierracode.autocontrol.data.local.entity.DocumentEntity
import com.sierracode.autocontrol.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DocumentEditViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""
    private val documentId: String? = savedStateHandle.get<String>("documentId")?.takeIf { it.isNotBlank() }

    val name: StateFlow<String> = savedStateHandle.getStateFlow("name", "")
    val type: StateFlow<String> = savedStateHandle.getStateFlow("type", "")
    val leadDays: StateFlow<String> = savedStateHandle.getStateFlow("leadDays", "30")
    val notes: StateFlow<String> = savedStateHandle.getStateFlow("notes", "")
    val existingFileUri: StateFlow<String?> = savedStateHandle.getStateFlow("existingFileUri", null)

    val selectedFileUri: StateFlow<Uri?> = savedStateHandle
        .getStateFlow<String?>("selectedFileUriString", null)
        .map { it?.let { Uri.parse(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val issueDate: StateFlow<LocalDate?> = savedStateHandle
        .getStateFlow<Long?>("issueDateEpochDay", null)
        .map { it?.let { LocalDate.ofEpochDay(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val expiryDate: StateFlow<LocalDate?> = savedStateHandle
        .getStateFlow<Long?>("expiryDateEpochDay", null)
        .map { it?.let { LocalDate.ofEpochDay(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasUnsavedChanges: StateFlow<Boolean> = savedStateHandle.getStateFlow("hasUnsavedChanges", false)

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        if (documentId != null && !savedStateHandle.contains("loadedFromDb")) {
            viewModelScope.launch {
                repo.get(documentId)?.let { doc ->
                    savedStateHandle["name"] = doc.name
                    savedStateHandle["type"] = doc.type
                    savedStateHandle["issueDateEpochDay"] = doc.issueDateEpochDay
                    savedStateHandle["expiryDateEpochDay"] = doc.expiryDateEpochDay
                    savedStateHandle["leadDays"] = doc.reminderLeadDays.toString()
                    savedStateHandle["notes"] = doc.notes ?: ""
                    savedStateHandle["existingFileUri"] = doc.fileUri
                }
                savedStateHandle["loadedFromDb"] = true
            }
        }
    }

    fun updateName(v: String) { savedStateHandle["name"] = v; markDirty(); error = null }
    fun updateType(v: String) { savedStateHandle["type"] = v; markDirty(); error = null }
    fun updateIssueDate(d: LocalDate?) { savedStateHandle["issueDateEpochDay"] = d?.toEpochDay(); markDirty(); error = null }
    fun updateExpiryDate(d: LocalDate?) { savedStateHandle["expiryDateEpochDay"] = d?.toEpochDay(); markDirty(); error = null }
    fun updateLeadDays(v: String) { savedStateHandle["leadDays"] = v; markDirty(); error = null }
    fun updateNotes(v: String) { savedStateHandle["notes"] = v; markDirty() }
    fun updateFileUri(uri: Uri?) { savedStateHandle["selectedFileUriString"] = uri?.toString(); markDirty() }
    private fun markDirty() { savedStateHandle["hasUnsavedChanges"] = true }

    fun save(onDone: () -> Unit) {
        val currentName = name.value
        val currentType = type.value
        val currentExpiry = expiryDate.value
        val currentIssue = issueDate.value

        if (currentName.isBlank() || currentExpiry == null) {
            error = context.getString(R.string.doc_edit_err_required); return
        }
        if (currentType.isBlank()) {
            error = context.getString(R.string.doc_edit_err_type); return
        }

        val leadDaysInt = leadDays.value.toIntOrNull() ?: 30
        if (leadDaysInt < 1 || leadDaysInt > 90) {
            error = context.getString(R.string.doc_edit_err_days); return
        }

        if (currentIssue != null && currentExpiry.isBefore(currentIssue)) {
            error = context.getString(R.string.doc_edit_err_date); return
        }

        val currentSelectedFileUri = selectedFileUri.value
        val currentExistingFileUri = existingFileUri.value

        viewModelScope.launch {
            isLoading = true
            try {
                var finalFileUri = currentExistingFileUri

                if (currentSelectedFileUri != null) {
                    val mimeType = context.contentResolver.getType(currentSelectedFileUri)
                    ImageUtils.deleteLocalFile(context, currentExistingFileUri)
                    if (mimeType == "application/pdf") {
                        finalFileUri = ImageUtils.copyToInternalStorage(context, currentSelectedFileUri, "pdf")
                    } else {
                        finalFileUri = ImageUtils.compressAndSave(context, currentSelectedFileUri)
                    }
                }

                val doc = DocumentEntity(
                    id = documentId ?: UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    type = currentType,
                    name = currentName,
                    issueDateEpochDay = currentIssue?.toEpochDay(),
                    expiryDateEpochDay = currentExpiry.toEpochDay(),
                    reminderLeadDays = leadDaysInt,
                    fileUri = finalFileUri,
                    notes = notes.value.takeIf { it.isNotBlank() }
                )
                repo.save(doc)

                savedStateHandle["hasUnsavedChanges"] = false
                onDone()
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                error = com.sierracode.autocontrol.core.ui.UiError.from(e).asString(context)
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

    val name by vm.name.collectAsStateWithLifecycle()
    val type by vm.type.collectAsStateWithLifecycle()
    val leadDays by vm.leadDays.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val existingFileUri by vm.existingFileUri.collectAsStateWithLifecycle()
    val selectedFileUri by vm.selectedFileUri.collectAsStateWithLifecycle()
    val issueDate by vm.issueDate.collectAsStateWithLifecycle()
    val expiryDate by vm.expiryDate.collectAsStateWithLifecycle()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsStateWithLifecycle()

    var showExpiryPicker by remember { mutableStateOf(false) }
    var showIssuePicker by remember { mutableStateOf(false) }
    var expandedType by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { vm.save { onDone() } }

    BackHandler(enabled = hasUnsavedChanges) { showDiscardDialog = true }

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
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.action_keep_editing)) }
            }
        )
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) vm.updateFileUri(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doc_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (hasUnsavedChanges) showDiscardDialog = true else onDone() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { vm.updateName(it) },
                label = { Text(stringResource(R.string.doc_edit_name_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = !expandedType }) {
                OutlinedTextField(
                    value = if (type.isBlank()) "" else type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.doc_edit_type_label)) },
                    placeholder = { Text(stringResource(R.string.doc_edit_type_placeholder)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                    val types = listOf(
                        "SEGURO" to stringResource(R.string.doc_type_insurance),
                        "CIRCULACION" to stringResource(R.string.doc_type_circulation),
                        "VERIFICACION" to stringResource(R.string.doc_type_verification),
                        "FACTURA" to stringResource(R.string.doc_type_invoice),
                        "OTRO" to stringResource(R.string.doc_type_other)
                    )
                    types.forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { vm.updateType(id); expandedType = false })
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = issueDate?.let { formatDate(it.toEpochDay()) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.doc_edit_issue_date_label)) },
                    placeholder = { Text(stringResource(R.string.doc_edit_date_placeholder)) },
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.action_choose_date)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable { showIssuePicker = true })
            }

            if (showIssuePicker) {
                val initialMillis = (issueDate ?: LocalDate.now())
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                DatePickerDialog(
                    onDismissRequest = { showIssuePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let {
                                vm.updateIssueDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                            }
                            showIssuePicker = false
                        }) { Text(stringResource(R.string.action_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showIssuePicker = false }) { Text(stringResource(R.string.action_cancel)) }
                    }
                ) { DatePicker(state = state) }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = expiryDate?.let { formatDate(it.toEpochDay()) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.doc_edit_expiry_date_label)) },
                    placeholder = { Text(stringResource(R.string.doc_edit_date_placeholder)) },
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.action_choose_date)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable { showExpiryPicker = true })
            }

            if (showExpiryPicker) {
                val initialMillis = (expiryDate ?: LocalDate.now())
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                DatePickerDialog(
                    onDismissRequest = { showExpiryPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let {
                                vm.updateExpiryDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                            }
                            showExpiryPicker = false
                        }) { Text(stringResource(R.string.action_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExpiryPicker = false }) { Text(stringResource(R.string.action_cancel)) }
                    }
                ) { DatePicker(state = state) }
            }

            OutlinedTextField(
                value = leadDays,
                onValueChange = { vm.updateLeadDays(it) },
                label = { Text(stringResource(R.string.doc_edit_lead_days_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (selectedFileUri != null || existingFileUri != null) stringResource(R.string.doc_edit_change_file)
                    else stringResource(R.string.doc_edit_attach_file)
                )
            }

            if (selectedFileUri != null || existingFileUri != null) {
                Text(
                    stringResource(R.string.doc_edit_file_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { vm.updateNotes(it) },
                label = { Text(stringResource(R.string.field_notes)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                            expiryDate != null
                    if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.save { onDone() }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) CircularProgressIndicator(Modifier.size(24.dp))
                else Text(stringResource(R.string.action_save))
            }
        }
    }
}