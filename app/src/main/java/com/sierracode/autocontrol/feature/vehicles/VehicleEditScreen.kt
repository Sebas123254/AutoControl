package com.sierracode.autocontrol.feature.vehicles

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.ui.components.CurrencyField
import com.sierracode.autocontrol.core.ui.components.VehicleAvatar
import com.sierracode.autocontrol.core.util.ImageUtils
import com.sierracode.autocontrol.data.local.entity.VehicleEntity
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.repository.VehicleRepository
import com.sierracode.autocontrol.domain.calc.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class VehicleEditViewModel @Inject constructor(
    private val repo: VehicleRepository,
    private val settingsStore: SettingsStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String? = savedStateHandle.get<String>("vehicleId")?.takeIf { it.isNotBlank() }

    val photoUri: StateFlow<String?> = savedStateHandle.getStateFlow("photoUri", null)
    val brand: StateFlow<String> = savedStateHandle.getStateFlow("brand", "")
    val model: StateFlow<String> = savedStateHandle.getStateFlow("model", "")
    val year: StateFlow<String> = savedStateHandle.getStateFlow("year", "")
    val plates: StateFlow<String> = savedStateHandle.getStateFlow("plates", "")
    val odometer: StateFlow<String> = savedStateHandle.getStateFlow("odometer", "")
    val purchasePrice: StateFlow<String> = savedStateHandle.getStateFlow("purchasePrice", "")
    val currencyCode: StateFlow<String> = savedStateHandle.getStateFlow("currencyCode", "MXN")
    val unitSystem: StateFlow<String> = savedStateHandle.getStateFlow("unitSystem", "METRIC")
    val nickname: StateFlow<String> = savedStateHandle.getStateFlow("nickname", "")
    val trim: StateFlow<String> = savedStateHandle.getStateFlow("trim", "")
    val engine: StateFlow<String> = savedStateHandle.getStateFlow("engine", "")
    val cylinders: StateFlow<String> = savedStateHandle.getStateFlow("cylinders", "")
    val transmission: StateFlow<String> = savedStateHandle.getStateFlow("transmission", "")
    val color: StateFlow<String> = savedStateHandle.getStateFlow("color", "")
    val fuelType: StateFlow<String> = savedStateHandle.getStateFlow("fuelType", "GASOLINA")
    val tankCapacity: StateFlow<String> = savedStateHandle.getStateFlow("tankCapacity", "")
    val hasUnsavedChanges: StateFlow<Boolean> = savedStateHandle.getStateFlow("hasUnsavedChanges", false)

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        if (!savedStateHandle.contains("loadedFromDb")) {
            if (vehicleId != null) {
                viewModelScope.launch {
                    repo.get(vehicleId)?.let { v ->
                        savedStateHandle["photoUri"] = v.photoUri
                        savedStateHandle["brand"] = v.brand
                        savedStateHandle["model"] = v.model
                        savedStateHandle["year"] = v.year.toString()
                        savedStateHandle["plates"] = v.plates ?: ""
                        savedStateHandle["unitSystem"] = v.unitSystem
                        val odoDisplay = if (v.unitSystem == "METRIC") v.odometer
                        else UnitConverter.kmToMiles(v.odometer).roundToLong()
                        savedStateHandle["odometer"] = odoDisplay.toString()
                        savedStateHandle["currencyCode"] = v.currencyCode
                        savedStateHandle["purchasePrice"] = v.purchasePriceCents?.let { (it / 100.0).toString() } ?: ""
                        savedStateHandle["nickname"] = v.nickname ?: ""
                        savedStateHandle["trim"] = v.trim ?: ""
                        savedStateHandle["engine"] = v.engine ?: ""
                        savedStateHandle["cylinders"] = v.cylinders?.toString() ?: ""
                        savedStateHandle["transmission"] = v.transmission ?: ""
                        savedStateHandle["color"] = v.color ?: ""
                        savedStateHandle["fuelType"] = v.fuelType
                        val tankDisplay = v.tankLiters?.let {
                            val tankVal = if (v.unitSystem == "US") it / UnitConverter.LITERS_PER_GALLON else it
                            String.format(Locale.US, "%.1f", tankVal).trimEnd('0').trimEnd('.')
                        } ?: ""
                        savedStateHandle["tankCapacity"] = tankDisplay
                    }
                    savedStateHandle["loadedFromDb"] = true
                }
            } else {
                viewModelScope.launch {
                    savedStateHandle["currencyCode"] = settingsStore.currencyCode.first()
                    savedStateHandle["unitSystem"] = settingsStore.unitSystem.first().name
                    savedStateHandle["fuelType"] = "GASOLINA"
                    savedStateHandle["loadedFromDb"] = true
                }
            }
        }
    }

    fun updatePhoto(uri: Uri?) { savedStateHandle["photoUri"] = uri?.toString(); markDirty() }
    fun removePhoto() { savedStateHandle["photoUri"] = null; markDirty() }
    fun updateBrand(v: String) { savedStateHandle["brand"] = v; markDirty(); error = null }
    fun updateModel(v: String) { savedStateHandle["model"] = v; markDirty(); error = null }
    fun updateYear(v: String) { savedStateHandle["year"] = v; markDirty(); error = null }
    fun updatePlates(v: String) { savedStateHandle["plates"] = v; markDirty() }
    fun updateOdometer(v: String) { savedStateHandle["odometer"] = v; markDirty(); error = null }
    fun updatePurchasePrice(v: String) { savedStateHandle["purchasePrice"] = v; markDirty(); error = null }
    fun updateCurrency(v: String) { savedStateHandle["currencyCode"] = v; markDirty() }
    fun updateUnits(v: String) { savedStateHandle["unitSystem"] = v; markDirty() }
    fun updateNickname(v: String) { savedStateHandle["nickname"] = v; markDirty() }
    fun updateTrim(v: String) { savedStateHandle["trim"] = v; markDirty() }
    fun updateEngine(v: String) { savedStateHandle["engine"] = v; markDirty() }
    fun updateCylinders(v: String) { savedStateHandle["cylinders"] = v; markDirty(); error = null }
    fun updateTransmission(v: String) { savedStateHandle["transmission"] = v; markDirty() }
    fun updateColor(v: String) { savedStateHandle["color"] = v; markDirty() }
    fun updateFuelType(v: String) { savedStateHandle["fuelType"] = v; markDirty() }
    fun updateTankCapacity(v: String) { savedStateHandle["tankCapacity"] = v; markDirty(); error = null }
    private fun markDirty() { savedStateHandle["hasUnsavedChanges"] = true }

    fun save(context: android.content.Context, onDone: () -> Unit) {
        val currentBrand = brand.value
        val currentModel = model.value
        val currentYear = year.value
        val currentOdometer = odometer.value

        if (currentBrand.isBlank() || currentModel.isBlank() || currentYear.isBlank() || currentOdometer.isBlank()) {
            error = context.getString(R.string.vehicles_err_required); return
        }

        val yearInt = currentYear.toIntOrNull()
        val currentYearInt = LocalDate.now().year
        if (yearInt == null || yearInt < 1900 || yearInt > currentYearInt + 1) {
            error = context.getString(R.string.vehicles_err_year, currentYearInt + 1); return
        }

        val priceDouble = purchasePrice.value.toDoubleOrNull()
        if (purchasePrice.value.isNotBlank() && (priceDouble == null || priceDouble < 0 || priceDouble > 10_000_000.0)) {
            error = context.getString(R.string.vehicles_err_price); return
        }

        val cylText = cylinders.value.trim()
        val cylInt = if (cylText.isNotBlank()) {
            val parsed = cylText.toIntOrNull()
            if (parsed == null || parsed !in 1..16) {
                error = context.getString(R.string.vehicles_err_cylinders); return
            }
            parsed
        } else null

        val currentUnitSystem = unitSystem.value
        val tankText = tankCapacity.value.trim()
        val tankLitersVal = if (tankText.isNotBlank()) {
            val parsed = tankText.toDoubleOrNull()
            if (parsed == null || parsed <= 0) {
                error = context.getString(R.string.vehicles_err_tank); return
            }
            if (currentUnitSystem == "US") parsed * UnitConverter.LITERS_PER_GALLON else parsed
        } else null

        val priceCents = priceDouble?.times(100)?.roundToLong()
        val odoInput = currentOdometer.toDoubleOrNull() ?: 0.0
        val odoKm = if (currentUnitSystem == "METRIC") odoInput.roundToLong()
        else UnitConverter.milesToKm(odoInput)

        viewModelScope.launch {
            isLoading = true
            try {
                val existing = vehicleId?.let { repo.get(it) }
                val currentPhoto = photoUri.value
                val existingPhoto = existing?.photoUri
                var finalPhotoUri: String? = existingPhoto

                if (currentPhoto == null) {
                    if (existingPhoto != null) {
                        ImageUtils.deleteLocalFile(context, existingPhoto)
                    }
                    finalPhotoUri = null
                } else if (currentPhoto != existingPhoto) {
                    val parsed = try { Uri.parse(currentPhoto) } catch (_: Exception) { null }
                    if (parsed != null && (parsed.scheme == "content" || parsed.scheme == "file")) {
                        val savedFileName = ImageUtils.compressAndSave(context, parsed)
                        if (savedFileName != null) {
                            if (existingPhoto != null) {
                                ImageUtils.deleteLocalFile(context, existingPhoto)
                            }
                            finalPhotoUri = savedFileName
                        } else {
                            error = context.getString(R.string.vehicles_photo_err_save)
                            isLoading = false
                            return@launch
                        }
                    }
                }

                val vehicle = if (existing != null) {
                    existing.copy(
                        brand = currentBrand,
                        model = currentModel,
                        year = yearInt,
                        plates = plates.value.trim().takeIf { it.isNotBlank() },
                        odometer = odoKm,
                        photoUri = finalPhotoUri,
                        currencyCode = currencyCode.value,
                        unitSystem = currentUnitSystem,
                        purchasePriceCents = priceCents,
                        nickname = nickname.value.trim().takeIf { it.isNotBlank() },
                        trim = trim.value.trim().takeIf { it.isNotBlank() },
                        engine = engine.value.trim().takeIf { it.isNotBlank() },
                        cylinders = cylInt,
                        transmission = transmission.value.trim().takeIf { it.isNotBlank() },
                        color = color.value.trim().takeIf { it.isNotBlank() },
                        fuelType = fuelType.value,
                        tankLiters = tankLitersVal,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    VehicleEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        brand = currentBrand,
                        model = currentModel,
                        year = yearInt,
                        plates = plates.value.trim().takeIf { it.isNotBlank() },
                        odometer = odoKm,
                        photoUri = finalPhotoUri,
                        currencyCode = currencyCode.value,
                        unitSystem = currentUnitSystem,
                        purchasePriceCents = priceCents,
                        nickname = nickname.value.trim().takeIf { it.isNotBlank() },
                        trim = trim.value.trim().takeIf { it.isNotBlank() },
                        engine = engine.value.trim().takeIf { it.isNotBlank() },
                        cylinders = cylInt,
                        transmission = transmission.value.trim().takeIf { it.isNotBlank() },
                        color = color.value.trim().takeIf { it.isNotBlank() },
                        fuelType = fuelType.value,
                        tankLiters = tankLitersVal
                    )
                }
                repo.upsert(vehicle)
                settingsStore.setActiveVehicle(vehicle.id)
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
fun VehicleEditScreen(
    vehicleId: String?,
    onDone: () -> Unit,
    vm: VehicleEditViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val photoUri by vm.photoUri.collectAsStateWithLifecycle()
    val brand by vm.brand.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val year by vm.year.collectAsStateWithLifecycle()
    val plates by vm.plates.collectAsStateWithLifecycle()
    val odometer by vm.odometer.collectAsStateWithLifecycle()
    val purchasePrice by vm.purchasePrice.collectAsStateWithLifecycle()
    val currencyCode by vm.currencyCode.collectAsStateWithLifecycle()
    val unitSystem by vm.unitSystem.collectAsStateWithLifecycle()
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    val trim by vm.trim.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val cylinders by vm.cylinders.collectAsStateWithLifecycle()
    val transmission by vm.transmission.collectAsStateWithLifecycle()
    val color by vm.color.collectAsStateWithLifecycle()
    val fuelType by vm.fuelType.collectAsStateWithLifecycle()
    val tankCapacity by vm.tankCapacity.collectAsStateWithLifecycle()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsStateWithLifecycle()

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var expandedCurrency by remember { mutableStateOf(false) }
    var expandedUnits by remember { mutableStateOf(false) }
    var expandedFuelType by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val isEditing = vehicleId != null

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            vm.updatePhoto(cameraUri)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            vm.updatePhoto(uri)
        }
    }

    val hasDetails = nickname.isNotBlank() || trim.isNotBlank() || engine.isNotBlank() ||
            cylinders.isNotBlank() || transmission.isNotBlank() || color.isNotBlank() ||
            fuelType != "GASOLINA" || tankCapacity.isNotBlank()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vehicleId == null) R.string.vehicles_new_title else R.string.vehicles_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (hasUnsavedChanges) showDiscardDialog = true else onDone() }) {
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
            // Sección de Foto del vehículo
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.vehicles_photo_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    VehicleAvatar(
                        photoUri = photoUri,
                        size = 110.dp,
                        shape = RoundedCornerShape(16.dp),
                        contentDescription = stringResource(R.string.vehicles_photo_cd)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val uri = ImageUtils.createTempImageUri(context)
                                if (uri != null) {
                                    cameraUri = uri
                                    cameraLauncher.launch(uri)
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.vehicles_photo_camera),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.vehicles_photo_gallery),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (photoUri != null) {
                        TextButton(
                            onClick = { vm.removePhoto() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.vehicles_photo_remove))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = brand,
                onValueChange = { vm.updateBrand(it) },
                label = { Text(stringResource(R.string.vehicles_brand_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { vm.updateModel(it) },
                label = { Text(stringResource(R.string.vehicles_model_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = year,
                onValueChange = { vm.updateYear(it) },
                label = { Text(stringResource(R.string.vehicles_year_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = plates,
                onValueChange = { vm.updatePlates(it) },
                label = { Text(stringResource(R.string.vehicles_plates_label)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            // Selector de cómo registrar el vehículo (Unidades y Moneda)
            Text(
                stringResource(R.string.vehicles_how_to_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Selector de Unidades
                ExposedDropdownMenuBox(
                    expanded = expandedUnits,
                    onExpandedChange = { expandedUnits = !expandedUnits },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = if (unitSystem == "METRIC") "km / L" else "mi / gal",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.vehicles_units_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnits) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedUnits,
                        onDismissRequest = { expandedUnits = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("km / L") },
                            onClick = {
                                vm.updateUnits("METRIC")
                                expandedUnits = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("mi / gal") },
                            onClick = {
                                vm.updateUnits("US")
                                expandedUnits = false
                            }
                        )
                    }
                }

                // Selector de Moneda
                ExposedDropdownMenuBox(
                    expanded = expandedCurrency,
                    onExpandedChange = { expandedCurrency = !expandedCurrency },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = currencyCode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.vehicles_currency_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurrency) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCurrency,
                        onDismissRequest = { expandedCurrency = false }
                    ) {
                        listOf("MXN", "USD", "EUR", "COP", "ARS", "CLP").forEach { curr ->
                            DropdownMenuItem(
                                text = { Text(curr) },
                                onClick = {
                                    vm.updateCurrency(curr)
                                    expandedCurrency = false
                                }
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = { showMore = !showMore },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(if (showMore) R.string.vehicles_less_details else R.string.vehicles_more_details))
            }

            if (showMore || hasDetails) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { vm.updateNickname(it) },
                    label = { Text(stringResource(R.string.vehicles_field_nickname)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = trim,
                    onValueChange = { vm.updateTrim(it) },
                    label = { Text(stringResource(R.string.vehicles_field_trim)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = engine,
                    onValueChange = { vm.updateEngine(it) },
                    label = { Text(stringResource(R.string.vehicles_field_engine)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cylinders,
                    onValueChange = { vm.updateCylinders(it) },
                    label = { Text(stringResource(R.string.vehicles_field_cylinders)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )

                // Transmisión
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = transmission == "MANUAL",
                        onClick = { vm.updateTransmission(if (transmission == "MANUAL") "" else "MANUAL") },
                        label = { Text(stringResource(R.string.vehicles_trans_manual)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = transmission == "AUTOMATICA",
                        onClick = { vm.updateTransmission(if (transmission == "AUTOMATICA") "" else "AUTOMATICA") },
                        label = { Text(stringResource(R.string.vehicles_trans_automatic)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = color,
                    onValueChange = { vm.updateColor(it) },
                    label = { Text(stringResource(R.string.vehicles_field_color)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )

                // Tipo de combustible
                ExposedDropdownMenuBox(
                    expanded = expandedFuelType,
                    onExpandedChange = { expandedFuelType = !expandedFuelType }
                ) {
                    val fuelLabel = when (fuelType) {
                        "DIESEL" -> stringResource(R.string.fuel_type_diesel)
                        "GAS_LP" -> stringResource(R.string.fuel_type_lpg)
                        "ELECTRICO" -> stringResource(R.string.fuel_type_ev)
                        else -> stringResource(R.string.fuel_type_gas)
                    }
                    OutlinedTextField(
                        value = fuelLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.vehicles_field_fuel_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFuelType) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedFuelType,
                        onDismissRequest = { expandedFuelType = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fuel_type_gas)) },
                            onClick = { vm.updateFuelType("GASOLINA"); expandedFuelType = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fuel_type_diesel)) },
                            onClick = { vm.updateFuelType("DIESEL"); expandedFuelType = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fuel_type_lpg)) },
                            onClick = { vm.updateFuelType("GAS_LP"); expandedFuelType = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fuel_type_ev)) },
                            onClick = { vm.updateFuelType("ELECTRICO"); expandedFuelType = false }
                        )
                    }
                }

                OutlinedTextField(
                    value = tankCapacity,
                    onValueChange = { vm.updateTankCapacity(it) },
                    label = { Text(stringResource(R.string.vehicles_field_tank_capacity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    suffix = { Text(if (unitSystem == "METRIC") "L" else "gal") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            OutlinedTextField(
                value = odometer,
                onValueChange = { vm.updateOdometer(it) },
                label = { Text(stringResource(R.string.vehicles_odometer_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                suffix = { Text(if (unitSystem == "METRIC") "km" else "mi") },
                modifier = Modifier.fillMaxWidth()
            )

            CurrencyField(
                value = purchasePrice,
                onValueChange = { vm.updatePurchasePrice(it) },
                label = stringResource(R.string.vehicles_price_label),
                currencyCode = currencyCode,
                imeAction = ImeAction.Done,
                onImeAction = { focusManager.clearFocus() },
                modifier = Modifier.fillMaxWidth()
            )

            vm.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    vm.save(context) {
                        Toast.makeText(context, context.getString(R.string.vehicles_saved_toast), Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(R.string.vehicles_save_btn))
            }
        }
    }
}
