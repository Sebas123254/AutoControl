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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sierracode.autocontrol.core.util.ImageUtils
import com.sierracode.autocontrol.data.local.dao.CategoryDao
import com.sierracode.autocontrol.data.local.entity.AttachmentEntity
import com.sierracode.autocontrol.data.local.entity.CategoryEntity
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import com.sierracode.autocontrol.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ExpenseEditViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val vehicleRepo: VehicleRepository,
    categoryDao: CategoryDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""

    val categories = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var amount by mutableStateOf("")
    var odometer by mutableStateOf("")
    var description by mutableStateOf("")
    var selectedCategory by mutableStateOf<CategoryEntity?>(null)
    var selectedImageUri by mutableStateOf<Uri?>(null) // <-- FOTO

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun save(context: android.content.Context, onDone: () -> Unit) {
        val amountDouble = amount.toDoubleOrNull() ?: 0.0
        val amountCents = (amountDouble * 100).toLong()
        val odo = odometer.toLongOrNull()

        if (amount.isEmpty() || amountCents <= 0) {
            error = "Ingresa un monto válido"
            return
        }
        if (selectedCategory == null) {
            error = "Selecciona una categoría"
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val expenseId = java.util.UUID.randomUUID().toString()
                val expense = ExpenseEntity(
                    id = expenseId,
                    vehicleId = vehicleId,
                    type = selectedCategory!!.group,
                    categoryId = selectedCategory!!.id,
                    amountCents = amountCents,
                    dateEpochDay = LocalDate.now().toEpochDay(),
                    odometer = odo,
                    description = description.takeIf { it.isNotBlank() }
                )

                // Comprimir y guardar la imagen si existe
                var attachment: AttachmentEntity? = null
                selectedImageUri?.let { uri ->
                    val compressedUri = ImageUtils.compressAndSave(context, uri)
                    if (compressedUri != null) {
                        attachment = AttachmentEntity(
                            ownerType = "expense",
                            ownerId = expenseId,
                            uri = compressedUri.toString(),
                            mimeType = "image/jpeg",
                            bytes = 0L
                        )
                    }
                }

                expenseRepo.saveWithDetails(expense, null, null, attachment)

                if (odo != null && odo > 0) {
                    vehicleRepo.bumpOdometerIfHigher(vehicleId, odo)
                }

                onDone()
            } catch (e: Exception) {
                error = "Error al guardar: ${e.message}"
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
                title = { Text("Registrar Gasto") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
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
            // Selector de categoría
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = vm.selectedCategory?.name ?: "Selecciona categoría",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoría *") },
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
                label = { Text("Monto total ($) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.odometer,
                onValueChange = { vm.odometer = it },
                label = { Text("Kilometraje actual (Opcional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.description,
                onValueChange = { vm.description = it },
                label = { Text("Descripción (Ej. Cambio de aceite)") },
                modifier = Modifier.fillMaxWidth()
            )

            // --- SECCIÓN DE FOTO ---
            OutlinedButton(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (vm.selectedImageUri == null) "Adjuntar recibo (Opcional)" else "Cambiar recibo")
            }

            if (vm.selectedImageUri != null) {
                AsyncImage(
                    model = vm.selectedImageUri,
                    contentDescription = "Recibo",
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
                if (vm.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Guardar Gasto")
            }
        }
    }
}