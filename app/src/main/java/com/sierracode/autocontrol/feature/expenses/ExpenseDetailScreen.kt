package com.sierracode.autocontrol.feature.expenses

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.formatKm
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.core.format.formatVolume
import com.sierracode.autocontrol.core.format.localizedCategoryName
import com.sierracode.autocontrol.core.ui.components.FullScreenImageDialog
import com.sierracode.autocontrol.data.local.entity.AttachmentEntity
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import com.sierracode.autocontrol.data.local.entity.FuelDetailEntity
import com.sierracode.autocontrol.data.local.entity.MaintenanceDetailEntity
import com.sierracode.autocontrol.data.repository.AttachmentRepository
import com.sierracode.autocontrol.data.repository.CategoryRepository
import com.sierracode.autocontrol.data.repository.ExpenseDeletionSnapshot
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ExpenseDetailState(
    val expense: ExpenseEntity,
    val fuel: FuelDetailEntity?,
    val maintenance: MaintenanceDetailEntity?,
    val attachment: AttachmentEntity?,
    val categoryName: String?
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val attachmentRepo: AttachmentRepository,
    categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val expenseId: String = savedStateHandle["expenseId"] ?: ""

    val state: StateFlow<ExpenseDetailState?> = expenseRepo.observeOne(expenseId)
        .flatMapLatest { expense: ExpenseEntity? ->
            // Tipar explícitamente el Flow para que flowOf(null) no infiera Flow<Nothing?>.
            if (expense == null) {
                flowOf<ExpenseDetailState?>(null)
            } else {
                flow<ExpenseDetailState?> {
                    val fuel = expenseRepo.getFuelDetail(expenseId)
                    val maint = expenseRepo.getMaintenanceDetail(expenseId)
                    val attachment = attachmentRepo.getForOwner("expense", expenseId)
                    val categories = categoryRepo.observeAll().first()
                    val catName = categories.find { it.id == expense.categoryId }?.name
                    emit(ExpenseDetailState(expense, fuel, maint, attachment, catName))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun captureForDeletion(): ExpenseDeletionSnapshot? = expenseRepo.captureForDeletion(expenseId)

    suspend fun deleteNow() {
        expenseRepo.deleteDeferred(expenseId)
    }

    fun restore(snapshot: ExpenseDeletionSnapshot) {
        viewModelScope.launch { expenseRepo.restoreFromSnapshot(snapshot) }
    }

    fun purgeFile(snapshot: ExpenseDeletionSnapshot) {
        expenseRepo.purgeAttachmentFile(snapshot.attachment?.uri)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    expenseId: String,
    vehicleId: String,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: ExpenseDetailViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showFullScreenImage by remember { mutableStateOf(false) }
    var showLockedDialog by remember { mutableStateOf(false) }

    val undoLabel = stringResource(R.string.action_undo)
    val shareTitle = stringResource(R.string.expense_edit_receipt_desc)
    val deletedMessage = stringResource(R.string.expense_deleted)

    val deleteWithUndo: () -> Unit = {
        scope.launch {
            val snapshot = vm.captureForDeletion() ?: return@launch
            vm.deleteNow()
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                vm.restore(snapshot)
            } else {
                vm.purgeFile(snapshot)
                onBack()
            }
        }
    }

    val currentState = state
    if (currentState == null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.expense_detail_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val expense = currentState.expense
    val isFuelOrMaint = expense.type == "FUEL" || expense.type == "MAINTENANCE"
    val displayTitle = if (expense.customCategoryName != null) {
        "Otro · ${expense.customCategoryName}"
    } else {
        expense.description ?: stringResource(R.string.expenses_default_name)
    }

    val onEditAction: () -> Unit = {
        if (isFuelOrMaint) {
            showLockedDialog = true
        } else {
            onEditClick()
        }
    }

    if (showLockedDialog) {
        AlertDialog(
            onDismissRequest = { showLockedDialog = false },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            title = { Text(stringResource(R.string.expense_detail_locked_title)) },
            text = { Text(stringResource(R.string.expense_detail_locked_desc)) },
            confirmButton = {
                TextButton(onClick = { showLockedDialog = false }) {
                    Text(stringResource(R.string.action_understood))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expense_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (currentState.attachment != null) {
                        IconButton(onClick = {
                            shareAttachment(context, currentState.attachment.uri, shareTitle)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                    IconButton(onClick = onEditAction) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = deleteWithUndo) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = formatMoneyCents(expense.amountCents),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(localizedCategoryName(expense.categoryId, currentState.categoryName ?: expense.categoryId))
                        }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoRow(
                        label = stringResource(R.string.expense_detail_date),
                        value = formatDate(expense.dateEpochDay)
                    )
                    if (expense.odometer != null) {
                        InfoRow(
                            label = stringResource(R.string.expense_detail_odometer),
                            value = formatKm(expense.odometer)
                        )
                    }
                    if (!expense.description.isNullOrBlank()) {
                        InfoRow(
                            label = stringResource(R.string.expense_detail_description),
                            value = expense.description
                        )
                    }
                    if (!expense.establishment.isNullOrBlank()) {
                        InfoRow(
                            label = stringResource(R.string.expense_detail_establishment),
                            value = expense.establishment
                        )
                    }
                    if (!expense.paymentMethod.isNullOrBlank()) {
                        InfoRow(
                            label = stringResource(R.string.expense_detail_payment_method),
                            value = expense.paymentMethod
                        )
                    }
                }
            }

            currentState.fuel?.let { fuel ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.expense_detail_fuel_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        InfoRow(
                            label = stringResource(R.string.expense_detail_liters),
                            value = formatVolume(fuel.litersMilli)
                        )
                        InfoRow(
                            label = stringResource(R.string.expense_detail_price_per_liter),
                            value = formatMoneyCents(fuel.pricePerLiterCents)
                        )
                        if (!fuel.station.isNullOrBlank()) {
                            InfoRow(
                                label = stringResource(R.string.expense_detail_station),
                                value = fuel.station
                            )
                        }
                        InfoRow(
                            label = stringResource(R.string.expense_detail_full_tank),
                            value = if (fuel.fullTank) stringResource(R.string.expense_detail_yes) else stringResource(R.string.expense_detail_no)
                        )
                    }
                }
            }

            currentState.maintenance?.let { maint ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.expense_detail_maint_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        InfoRow(
                            label = stringResource(R.string.expense_detail_service),
                            value = maint.serviceName
                        )
                        if (!maint.workshop.isNullOrBlank()) {
                            InfoRow(
                                label = stringResource(R.string.expense_detail_workshop),
                                value = maint.workshop
                            )
                        }
                        if (maint.nextOdometer != null) {
                            InfoRow(
                                label = stringResource(R.string.expense_detail_next_odometer),
                                value = formatKm(maint.nextOdometer)
                            )
                        }
                        if (maint.nextDateEpochDay != null) {
                            InfoRow(
                                label = stringResource(R.string.expense_detail_next_date),
                                value = formatDate(maint.nextDateEpochDay)
                            )
                        }
                    }
                }
            }

            if (!expense.notes.isNullOrBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.expense_detail_notes_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = expense.notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            currentState.attachment?.let { attachment ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.expense_detail_receipt),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = attachment.uri,
                            contentDescription = stringResource(R.string.expense_edit_receipt_desc),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clickable { showFullScreenImage = true },
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.expense_detail_tap_image),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showFullScreenImage && currentState.attachment != null) {
        FullScreenImageDialog(
            imageUri = Uri.parse(currentState.attachment.uri),
            onDismiss = { showFullScreenImage = false }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun shareAttachment(context: android.content.Context, fileUri: String?, title: String) {
    if (fileUri.isNullOrBlank()) return
    try {
        val parsed = Uri.parse(fileUri)
        val contentUri = when (parsed.scheme) {
            "file" -> {
                val file = File(parsed.path ?: return)
                if (!file.exists()) return
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            "content" -> parsed
            else -> return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}