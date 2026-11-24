package com.sierracode.autocontrol.feature.fuel

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
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.sierracode.autocontrol.data.local.entity.FuelDetailEntity
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.preferences.UnitSystem
import com.sierracode.autocontrol.data.repository.CategoryRepository
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import com.sierracode.autocontrol.data.repository.VehicleRepository
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
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class FuelEditViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val vehicleRepo: VehicleRepository,
    private val settingsStore: SettingsStore,
    categoryRepo: CategoryRepository,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""

    val fuelCategories: StateFlow<List<CategoryEntity>> = categoryRepo.observeAll()
        .map { list -> list.filter { it.group == "FUEL" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedCategoryId: StateFlow<String?> = savedStateHandle.getStateFlow("categoryId", null)
    val selectedCategory: StateFlow<CategoryEntity?> = combine(selectedCategoryId, fuelCategories) { id, cats ->
        cats.find { it.id == id } ?: cats.firstOrNull { it.id == "fuel_gas" } ?: cats.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val dateEpochDay: StateFlow<Long> = savedStateHandle.getStateFlow("dateEpochDay", LocalDate.now().toEpochDay())
    val odometer: StateFlow<String> = savedStateHandle.getStateFlow("odometer", "")
    val liters: StateFlow<String> = savedStateHandle.getStateFlow("liters", "")
    val pricePerLiter: StateFlow<String> = savedStateHandle.getStateFlow("pricePerLiter", "")
    val total: StateFlow<String> = savedStateHandle.getStateFlow("total", "")
    val station: StateFlow<String> = savedStateHandle.getStateFlow("station", "")
    val isFullTank: StateFlow<Boolean> = savedStateHandle.getStateFlow("isFullTank", true)
    val selectedImageUri: StateFlow<Uri?> = savedStateHandle.getStateFlow<String?>("imageUriString", null)
        .map { it?.let { Uri.parse(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val hasUnsavedChanges: StateFlow<Boolean> = savedStateHandle.getStateFlow("hasUnsavedChanges", false)

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var warningMessage by mutableStateOf<String?>(null)
        private set

    init {
        if (!savedStateHandle.contains("loadedFromDb")) {
            viewModelScope.launch {
                val unitSystem = settingsStore.unitSystem.first()
                val vehicle = vehicleRepo.observe(vehicleId).first()
                if (vehicle != null) {
                    if (odometer.value.isBlank()) {
                        savedStateHandle["odometer"] = if (unitSystem == UnitSystem.US) {
                            UnitConverter.kmToMiles(vehicle.odometer).roundToLong().toString()
                        } else {
                            vehicle.odometer.toString()
                        }
                    }
                    if (savedStateHandle.get<String?>("categoryId") == null) {
                        val defaultCatId = when (vehicle.fuelType) {
                            "GASOLINA" -> "fuel_gas"
                            "DIESEL" -> "fuel_diesel"
                            "GAS_LP" -> "fuel_gas_lp"
                            "ELECTRICO" -> "fuel_ev"
                            else -> "fuel_gas"
                        }
                        savedStateHandle["categoryId"] = defaultCatId
                    }
                }
                savedStateHandle["loadedFromDb"] = true
            }
        }
    }

    fun updateCategory(cat: CategoryEntity) { savedStateHandle["categoryId"] = cat.id; markDirty() }
    fun updateDate(d: LocalDate) { savedStateHandle["dateEpochDay"] = d.toEpochDay(); markDirty() }
    fun updateOdometer(v: String) { savedStateHandle["odometer"] = v; markDirty() }
    fun updateLiters(v: String) { savedStateHandle["liters"] = v; onLitersOrPriceChange(); markDirty() }
    fun updatePrice(v: String) { savedStateHandle["pricePerLiter"] = v; onLitersOrPriceChange(); markDirty() }
    fun updateTotal(v: String) { savedStateHandle["total"] = v; markDirty() }
    fun updateStation(v: String) { savedStateHandle["station"] = v; markDirty() }
    fun updateFullTank(v: Boolean) { savedStateHandle["isFullTank"] = v; markDirty() }
    fun updateImage(uri: Uri?) { savedStateHandle["imageUriString"] = uri?.toString(); markDirty() }
    fun clearWarning() { warningMessage = null }
    private fun markDirty() { savedStateHandle["hasUnsavedChanges"] = true }

    private fun onLitersOrPriceChange() {
        val l = liters.value.toDoubleOrNull()
        val p = pricePerLiter.value.toDoubleOrNull()
        if (l != null && p != null) {
            savedStateHandle["total"] = String.format(Locale.US, "%.2f", l * p)
        }
    }

    fun save(unitSystem: UnitSystem, onDone: () -> Unit) {
        val lInput = liters.value.toDoubleOrNull() ?: 0.0
        val tInput = total.value.toDoubleOrNull() ?: 0.0
        val pInput = pricePerLiter.value.toDoubleOrNull() ?: 0.0
        val odoInput = odometer.value.toDoubleOrNull()
        val currentCategory = selectedCategory.value

        if (tInput <= 0 || lInput <= 0) {
            error = context.getString(R.string.fuel_edit_err_valid); return
        }
        if (currentCategory == null) {
            error = context.getString(R.string.fuel_edit_err_type); return
        }

        val odoKm = if (odoInput != null) {
            if (unitSystem == UnitSystem.METRIC) odoInput.roundToLong() else UnitConverter.milesToKm(odoInput)
        } else null

        val litersMilli = if (unitSystem == UnitSystem.METRIC) (lInput * 1000).roundToLong()
        else UnitConverter.gallonsToLitersMilli(lInput)

        val pricePerLiterDouble = if (unitSystem == UnitSystem.METRIC) pInput
        else pInput / UnitConverter.LITERS_PER_GALLON
        val pricePerLiterCents = (pricePerLiterDouble * 100).roundToLong()

        viewModelScope.launch {
            isLoading = true
            try {
                val expenseId = UUID.randomUUID().toString()
                val expense = ExpenseEntity(
                    id = expenseId,
                    vehicleId = vehicleId,
                    type = "FUEL",
                    categoryId = currentCategory.id,
                    amountCents = (tInput * 100).roundToLong(),
                    dateEpochDay = dateEpochDay.value,
                    odometer = odoKm,
                    description = context.getString(R.string.fuel_edit_desc, currentCategory.name.lowercase())
                )

                val fuelDetail = FuelDetailEntity(
                    expenseId = expenseId,
                    litersMilli = litersMilli,
                    pricePerLiterCents = pricePerLiterCents,
                    fuelType = currentCategory.name.uppercase(),
                    station = station.value.takeIf { it.isNotBlank() },
                    fullTank = isFullTank.value
                )

                var attachment: AttachmentEntity? = null
                val currentUri = selectedImageUri.value
                if (currentUri != null) {
                    val compressedFileName = ImageUtils.compressAndSave(context, currentUri)
                    if (compressedFileName != null) {
                        val fileSize = FileUtils.getFile(context, compressedFileName)?.length() ?: 0L
                        attachment = AttachmentEntity(
                            ownerType = "expense",
                            ownerId = expenseId,
                            uri = compressedFileName,
                            mimeType = "image/jpeg",
                            bytes = fileSize
                        )
                    } else {
                        warningMessage = context.getString(R.string.fuel_edit_err_image)
                    }
                }

                expenseRepo.saveWithDetails(expense, fuelDetail, null, attachment)

                if (odoKm != null && odoKm > 0) vehicleRepo.bumpOdometerIfHigher(vehicleId, odoKm)

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
fun FuelEditScreen(
    vehicleId: String,
    onDone: () -> Unit,
    vm: FuelEditViewModel = hiltViewModel()
) {
    val config = LocalAppConfig.current
    val categories by vm.fuelCategories.collectAsStateWithLifecycle()

    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val dateEpochDay by vm.dateEpochDay.collectAsStateWithLifecycle()
    val odometer by vm.odometer.collectAsStateWithLifecycle()
    val liters by vm.liters.collectAsStateWithLifecycle()
    val pricePerLiter by vm.pricePerLiter.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val station by vm.station.collectAsStateWithLifecycle()
    val isFullTank by vm.isFullTank.collectAsStateWithLifecycle()
    val selectedImageUri by vm.selectedImageUri.collectAsStateWithLifecycle()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
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
            snackbarHostState.showSnackbar(it); vm.clearWarning()
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

    if (showDatePicker) {
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
    val volumeLabel = if (config.unitSystem == UnitSystem.METRIC) "L" else "gal"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fuel_edit_title)) },
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
                        ?: stringResource(R.string.fuel_edit_select_type),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.fuel_edit_type)) },
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
                    label = { Text(stringResource(R.string.fuel_edit_fecha_carga)) },
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
                label = { Text(stringResource(R.string.fuel_edit_odometer)) },
                suffix = { Text(unitLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = liters,
                    onValueChange = { vm.updateLiters(it) },
                    label = { Text(stringResource(R.string.fuel_edit_liters)) },
                    suffix = { Text(volumeLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = pricePerLiter,
                    onValueChange = { vm.updatePrice(it) },
                    label = { Text(stringResource(R.string.fuel_edit_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.weight(1f)
                )
            }

            CurrencyField(
                value = total,
                onValueChange = { vm.updateTotal(it) },
                label = stringResource(R.string.fuel_edit_total),
                currencyCode = config.currencyCode,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = station,
                onValueChange = { vm.updateStation(it) },
                label = { Text(stringResource(R.string.fuel_edit_station)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = isFullTank, onCheckedChange = { vm.updateFullTank(it) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fuel_edit_full_tank))
            }

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
                onClick = { vm.save(config.unitSystem) { onDone() } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.fuel_edit_save))
                }
            }
        }
    }
}