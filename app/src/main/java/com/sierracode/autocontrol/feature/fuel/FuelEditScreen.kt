package com.sierracode.autocontrol.feature.fuel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.util.ImageUtils
import com.sierracode.autocontrol.data.local.entity.AttachmentEntity
import com.sierracode.autocontrol.data.local.entity.CategoryEntity
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import com.sierracode.autocontrol.data.local.entity.FuelDetailEntity
import com.sierracode.autocontrol.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FuelEditViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val vehicleRepo: VehicleRepository,
    categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""

    val fuelCategories = categoryRepo.observeAll()
        .map { list -> list.filter { it.group == "FUEL" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedCategory by mutableStateOf<CategoryEntity?>(null)
    var odometer by mutableStateOf("")
    var liters by mutableStateOf("")
    var pricePerLiter by mutableStateOf("")
    var total by mutableStateOf("")
    var station by mutableStateOf("")
    var isFullTank by mutableStateOf(true)
    var selectedImageUri by mutableStateOf<Uri?>(null)

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun onLitersOrPriceChange() {
        val l = liters.toDoubleOrNull()
        val p = pricePerLiter.toDoubleOrNull()
        if (l != null && p != null) {
            total = String.format(java.util.Locale.US, "%.2f", l * p)
        }
    }

    fun save(context: android.content.Context, onDone: () -> Unit) {
        val l = liters.toDoubleOrNull() ?: 0.0
        val t = total.toDoubleOrNull() ?: 0.0
        val p = pricePerLiter.toDoubleOrNull() ?: 0.0
        val odo = odometer.toLongOrNull()

        if (t <= 0 || l <= 0) {
            error = context.getString(R.string.fuel_edit_err_valid)
            return
        }
        if (selectedCategory == null) {
            error = context.getString(R.string.fuel_edit_err_type)
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val expenseId = UUID.randomUUID().toString()
                val expense = ExpenseEntity(
                    id = expenseId,
                    vehicleId = vehicleId,
                    type = "FUEL",
                    categoryId = selectedCategory!!.id,
                    amountCents = (t * 100).toLong(),
                    dateEpochDay = LocalDate.now().toEpochDay(),
                    odometer = odo,
                    description = context.getString(R.string.fuel_edit_desc, selectedCategory!!.name.lowercase())
                )

                val fuelDetail = FuelDetailEntity(
                    expenseId = expenseId,
                    litersMilli = (l * 1000).toLong(),
                    pricePerLiterCents = (p * 100).toLong(),
                    fuelType = selectedCategory!!.name.uppercase(),
                    station = station.takeIf { it.isNotBlank() },
                    fullTank = isFullTank
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

                expenseRepo.saveWithDetails(expense, fuelDetail, null, attachment)

                if (odo != null && odo > 0) {
                    vehicleRepo.bumpOdometerIfHigher(vehicleId, odo)
                }

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
fun FuelEditScreen(
    vehicleId: String,
    onDone: () -> Unit,
    vm: FuelEditViewModel = hiltViewModel()
) {
    val categories by vm.fuelCategories.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(categories) {
        if (vm.selectedCategory == null && categories.isNotEmpty()) {
            vm.selectedCategory = categories.firstOrNull { it.id == "fuel_gas" } ?: categories.first()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) vm.selectedImageUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fuel_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
                    value = vm.selectedCategory?.name ?: stringResource(R.string.fuel_edit_select_type),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.fuel_edit_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                vm.selectedCategory = category
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = vm.odometer,
                onValueChange = { vm.odometer = it },
                label = { Text(stringResource(R.string.fuel_edit_odometer)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vm.liters,
                    onValueChange = {
                        vm.liters = it
                        vm.onLitersOrPriceChange()
                    },
                    label = { Text(stringResource(R.string.fuel_edit_liters)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = vm.pricePerLiter,
                    onValueChange = {
                        vm.pricePerLiter = it
                        vm.onLitersOrPriceChange()
                    },
                    label = { Text(stringResource(R.string.fuel_edit_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = vm.total,
                onValueChange = { vm.total = it },
                label = { Text(stringResource(R.string.fuel_edit_total)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.station,
                onValueChange = { vm.station = it },
                label = { Text(stringResource(R.string.fuel_edit_station)) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = vm.isFullTank,
                    onCheckedChange = { vm.isFullTank = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fuel_edit_full_tank))
            }

            OutlinedButton(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (vm.selectedImageUri == null) R.string.fuel_edit_receipt else R.string.fuel_edit_receipt_change))
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
                onClick = { vm.save(context, onDone) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
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