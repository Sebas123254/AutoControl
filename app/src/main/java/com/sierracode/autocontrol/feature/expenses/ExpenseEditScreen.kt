package com.sierracode.autocontrol.feature.expenses

import android.content.Context
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
import androidx.compose.material.icons.filled.Star
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.localizedCategoryName
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.core.ui.components.CurrencyField
import com.sierracode.autocontrol.core.util.FileUtils
import com.sierracode.autocontrol.core.util.ImageUtils
import com.sierracode.autocontrol.data.local.entity.AttachmentEntity
import com.sierracode.autocontrol.data.local.entity.CategoryEntity
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.preferences.UnitSystem
import com.sierracode.autocontrol.data.repository.*
import com.sierracode.autocontrol.domain.calc.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class ExpenseEditViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val vehicleRepo: VehicleRepository,
    private val attachmentRepo: AttachmentRepository,
    private val goalRepo: GoalRepository,
    private val settingsStore: SettingsStore,
    categoryRepo: CategoryRepository,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""
    private val expenseId: String? = savedStateHandle.get<String>("expenseId")?.takeIf { it.isNotBlank() }
    private val goalId: String? = savedStateHandle.get<String>("goalId")?.takeIf { it.isNotBlank() }

    val categories: StateFlow<List<CategoryEntity>> = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentMonthExpenses: StateFlow<Int> = expenseRepo.observeByVehicle(vehicleId).map { list ->
        val currentMonth = YearMonth.now()
        list.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == currentMonth }.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val amount: StateFlow<String> = savedStateHandle.getStateFlow("amount", "")
    val dateEpochDay: StateFlow<Long> = savedStateHandle.getStateFlow("dateEpochDay", LocalDate.now().toEpochDay())
    val odometer: StateFlow<String> = savedStateHandle.getStateFlow("odometer", "")
    val description: StateFlow<String> = savedStateHandle.getStateFlow("description", "")
    val customCategoryName: StateFlow<String> = savedStateHandle.getStateFlow("customCategoryName", "")
    val selectedCategoryId: StateFlow<String?> = savedStateHandle.getStateFlow("categoryId", null)

    val selectedCategory: StateFlow<CategoryEntity?> = combine(
        selectedCategoryId,
        categories
    ) { catId, cats -> cats.find { it.id == catId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selectedImageUri: StateFlow<Uri?> = savedStateHandle
        .getStateFlow<String?>("imageUriString", null)
        .map { it?.let { Uri.parse(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasUnsavedChanges: StateFlow<Boolean> = savedStateHandle.getStateFlow("hasUnsavedChanges", false)

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var warningMessage by mutableStateOf<String?>(null)
        private set

    val isAmountError: Boolean get() = amount.value.isNotEmpty() && (amount.value.toDoubleOrNull() ?: 0.0) <= 0.0

    init {
        if (expenseId != null && !savedStateHandle.contains("loadedFromDb")) {
            viewModelScope.launch {
                val catList = categories.first { it.isNotEmpty() }
                val existing = expenseRepo.get(expenseId)
                if (existing != null) {
                    savedStateHandle["amount"] = (existing.amountCents / 100.0).toString()
                    savedStateHandle["dateEpochDay"] = existing.dateEpochDay

                    val unitSystem = settingsStore.unitSystem.first()
                    savedStateHandle["odometer"] = existing.odometer?.let { km ->
                        if (unitSystem == UnitSystem.US) UnitConverter.kmToMiles(km).roundToLong().toString() else km.toString()
                    } ?: ""

                    savedStateHandle["description"] = existing.description ?: ""
                    savedStateHandle["customCategoryName"] = existing.customCategoryName ?: ""
                    savedStateHandle["categoryId"] = catList.find { it.id == existing.categoryId }?.id
                        ?: existing.categoryId

                    val oldAttachment = attachmentRepo.getForOwner("expense", expenseId)
                    if (oldAttachment != null) {
                        savedStateHandle["imageUriString"] = FileUtils.getContentUri(context, oldAttachment.uri)?.toString()
                    }
                }
                savedStateHandle["loadedFromDb"] = true
            }
        } else if (expenseId == null && !savedStateHandle.contains("loadedFromDb")) {
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

                if (goalId != null) {
                    goalRepo.get(goalId)?.let { goal ->
                        savedStateHandle["amount"] = (goal.estimatedCents / 100.0).toString()
                        savedStateHandle["description"] = goal.title
                        if (goal.categoryId != null) {
                            savedStateHandle["categoryId"] = goal.categoryId
                        }
                    }
                }
                savedStateHandle["loadedFromDb"] = true
            }
        }
    }

    fun updateAmount(v: String) { savedStateHandle["amount"] = v; markDirty(); error = null }
    fun updateDate(d: LocalDate) { savedStateHandle["dateEpochDay"] = d.toEpochDay(); markDirty() }
    fun updateOdometer(v: String) { savedStateHandle["odometer"] = v; markDirty() }
    fun updateDescription(v: String) { savedStateHandle["description"] = v; markDirty() }
    fun updateCustomCategoryName(v: String) {
        savedStateHandle["customCategoryName"] = v.take(40)
        markDirty(); error = null
    }
    fun updateCategory(cat: CategoryEntity) {
        savedStateHandle["categoryId"] = cat.id
        if (cat.id != "other_misc") savedStateHandle["customCategoryName"] = ""
        markDirty(); error = null
    }
    fun updateImage(uri: Uri?) {
        savedStateHandle["imageUriString"] = uri?.toString()
        markDirty()
    }
    fun clearWarning() { warningMessage = null }
    private fun markDirty() { savedStateHandle["hasUnsavedChanges"] = true }

    fun save(unitSystem: UnitSystem, onDone: () -> Unit) {
        val amt = amount.value.toDoubleOrNull() ?: 0.0
        val amountCents = (amt * 100).roundToLong()
        val odoInput = odometer.value.toDoubleOrNull()
        val currentCategory = selectedCategory.value
        val currentCustomName = customCategoryName.value

        if (amount.value.isEmpty() || amountCents <= 0) {
            error = context.getString(R.string.expense_edit_err_amount); return
        }
        if (amt > 1_000_000.0) {
            error = context.getString(R.string.expense_edit_err_high); return
        }
        if (currentCategory == null) {
            error = context.getString(R.string.expense_edit_err_category); return
        }
        if (currentCategory.id == "other_misc" && currentCustomName.isBlank()) {
            error = context.getString(R.string.expense_custom_category_err); return
        }

        val odoKm = if (odoInput != null) {
            if (unitSystem == UnitSystem.METRIC) odoInput.roundToLong() else UnitConverter.milesToKm(odoInput)
        } else null

        viewModelScope.launch {
            isLoading = true
            try {
                val finalExpenseId = expenseId ?: java.util.UUID.randomUUID().toString()

                val expense = ExpenseEntity(
                    id = finalExpenseId,
                    vehicleId = vehicleId,
                    type = currentCategory.group,
                    categoryId = currentCategory.id,
                    amountCents = amountCents,
                    dateEpochDay = dateEpochDay.value,
                    odometer = odoKm,
                    description = description.value.takeIf { it.isNotBlank() },
                    customCategoryName = currentCustomName.takeIf { it.isNotBlank() }
                )

                var attachment: AttachmentEntity? = null
                val currentUri = selectedImageUri.value

                if (currentUri != null && currentUri.scheme != "file") {
                    val compressedFileName = ImageUtils.compressAndSave(context, currentUri)
                    if (compressedFileName != null) {
                        if (expenseId != null) {
                            val oldAtt = attachmentRepo.getForOwner("expense", expenseId)
                            if (oldAtt != null) {
                                ImageUtils.deleteLocalFile(context, oldAtt.uri)
                                attachmentRepo.deleteById(oldAtt.id)
                            }
                        }
                        val fileSize = FileUtils.getFile(context, compressedFileName)?.length() ?: 0L
                        attachment = AttachmentEntity(
                            ownerType = "expense",
                            ownerId = finalExpenseId,
                            uri = compressedFileName,
                            mimeType = "image/jpeg",
                            bytes = fileSize
                        )
                    } else {
                        warningMessage = context.getString(R.string.expense_edit_err_image)
                    }
                } else if (currentUri == null && expenseId != null) {
                    val oldAtt = attachmentRepo.getForOwner("expense", expenseId)
                    if (oldAtt != null) {
                        ImageUtils.deleteLocalFile(context, oldAtt.uri)
                        attachmentRepo.deleteById(oldAtt.id)
                    }
                }

                expenseRepo.saveWithDetails(expense, null, null, attachment)

                if (goalId != null && expenseId == null) {
                    goalRepo.markCompleted(goalId, finalExpenseId)
                }
                if (odoKm != null && odoKm > 0) {
                    vehicleRepo.bumpOdometerIfHigher(vehicleId, odoKm)
                }

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
fun ExpenseEditScreen(
    vehicleId: String,
    expenseId: String?,
    goalId: String? = null,
    onDone: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: ExpenseEditViewModel = hiltViewModel()
) {
    val config = LocalAppConfig.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val currentMonthExpenses by vm.currentMonthExpenses.collectAsStateWithLifecycle()

    val amount by vm.amount.collectAsStateWithLifecycle()
    val dateEpochDay by vm.dateEpochDay.collectAsStateWithLifecycle()
    val odometer by vm.odometer.collectAsStateWithLifecycle()
    val description by vm.description.collectAsStateWithLifecycle()
    val customCategoryName by vm.customCategoryName.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val selectedImageUri by vm.selectedImageUri.collectAsStateWithLifecycle()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showProLimitDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) vm.updateImage(cameraUri) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) vm.updateImage(uri) }

    LaunchedEffect(vm.warningMessage) {
        vm.warningMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearWarning()
        }
    }

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

    if (showProLimitDialog) {
        AlertDialog(
            onDismissRequest = { showProLimitDialog = false },
            icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            title = { Text(stringResource(R.string.expense_edit_limit_title)) },
            text = { Text(stringResource(R.string.expense_edit_limit_desc)) },
            confirmButton = {
                TextButton(onClick = { showProLimitDialog = false; onOpenSettings() }) {
                    Text(stringResource(R.string.expense_edit_limit_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showProLimitDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showDatePicker) {
        val initialMillis = LocalDate.ofEpochDay(dateEpochDay)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.updateDate(date)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(state = state) }
    }

    val unitLabel = if (config.unitSystem == UnitSystem.METRIC) "km" else "mi"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (expenseId == null) R.string.expense_edit_new else R.string.expense_edit_update)) },
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
                        ?: stringResource(R.string.expense_edit_select_category),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.expense_edit_category)) },
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

            if (selectedCategory?.id == "other_misc") {
                OutlinedTextField(
                    value = customCategoryName,
                    onValueChange = { vm.updateCustomCategoryName(it) },
                    label = { Text(stringResource(R.string.expense_custom_category_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    isError = vm.error == context.getString(R.string.expense_custom_category_err),
                    supportingText = {
                        if (vm.error == context.getString(R.string.expense_custom_category_err)) {
                            Text(context.getString(R.string.expense_custom_category_err))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            CurrencyField(
                value = amount,
                onValueChange = { vm.updateAmount(it) },
                label = stringResource(R.string.expense_edit_amount),
                currencyCode = config.currencyCode,
                isError = vm.isAmountError,
                errorMessage = if (vm.isAmountError) stringResource(R.string.expense_amount_err) else null,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                modifier = Modifier.fillMaxWidth()
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formatDate(dateEpochDay),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.expense_edit_date)) },
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

            OutlinedTextField(
                value = odometer,
                onValueChange = { vm.updateOdometer(it) },
                label = { Text(stringResource(R.string.expense_edit_odometer)) },
                suffix = { Text(unitLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { vm.updateDescription(it) },
                label = { Text(stringResource(R.string.expense_edit_desc)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
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

            vm.error?.let {
                if (it != context.getString(R.string.expense_custom_category_err)) {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }

            Button(
                onClick = {
                    if (expenseId == null && !config.isPro && currentMonthExpenses >= 50) {
                        showProLimitDialog = true
                    } else {
                        vm.save(config.unitSystem) { onDone() }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Text(stringResource(R.string.expense_edit_save))
            }
        }
    }
}