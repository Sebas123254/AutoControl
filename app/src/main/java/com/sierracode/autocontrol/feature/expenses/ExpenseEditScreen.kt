package com.sierracode.autocontrol.feature.expenses

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
import com.sierracode.autocontrol.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ExpenseEditViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val vehicleRepo: VehicleRepository,
    private val attachmentRepo: AttachmentRepository,
    categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""
    private val expenseId: String? = savedStateHandle.get<String>("expenseId")?.takeIf { it.isNotBlank() }

    val categories = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var amount by mutableStateOf("")
    var odometer by mutableStateOf("")
    var description by mutableStateOf("")
    var selectedCategory by mutableStateOf<CategoryEntity?>(null)
    var selectedImageUri by mutableStateOf<Uri?>(null)

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        if (expenseId != null) {
            viewModelScope.launch {
                val catList = categories.first { it.isNotEmpty() }
                val existingExpense = expenseRepo.get(expenseId)
                if (existingExpense != null) {
                    amount = (existingExpense.amountCents / 100.0).toString()
                    odometer = existingExpense.odometer?.toString() ?: ""
                    description = existingExpense.description ?: ""
                    selectedCategory = catList.find { it.id == existingExpense.categoryId }

                    val oldAttachment = attachmentRepo.getForOwner("expense", expenseId)
                    if (oldAttachment != null) {
                        selectedImageUri = Uri.parse(oldAttachment.uri)
                    }
                }
            }
        }
    }

    fun save(context: android.content.Context, onDone: () -> Unit) {
        val amountDouble = amount.toDoubleOrNull() ?: 0.0
        val amountCents = (amountDouble * 100).toLong()
        val odo = odometer.toLongOrNull()

        if (amount.isEmpty() || amountCents <= 0) {
            error = context.getString(R.string.expense_edit_err_amount)
            return
        }
        if (selectedCategory == null) {
            error = context.getString(R.string.expense_edit_err_category)
            return
        }

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
                    dateEpochDay = existingExpense?.dateEpochDay ?: LocalDate.now().toEpochDay(),
                    odometer = odo,
                    description = description.takeIf { it.isNotBlank() }
                )

                var attachment: AttachmentEntity? = null
                val currentUri = selectedImageUri

                if (currentUri != null) {
                    if (currentUri.scheme != "file") {
                        val compressedUri = ImageUtils.compressAndSave(context, currentUri)
                        if (compressedUri != null) {
                            if (expenseId != null) {
                                attachmentRepo.getForOwner("expense", expenseId)?.let { oldAtt ->
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
                        }
                    }
                }

                expenseRepo.saveWithDetails(expense, null, null, attachment)

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
fun ExpenseEditScreen(
    vehicleId: String,
    expenseId: String?,
    onDone: () -> Unit,
    vm: ExpenseEditViewModel = hiltViewModel()
) {
    val categories by vm.categories.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            vm.selectedImageUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (expenseId == null) R.string.expense_edit_new else R.string.expense_edit_update)) },
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
                                vm.selectedCategory = category
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = vm.amount,
                onValueChange = { vm.amount = it },
                label = { Text(stringResource(R.string.expense_edit_amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.odometer,
                onValueChange = { vm.odometer = it },
                label = { Text(stringResource(R.string.expense_edit_odometer)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.description,
                onValueChange = { vm.description = it },
                label = { Text(stringResource(R.string.expense_edit_desc)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (vm.selectedImageUri == null) R.string.expense_edit_receipt else R.string.expense_edit_receipt_change))
            }

            if (vm.selectedImageUri != null) {
                AsyncImage(
                    model = vm.selectedImageUri,
                    contentDescription = stringResource(R.string.expense_edit_receipt_desc),
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop
                )
            }

            vm.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { vm.save(context, onDone) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text(stringResource(R.string.expense_edit_save))
            }
        }
    }
}