package com.sierracode.autocontrol.feature.maintenance

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
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
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.core.util.ImageUtils
import com.sierracode.autocontrol.data.local.entity.AttachmentEntity
import com.sierracode.autocontrol.data.local.entity.CategoryEntity
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import com.sierracode.autocontrol.data.local.entity.MaintenanceDetailEntity
import com.sierracode.autocontrol.data.local.entity.MaintenanceScheduleEntity
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
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class MaintenanceEditViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val vehicleRepo: VehicleRepository,
    private val scheduleRepo: ScheduleRepository,
    private val settingsStore: SettingsStore, // <-- INYECTADO
    categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""

    val maintCategories = categoryRepo.observeAll()
        .map { list -> list.filter { it.group == "MAINT" || it.group == "REPAIR" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedCategory by mutableStateOf<CategoryEntity?>(null)
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
                    dateEpochDay = LocalDate.now().toEpochDay(),
                    odometer = odoKm,
                    description = selectedCategory!!.name
                )

                val detail = MaintenanceDetailEntity(
                    expenseId = expenseId,
                    serviceName = selectedCategory!!.name,
                    workshop = workshop.takeIf { it.isNotBlank() },
                    nextOdometer = nextOdoKm
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

                expenseRepo.saveWithDetails(expense, null, detail, attachment)

                if (odoKm != null) {
                    val existingSchedule = scheduleRepo.getActiveByName(vehicleId, selectedCategory!!.name)

                    if (existingSchedule != null) {
                        val interval = if (nextOdoKm != null) (nextOdoKm - odoKm) else existingSchedule.intervalKm
                        val updatedSchedule = existingSchedule.copy(
                            lastOdometer = odoKm,
                            lastDateEpochDay = LocalDate.now().toEpochDay(),
                            intervalKm = interval
                        )
                        scheduleRepo.upsert(updatedSchedule)
                    } else if (nextOdoKm != null) {
                        val newSchedule = MaintenanceScheduleEntity(
                            vehicleId = vehicleId,
                            name = selectedCategory!!.name,
                            intervalKm = nextOdoKm - odoKm,
                            lastOdometer = odoKm,
                            lastDateEpochDay = LocalDate.now().toEpochDay()
                        )
                        scheduleRepo.upsert(newSchedule)
                    }
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
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(categories) {
        if (vm.selectedCategory == null && categories.isNotEmpty()) {
            vm.selectedCategory = categories.firstOrNull { it.id == "maint_general" } ?: categories.first()
        }
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

            OutlinedButton(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (vm.selectedImageUri == null) R.string.maint_edit_receipt else R.string.maint_edit_receipt_change))
            }

            if (vm.selectedImageUri != null) {
                AsyncImage(
                    model = vm.selectedImageUri,
                    contentDescription = stringResource(R.string.expense_edit_receipt_desc),
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop
                )
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