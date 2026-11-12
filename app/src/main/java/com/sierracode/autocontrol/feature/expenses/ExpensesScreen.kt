package com.sierracode.autocontrol.feature.expenses

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.data.local.entity.AttachmentEntity
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import com.sierracode.autocontrol.data.repository.AttachmentRepository
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val attachmentRepo: AttachmentRepository
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    val expenses = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else expenseRepo.observeByVehicle(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Estados para el diálogo de detalles
    var selectedExpense by mutableStateOf<ExpenseEntity?>(null)
    var selectedAttachment by mutableStateOf<AttachmentEntity?>(null)

    // Estados para la selección múltiple
    var isSelectionMode by mutableStateOf(false)
        private set
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var isDeleting by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    fun loadVehicle(id: String) {
        vehicleIdFlow.value = id
    }

    fun selectExpense(expense: ExpenseEntity) {
        selectedExpense = expense
        viewModelScope.launch {
            selectedAttachment = attachmentRepo.getForOwner("expense", expense.id)
        }
    }

    fun dismissDialog() {
        selectedExpense = null
        selectedAttachment = null
    }

    // --- Lógica de Selección Múltiple ---
    fun toggleSelection(id: String) {
        selectedIds = if (selectedIds.contains(id)) {
            val newSet = selectedIds - id
            if (newSet.isEmpty()) isSelectionMode = false
            newSet
        } else {
            selectedIds + id
        }
    }

    fun enterSelectionMode(id: String) {
        if (!isSelectionMode) {
            isSelectionMode = true
            selectedIds = setOf(id)
        }
    }

    fun selectAll(visibleExpenses: List<ExpenseEntity>) {
        selectedIds = visibleExpenses.map { it.id }.toSet()
    }

    fun clearSelection() {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    fun deleteSelected(onSuccess: (Int) -> Unit) {
        if (selectedIds.isEmpty()) return
        val count = selectedIds.size
        viewModelScope.launch {
            isDeleting = true
            errorMessage = null
            try {
                expenseRepo.deleteMultiple(selectedIds.toList())
                clearSelection()
                onSuccess(count)
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isDeleting = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpensesScreen(
    vehicleId: String,
    onBack: () -> Unit,
    onEditClick: (String) -> Unit,
    vm: ExpensesViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) {
        vm.loadVehicle(vehicleId)
    }

    val expenses by vm.expenses.collectAsState()
    var showFullScreenImage by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessageTemplate = stringResource(R.string.expenses_deleted_success)
    val errorMessageText = stringResource(R.string.expenses_delete_error)

    // Manejo del botón Atrás del sistema
    BackHandler(enabled = vm.isSelectionMode) {
        vm.clearSelection()
    }

    // Efecto para mostrar Snackbar de éxito o error
    LaunchedEffect(vm.errorMessage) {
        vm.errorMessage?.let {
            snackbarHostState.showSnackbar(errorMessageText)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.expenses_delete_title, vm.selectedIds.size)) },
            text = { Text(stringResource(R.string.expenses_delete_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        vm.deleteSelected { count ->
                            // Usamos corrutina para el Snackbar
                            kotlinx.coroutines.MainScope().launch {
                                snackbarHostState.showSnackbar(deletedMessageTemplate.format(count))
                            }
                        }
                    },
                    enabled = !vm.isDeleting
                ) {
                    if (vm.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (vm.selectedExpense != null) {
        val expense = vm.selectedExpense!!
        AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            title = { Text(expense.description ?: stringResource(R.string.expenses_detail_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.expenses_amount, formatMoneyCents(expense.amountCents)), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.expenses_date, formatDate(expense.dateEpochDay)))
                    if (expense.odometer != null) {
                        Text(stringResource(R.string.expenses_odometer, expense.odometer))
                    }

                    Spacer(Modifier.height(8.dp))

                    if (vm.selectedAttachment != null) {
                        Text(stringResource(R.string.expenses_receipt_zoom), style = MaterialTheme.typography.labelMedium)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .padding(top = 8.dp)
                                .clickable { showFullScreenImage = true }
                        ) {
                            AsyncImage(
                                model = Uri.parse(vm.selectedAttachment!!.uri),
                                contentDescription = stringResource(R.string.expense_edit_receipt_desc),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.expenses_amplify),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                                    .padding(4.dp),
                                tint = Color.White
                            )
                        }
                    } else {
                        Text(stringResource(R.string.expenses_no_receipt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.dismissDialog() }) { Text(stringResource(R.string.action_close)) }
                    Button(onClick = {
                        vm.dismissDialog()
                        onEditClick(expense.id)
                    }) { Text(stringResource(R.string.action_edit)) }
                }
            }
        )
    }

    if (showFullScreenImage && vm.selectedAttachment != null) {
        FullScreenImageDialog(
            imageUri = Uri.parse(vm.selectedAttachment!!.uri),
            onDismiss = { showFullScreenImage = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (vm.isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.expenses_selected, vm.selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.expenses_cancel_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.selectAll(expenses) }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.expenses_select_all))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete_plural), tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.expenses_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = stringResource(R.string.vehicles_title))
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (expenses.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.expenses_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenses, key = { it.id }) { expense ->
                    val isSelected = vm.selectedIds.contains(expense.id)
                    ExpenseRow(
                        expense = expense,
                        isSelectionMode = vm.isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (vm.isSelectionMode) vm.toggleSelection(expense.id)
                            else vm.selectExpense(expense)
                        },
                        onLongClick = { vm.enterSelectionMode(expense.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseRow(
    expense: ExpenseEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description ?: stringResource(R.string.expenses_default_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatDate(expense.dateEpochDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatMoneyCents(expense.amountCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ... El FullScreenImageDialog se mantiene exactamente igual ...
@Composable
fun FullScreenImageDialog(imageUri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.expenses_share_title)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offset += pan
                        } else {
                            offset = androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                }
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = stringResource(R.string.expenses_full_screen),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        try {
                            val file = File(imageUri.path ?: return@IconButton)
                            val contentUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, shareTitle))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                ) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share), tint = Color.White)
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
                }
            }
        }
    }
}