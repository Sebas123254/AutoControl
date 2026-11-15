package com.sierracode.autocontrol.feature.maintenance

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.localizedCategoryName
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.core.ui.components.CurrencyField
import com.sierracode.autocontrol.core.util.ImageUtils
import com.sierracode.autocontrol.data.local.entity.*
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.preferences.UnitSystem
import com.sierracode.autocontrol.data.repository.*
import com.sierracode.autocontrol.domain.calc.UnitConverter
import com.sierracode.autocontrol.domain.reminder.ReminderResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""

    val maintCategories: StateFlow<List<CategoryEntity>> = categoryRepo.observeAll()
        .map { list -> list.filter { it.group == "MAINT" || it.group == "REPAIR" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedCategoryId: StateFlow<String?> = savedStateHandle.getStateFlow("categoryId", null)
    val selectedCategory: StateFlow<CategoryEntity?> = combine(selectedCategoryId, maintCategories) { id, cats ->
        cats.find { it.id == id }
            ?: cats.firstOrNull { it.id == "maint_general" }
            ?: cats.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val dateEpochDay: StateFlow<Long> = savedStateHandle.getStateFlow("dateEpochDay", LocalDate.now().toEpochDay())
    val amount: StateFlow<String> = savedStateHandle.getStateFlow("amount", "")
    val odometer: StateFlow<String> = savedStateHandle.getStateFlow("odometer", "")
    val workshop: StateFlow<String> = savedStateHandle.getStateFlow("workshop", "")
    val nextOdometer: StateFlow<String> = savedStateHandle.getStateFlow("nextOdometer", "")
    val selectedImageUri: StateFlow<Uri?> = savedStateHandle.getStateFlow<String?>("imageUriString", null)
        .map { it?.let { Uri.parse(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val hasUnsavedChanges: StateFlow<Boolean> = savedStateHandle.getStateFlow("hasUnsavedChanges", false)

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        if (!savedStateHandle.contains("loadedFromDb")) {
            viewModelScope.launch {
                val unitSystem = settingsStore.unitSystem.first()
                val vehicle = vehicleRepo.observe(vehicleId).first()
                if (vehicle != null && odometer.value.isBlank()) {
                    savedStateHandle["odometer"] = if (unitSystem == UnitSystem.US) {
                        UnitConverter.kmToMiles(vehicle.odometer).roundToLong().toString()
                    } else {
                        vehicle.odometer.toString()
                    }
                }
                savedStateHandle["loadedFromDb"] = true
            }
        }
    }

    fun updateCategory(cat: CategoryEntity) { savedStateHandle["categoryId"] = cat.id; markDirty() }
    fun updateDate(d: LocalDate) { savedStateHandle["dateEpochDay"] = d.toEpochDay(); markDirty() }
    fun updateAmount(v: String) { savedStateHandle["amount"] = v; markDirty() }
    fun updateOdometer(v: String) { savedStateHandle["odometer"] = v; markDirty() }
    fun updateWorkshop(v: String) { savedStateHandle["workshop"] = v; markDirty() }
    fun updateNextOdometer(v: String) { savedStateHandle["nextOdometer"] = v; markDirty() }
    fun updateImage(uri: Uri?) { savedStateHandle["imageUriString"] = uri?.toString(); markDirty() }
    private fun markDirty() { savedStateHandle["hasUnsavedChanges"] = true }

    fun save(context: android.content.Context, unitSystem: UnitSystem, onDone: () -> Unit) {
        val amt = amount.value.toDoubleOrNull() ?: 0.0
        val odoInput = odometer.value.toDoubleOrNull()
        val nextOdoInput = nextOdometer.value.toDoubleOrNull()
        val currentCategory = selectedCategory.value

        if (currentCategory == null || amt <= 0) {
            error = context.getString(R.string.maint_edit_err_valid); return
        }

        val odoKm = if (odoInput != null) {
            if (unitSystem == UnitSystem.METRIC) odoInput.roundToLong() else UnitConverter.milesToKm(odoInput)
        } else null

        val nextOdoKm = if (nextOdoInput != null) {
            if (unitSystem == UnitSystem.METRIC) nextOdoInput.roundToLong() else UnitConverter.milesToKm(nextOdoInput)
        } else null

        if (odoKm != null && nextOdoKm != null && nextOdoKm <= odoKm) {
            error = context.getString(R.string.maint_edit_err_odo); return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val expenseId = UUID.randomUUID().toString()
                val expense = ExpenseEntity(
                    id = expenseId,
                    vehicleId = vehicleId,
                    type = "MAINTENANCE",
                    categoryId = currentCategory.id,
                    amountCents = (amt * 100).roundToLong(),
                    dateEpochDay = dateEpochDay.value,
                    odometer = odoKm,
                    description = currentCategory.name
                )

                var attachment: AttachmentEntity? = null
                val currentUri = selectedImageUri.value
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
                var finalSchedule: MaintenanceScheduleEntity? = null

                if (odoKm != null) {
                    val existingSchedule = scheduleRepo.getActiveByName(vehicleId, currentCategory.name)
                    if (existingSchedule != null) {
                        val interval = if (nextOdoKm != null) (nextOdoKm - odoKm) else existingSchedule.intervalKm
                        val updated = existingSchedule.copy(
                            lastOdometer = odoKm,
                            lastDateEpochDay = dateEpochDay.value,
                            intervalKm = interval
                        )
                        scheduleRepo.upsert(updated)
                        updatedScheduleId = updated.id
                        finalSchedule = updated
                    } else if (nextOdoKm != null) {
                        val newSchedule = MaintenanceScheduleEntity(
                            vehicleId = vehicleId,
                            name = currentCategory.name,
                            intervalKm = nextOdoKm - odoKm,
                            lastOdometer = odoKm,
                            lastDateEpochDay = dateEpochDay.value
                        )
                        scheduleRepo.upsert(newSchedule)
                        updatedScheduleId = newSchedule.id
                        finalSchedule = newSchedule
                    }
                }

                val detail = MaintenanceDetailEntity(
                    expenseId = expenseId,
                    scheduleId = updatedScheduleId,
                    serviceName = currentCategory.name,
                    workshop = workshop.value.takeIf { it.isNotBlank() },
                    nextOdometer = nextOdoKm
                )

                expenseRepo.saveWithDetails(expense, null, detail, attachment)

                if (finalSchedule != null) {
                    val remindDays = finalSchedule.remindBeforeDays.toLong()
                    val intervalMonths = finalSchedule.intervalMonths?.toLong() ?: 6L
                    val nextServiceDate = LocalDate.ofEpochDay(dateEpochDay.value).plusMonths(intervalMonths)
                    val triggerDate = nextServiceDate.minusDays(remindDays)
                    val triggerMillis = triggerDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    val reminder = ReminderEntity(
                        vehicleId = vehicleId,
                        sourceType = "MAINTENANCE",
                        sourceId = finalSchedule.id,
                        title = "Mantenimiento: ${currentCategory.name}",
                        body = "Tu vehículo podría necesitar este servicio pronto.",
                        triggerAtEpochMillis = triggerMillis,
                        templateKey = "maintenance_due",
                        templateArgs = ReminderResolver.encodeMaintenanceDueArgs(currentCategory.name)
                    )
                    reminderRepo.upsert(reminder)
                }

                if (odoKm != null && odoKm > 0) vehicleRepo.bumpOdometerIfHigher(vehicleId, odoKm)

                savedStateHandle["hasUnsavedChanges"] = false
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
    val categories by vm.maintCategories.collectAsStateWithLifecycle()

    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val dateEpochDay by vm.dateEpochDay.collectAsStateWithLifecycle()
    val amount by vm.amount.collectAsStateWithLifecycle()
    val odometer by vm.odometer.collectAsStateWithLifecycle()
    val workshop by vm.workshop.collectAsStateWithLifecycle()
    val nextOdometer by vm.nextOdometer.collectAsStateWithLifecycle()
    val selectedImageUri by vm.selectedImageUri.collectAsStateWithLifecycle()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) vm.updateImage(cameraUri) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) vm.updateImage(uri) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { vm.save(context, config.unitSystem, onDone) }

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

    if (showDatePicker) {
        // Fix: UTC end-to-end.
        val initialMillis = LocalDate.ofEpochDay(dateEpochDay)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        vm.updateDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = state) }
    }

    val unitLabel = if (config.unitSystem == UnitSystem.METRIC) "km" else "mi"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maint_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) showDiscardDialog = true else onDone()
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
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = selectedCategory?.let { localizedCategoryName(it.id, it.name) }
                        ?: stringResource(R.string.maint_edit_select_type),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.maint_edit_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(localizedCategoryName(category.id, category.name)) },
                            onClick = { vm.updateCategory(category); expanded = false }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formatDate(dateEpochDay),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.maint_edit_fecha_servicio)) },
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.action_choose_date)) },
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

            CurrencyField(
                value = amount,
                onValueChange = { vm.updateAmount(it) },
                label = stringResource(R.string.maint_edit_cost),
                currencyCode = config.currencyCode,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = odometer,
                onValueChange = { vm.updateOdometer(it) },
                label = { Text(stringResource(R.string.fuel_edit_odometer)) },
                suffix = { Text(unitLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = workshop,
                onValueChange = { vm.updateWorkshop(it) },
                label = { Text(stringResource(R.string.maint_edit_workshop)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = nextOdometer,
                onValueChange = { vm.updateNextOdometer(it) },
                label = { Text(stringResource(R.string.maint_edit_next_odo)) },
                suffix = { Text(unitLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val uri = ImageUtils.createTempImageUri(context)
                        if (uri != null) { cameraUri = uri; cameraLauncher.launch(uri) }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (selectedImageUri == null) stringResource(R.string.expense_edit_camera) else stringResource(R.string.expense_edit_retake),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.expense_edit_gallery),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (selectedImageUri != null) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 250.dp)) {
                    AsyncImage(
                        model = selectedImageUri,
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
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.expense_edit_delete_photo), tint = Color.White)
                    }
                }
            }

            vm.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val willCreateReminder = odometer.isNotBlank()
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                    if (willCreateReminder && needsPermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.save(context, config.unitSystem, onDone)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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