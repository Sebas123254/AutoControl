package com.sierracode.autocontrol.feature.expenses

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
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.core.ui.components.CurrencyField
import com.sierracode.autocontrol.core.util.ImageUtils
import com.sierracode.autocontrol.data.local.entity.AttachmentEntity
import com.sierracode.autocontrol.data.local.entity.CategoryEntity
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
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
import java.time.YearMonth
import java.time.ZoneId
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""
    private val expenseId: String? = savedStateHandle.get<String>("expenseId")?.takeIf { it.isNotBlank() }
    private val goalId: String? = savedStateHandle.get<String>("goalId")?.takeIf { it.isNotBlank() }

    val categories = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentMonthExpenses = expenseRepo.observeByVehicle(vehicleId).map { list ->
        val currentMonth = YearMonth.now()
        list.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == currentMonth }.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var amount by mutableStateOf("")
    var dateEpochDay by mutableStateOf(LocalDate.now().toEpochDay())
    var odometer by mutableStateOf("")
    var description by mutableStateOf("")
    var selectedCategory by mutableStateOf<CategoryEntity?>(null)
    var customCategoryName by mutableStateOf("")
    var selectedImageUri by mutableStateOf<Uri?>(null)

    var hasUnsavedChanges by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var warningMessage by mutableStateOf<String?>(null)
        private set

    val isAmountError = amount.isNotEmpty() && (amount.toDoubleOrNull() ?: 0.0) <= 0.0

    init {
        if (expenseId != null) {
            viewModelScope.launch {
                val catList = categories.first { it.isNotEmpty() }
                val existingExpense = expenseRepo.get(expenseId)
                if (existingExpense != null) {
                    amount = (existingExpense.amountCents / 100.0).toString()
                    dateEpochDay = existingExpense.dateEpochDay

                    val unitSystem = settingsStore.unitSystem.first()
                    odometer = existingExpense.odometer?.let { km ->
                        if (unitSystem == UnitSystem.US) UnitConverter.kmToMiles(km).roundToLong().toString() else km.toString()
                    } ?: ""

                    description = existingExpense.description ?: ""
                    customCategoryName = existingExpense.customCategoryName ?: ""
                    selectedCategory = catList.find { it.id == existingExpense.categoryId }

                    val oldAttachment = attachmentRepo.getForOwner("expense", expenseId)
                    if (oldAttachment != null) {
                        selectedImageUri = Uri.parse(oldAttachment.uri)
                    }
                }
            }
        } else {
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

            if (goalId != null) {
                viewModelScope.launch {
                    goalRepo.get(goalId)?.let { goal ->
                        amount = (goal.estimatedCents / 100.0).toString()
                        description = goal.title
                        if (goal.categoryId != null) {
                            val catList = categories.first { it.isNotEmpty() }
                            selectedCategory = catList.find { it.id == goal.categoryId }
                        }
                    }
                }
            }
        }
    }

    fun updateAmount(newAmount: String) { amount = newAmount; hasUnsavedChanges = true; error = null }
    fun updateDate(newDate: LocalDate) { dateEpochDay = newDate.toEpochDay(); hasUnsavedChanges = true }
    fun updateOdometer(newOdo: String) { odometer = newOdo; hasUnsavedChanges = true }
    fun updateDescription(newDesc: String) { description = newDesc; hasUnsavedChanges = true }
    fun updateCustomCategoryName(newName: String) {
        customCategoryName = newName.take(40)
        hasUnsavedChanges = true
        error = null
    }
    fun updateCategory(newCat: CategoryEntity) {
        selectedCategory = newCat
        if (newCat.id != "other_misc") {
            customCategoryName = ""
        }
        hasUnsavedChanges = true
        error = null
    }
    fun updateImage(newUri: Uri?) { selectedImageUri = newUri; hasUnsavedChanges = true }
    fun clearWarning() { warningMessage = null }

    fun save(context: android.content.Context, unitSystem: UnitSystem, onDone: () -> Unit) {
        val amountDouble = amount.toDoubleOrNull() ?: 0.0
        val amountCents = (amountDouble * 100).roundToLong()
        val odoInput = odometer.toDoubleOrNull()

        if (amount.isEmpty() || amountCents <= 0) {
            error = context.getString(R.string.expense_edit_err_amount)
            return
        }
        if (amountDouble > 1_000_000.0) {
            error = "El monto excede el límite permitido."
            return
        }
        if (selectedCategory == null) {
            error = context.getString(R.string.expense_edit_err_category)
            return
        }
        if (selectedCategory!!.id == "other_misc" && customCategoryName.isBlank()) {
            error = context.getString(R.string.expense_custom_category_err)
            return
        }

        val odoKm = if (odoInput != null) {
            if (unitSystem == UnitSystem.METRIC) odoInput.roundToLong() else UnitConverter.milesToKm(odoInput)
        } else null

        viewModelScope.launch {
            isLoading = true
            try {
                val finalExpenseId = expenseId ?: java.util.UUID.randomUUID().toString()
                val existingExpense = if (expenseId != null) expenseRepo.get(expenseId) else null

                val expense = ExpenseEntity(
                    id = finalExpenseId,
                    vehicleId = vehicleId,
                    type = selectedCategory!!.group,
                    categoryId = selectedCategory!!.id,
                    amountCents = amountCents,
                    dateEpochDay = dateEpochDay,
                    odometer = odoKm,
                    description = description.takeIf { it.isNotBlank() },
                    customCategoryName = customCategoryName.takeIf { it.isNotBlank() }
                )

                var attachment: AttachmentEntity? = null
                val currentUri = selectedImageUri

                if (currentUri != null) {
                    if (currentUri.scheme != "file") {
                        val compressedUri = ImageUtils.compressAndSave(context, currentUri)
                        if (compressedUri != null) {
                            if (expenseId != null) {
                                val oldAtt = attachmentRepo.getForOwner("expense", expenseId)
                                if (oldAtt != null) {
                                    ImageUtils.deleteLocalFile(oldAtt.uri)
                                    attachmentRepo.deleteById(oldAtt.id)
                                }
                            }
                            val fileSize = compressedUri.path?.let { java.io.File(it).length() } ?: 0L
                            attachment = AttachmentEntity(
                                ownerType = "expense",
                                ownerId = finalExpenseId,
                                uri = compressedUri.toString(),
                                mimeType = "image/jpeg",
                                bytes = fileSize
                            )
                        } else {
                            warningMessage = "El recibo no se pudo procesar, pero el gasto fue guardado."
                        }
                    }
                }

                expenseRepo.saveWithDetails(expense, null, null, attachment)

                if (goalId != null && expenseId == null) {
                    goalRepo.markCompleted(goalId, finalExpenseId)
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
fun ExpenseEditScreen(
    vehicleId: String,
    expenseId: String?,
    goalId: String? = null,
    onDone: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: ExpenseEditViewModel = hiltViewModel()
) {
    val config = LocalAppConfig.current
    val categories by vm.categories.collectAsState()
    val currentMonthExpenses by vm.currentMonthExpenses.collectAsState()

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
    ) { success ->
        if (success) vm.updateImage(cameraUri)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) vm.updateImage(uri)
    }

    LaunchedEffect(vm.warningMessage) {
        vm.warningMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearWarning()
        }
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

    if (showProLimitDialog) {
        AlertDialog(
            onDismissRequest = { showProLimitDialog = false },
            icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            title = { Text("Límite alcanzado") },
            text = { Text("Has alcanzado el límite de 50 gastos al mes en la versión gratuita.\n\nActualiza a PRO para registrar gastos ilimitados.") },
            confirmButton = {
                TextButton(onClick = {
                    showProLimitDialog = false
                    onOpenSettings()
                }) { Text("Ver opciones") }
            },
            dismissButton = {
                TextButton(onClick = { showProLimitDialog = false }) { Text("Cancelar") }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (expenseId == null) R.string.expense_edit_new else R.string.expense_edit_update)) },
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
                    value = vm.selectedCategory?.name ?: stringResource(R.string.expense_edit_select_category),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.expense_edit_category)) },
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

            if (vm.selectedCategory?.id == "other_misc") {
                OutlinedTextField(
                    value = vm.customCategoryName,
                    onValueChange = { vm.updateCustomCategoryName(it) },
                    label = { Text(stringResource(R.string.expense_custom_category_label)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
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

            // CAMBIO: CurrencyField para el monto
            CurrencyField(
                value = vm.amount,
                onValueChange = { vm.updateAmount(it) },
                label = stringResource(R.string.expense_edit_amount),
                currencyCode = config.currencyCode,
                isError = vm.isAmountError,
                errorMessage = if (vm.isAmountError) "El monto debe ser mayor a 0" else null,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                modifier = Modifier.fillMaxWidth()
            )

            // Campo Fecha
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formatDate(vm.dateEpochDay),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Fecha del gasto *") },
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
                value = vm.odometer,
                onValueChange = { vm.updateOdometer(it) },
                label = { Text(stringResource(R.string.expense_edit_odometer)) },
                suffix = { Text(unitLabel) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.description,
                onValueChange = { vm.updateDescription(it) },
                label = { Text(stringResource(R.string.expense_edit_desc)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
                if (it != context.getString(R.string.expense_custom_category_err)) {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }

            Button(
                onClick = {
                    if (expenseId == null && !config.isPro && currentMonthExpenses >= 50) {
                        showProLimitDialog = true
                    } else {
                        vm.save(context, config.unitSystem, onDone)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text(stringResource(R.string.expense_edit_save))
            }
        }
    }
}