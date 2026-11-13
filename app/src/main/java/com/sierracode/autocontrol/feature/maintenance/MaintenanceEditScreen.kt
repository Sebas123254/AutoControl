package com.sierracode.autocontrol.feature.maintenance

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.core.util.ImageUtils
import com.sierracode.autocontrol.data.local.entity.*
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.preferences.UnitSystem
import com.sierracode.autocontrol.data.repository.*
import com.sierracode.autocontrol.domain.calc.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class MaintenanceEditViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val vehicleRepo: VehicleRepository,
    private val scheduleRepo: ScheduleRepository,
    private val reminderRepo: ReminderRepository,
    private val settingsStore: SettingsStore,
    categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""

    val maintCategories = categoryRepo.observeAll()
        .map { list -> list.filter { it.group == "MAINT" || it.group == "REPAIR" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedCategory by mutableStateOf<CategoryEntity?>(null)
    var dateEpochDay by mutableStateOf(LocalDate.now().toEpochDay())
    var amount by mutableStateOf("")
    var odometer by mutableStateOf("")
    var workshop by mutableStateOf("")
    var nextOdometer by mutableStateOf("")
    var selectedImageUri by mutableStateOf<Uri?>(null)

    var hasUnsavedChanges by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            val unitSystem = settingsStore.unitSystem.first()
            val vehicle = vehicleRepo.observe(vehicleId).first()
            if (vehicle != null && odometer.isEmpty()) {
                odometer = if (unitSystem == UnitSystem.US) {
                    UnitConverter.kmToMiles(vehicle.odometer).roundToLong().toString()
                } else {
                    vehicle.odometer.toString()
                }
            }
        }
    }

    fun updateCategory(newCat: CategoryEntity) { selectedCategory = newCat; hasUnsavedChanges = true }
    fun updateDate(newDate: LocalDate) { dateEpochDay = newDate.toEpochDay(); hasUnsavedChanges = true }
    fun updateAmount(newAmount: String) { amount = newAmount; hasUnsavedChanges = true }
    fun updateOdometer(newOdo: String) { odometer = newOdo; hasUnsavedChanges = true }
    fun updateWorkshop(newWorkshop: String) { workshop = newWorkshop; hasUnsavedChanges = true }
    fun updateNextOdometer(newNextOdo: String) { nextOdometer = newNextOdo; hasUnsavedChanges = true }
    fun updateImage(newUri: Uri?) { selectedImageUri = newUri; hasUnsavedChanges = true }

    fun save(context: android.content.Context, unitSystem: UnitSystem, onDone: () -> Unit) {
        val amt = amount.toDoubleOrNull() ?: 0.0
        val odoInput = odometer.toDoubleOrNull()
        val nextOdoInput = nextOdometer.toDoubleOrNull()

        if (selectedCategory == null || amt <= 0) {
            error = context.getString(R.string.maint_edit_err_valid)
            return
        }

        val odoKm = if (odoInput != null) {
            if (unitSystem == UnitSystem.METRIC) odoInput.roundToLong() else UnitConverter.milesToKm(odoInput)
        } else null

        val nextOdoKm = if (nextOdoInput != null) {
            if (unitSystem == UnitSystem.METRIC) nextOdoInput.roundToLong() else UnitConverter.milesToKm(nextOdoInput)
        } else null

        if (odoKm != null && nextOdoKm != null && nextOdoKm <= odoKm) {
            error = "El próximo servicio debe ser mayor al odómetro actual."
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val expenseId = UUID.randomUUID().toString()
                val expense = ExpenseEntity(
                    id = expenseId,
                    vehicleId = vehicleId,
                    type = "MAINTENANCE",
                    categoryId = selectedCategory!!.id,
                    amountCents = (amt * 100).roundToLong(),
                    dateEpochDay = dateEpochDay,
                    odometer = odoKm,
                    description = selectedCategory!!.name
                )

                var attachment: AttachmentEntity? = null
                val currentUri = selectedImageUri
                if (currentUri != null) {
                    val compressedUri = ImageUtils.compressAndSave(context, currentUri)
                    if (compressedUri != null) {
                        val fileSize = compressedUri.path?.let { java.io.File(it).length() } ?: 0L
                        attachment = AttachmentEntity(
                            ownerType = "expense",
                            ownerId = expenseId,
                            uri = compressedUri.toString(),
                            mimeType = "image/jpeg",
                            bytes = fileSize
                        )
                    }
                }

                var updatedScheduleId: String? = null
                if (odoKm != null) {
                    val existingSchedule = scheduleRepo.getActiveByName(vehicleId, selectedCategory!!.name)

                    if (existingSchedule != null) {
                        val interval = if (nextOdoKm != null) (nextOdoKm - odoKm) else existingSchedule.intervalKm
                        val updatedSchedule = existingSchedule.copy(
                            lastOdometer = odoKm,
                            lastDateEpochDay = dateEpochDay,
                            intervalKm = interval
                        )
                        scheduleRepo.upsert(updatedSchedule)
                        updatedScheduleId = updatedSchedule.id
                    } else if (nextOdoKm != null) {
                        val newSchedule = MaintenanceScheduleEntity(
                            vehicleId = vehicleId,
                            name = selectedCategory!!.name,
                            intervalKm = nextOdoKm - odoKm,
                            lastOdometer = odoKm,
                            lastDateEpochDay = dateEpochDay
                        )
                        scheduleRepo.upsert(newSchedule)
                        updatedScheduleId = newSchedule.id
                    }
                }

                val detail = MaintenanceDetailEntity(
                    expenseId = expenseId,
                    scheduleId = updatedScheduleId,
                    serviceName = selectedCategory!!.name,
                    workshop = workshop.takeIf { it.isNotBlank() },
                    nextOdometer = nextOdoKm
                )

                expenseRepo.saveWithDetails(expense, null, detail, attachment)

                if (updatedScheduleId != null) {
                    val triggerMillis = LocalDate.ofEpochDay(dateEpochDay).plusMonths(1)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()

                    val reminder = ReminderEntity(
                        vehicleId = vehicleId,
                        sourceType = "MAINTENANCE",
                        sourceId = updatedScheduleId,
                        title = "Mantenimiento: ${selectedCategory!!.name}",
                        body = "Tu vehículo podría necesitar este servicio pronto.",
                        triggerAtEpochMillis = triggerMillis
                    )
                    reminderRepo.upsert(reminder)
                }

                if (odoKm != null && odoKm > 0) {
                    vehicleRepo.bumpOdometerIfHigher(vehicleId, odoKm)
                }

                hasUnsavedChanges = false
                onDone()
            } catch (e: Exception) {
                error = context.getString(R.string.expense_edit_err_generic, e.message ?: "")
            } finally {
                isLoading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceEditScreen(
    vehicleId: String,
    onDone: () -> Unit,
    vm: MaintenanceEditViewModel = hiltViewModel()
) {
    val config = LocalAppConfig.current
    val categories by vm.maintCategories.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(categories) {
        if (vm.selectedCategory == null && categories.isNotEmpty()) {
            vm.selectedCategory = categories.firstOrNull { it.id == "maint_general" } ?: categories.first()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) vm.updateImage(cameraUri)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) vm.updateImage(uri)
    }

    BackHandler(enabled = vm.hasUnsavedChanges) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDone()
                }) { Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.action_keep_editing)) }
            }
        )
    }

    if (showDatePicker) {
        val initialMillis = Instant.ofEpochSecond(vm.dateEpochDay * 86400).toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        vm.updateDate(date)
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }

    val unitLabel = if (config.unitSystem == UnitSystem.METRIC) "km" else "mi"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maint_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (vm.hasUnsavedChanges) showDiscardDialog = true else onDone()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = vm.selectedCategory?.name ?: stringResource(R.string.maint_edit_select_type),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.maint_edit_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                vm.updateCategory(category)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Campo Fecha
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formatDate(vm.dateEpochDay),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Fecha del servicio *") },
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha") },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
            }

            OutlinedTextField(
                value = vm.amount,
                onValueChange = { vm.updateAmount(it) },
                label = { Text(stringResource(R.string.maint_edit_cost)) },
                suffix = { Text(config.currencyCode) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.odometer,
                onValueChange = { vm.updateOdometer(it) },
                label = { Text(stringResource(R.string.fuel_edit_odometer)) },
                suffix = { Text(unitLabel) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.workshop,
                onValueChange = { vm.updateWorkshop(it) },
                label = { Text(stringResource(R.string.maint_edit_workshop)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.nextOdometer,
                onValueChange = { vm.updateNextOdometer(it) },
                label = { Text(stringResource(R.string.maint_edit_next_odo)) },
                suffix = { Text(unitLabel) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )

            // Botones Cámara / Galería
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val uri = ImageUtils.createTempImageUri(context)
                        if (uri != null) {
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (vm.selectedImageUri == null) "Tomar Foto" else "Repetir Foto", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Galería", maxLines = 1)
                }
            }

            // Vista previa con botón para eliminar
            if (vm.selectedImageUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    AsyncImage(
                        model = vm.selectedImageUri,
                        contentDescription = stringResource(R.string.expense_edit_receipt_desc),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { vm.updateImage(null) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Eliminar foto",
                            tint = Color.White
                        )
                    }
                }
            }

            vm.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { vm.save(context, config.unitSystem, onDone) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.maint_edit_save))
                }
            }
        }
    }
}