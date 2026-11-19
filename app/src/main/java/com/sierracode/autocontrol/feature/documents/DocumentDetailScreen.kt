package com.sierracode.autocontrol.feature.documents

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.localizedDocumentType
import com.sierracode.autocontrol.core.ui.components.FullScreenImageDialog
import com.sierracode.autocontrol.core.ui.components.StatusChip
import com.sierracode.autocontrol.core.ui.components.StatusVariant
import com.sierracode.autocontrol.core.util.FileUtils
import com.sierracode.autocontrol.data.local.entity.DocumentEntity
import com.sierracode.autocontrol.data.repository.DocumentRepository
import com.sierracode.autocontrol.domain.calc.DocumentStatus
import com.sierracode.autocontrol.domain.calc.evaluateDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val repo: DocumentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val documentId: String = savedStateHandle["documentId"] ?: ""

    val document: StateFlow<DocumentEntity?> = repo.observeOne(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun deleteDeferred() {
        repo.deleteDeferred(documentId)
    }

    fun restore(doc: DocumentEntity) {
        viewModelScope.launch { repo.save(doc) }
    }

    fun purgeFile(fileUri: String?) {
        repo.purgeFile(fileUri)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    onBack: () -> Unit,
    onEditClick: (String) -> Unit,
    vm: DocumentDetailViewModel = hiltViewModel()
) {
    val rawDoc by vm.document.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var lastDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    LaunchedEffect(rawDoc) { if (rawDoc != null) lastDoc = rawDoc }
    val doc = rawDoc ?: lastDoc

    var showFullScreenImage by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val undoLabel = stringResource(R.string.action_undo)
    val deletedMessage = stringResource(R.string.expense_deleted)

    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && doc?.fileUri != null) {
            try {
                // Usamos FileUtils para obtener el archivo físico
                val sourceFile = FileUtils.getFile(context, doc!!.fileUri)
                if (sourceFile != null && sourceFile.exists()) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (doc == null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.doc_detail_title)) },
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

    val d = doc
    val isPdf = d.fileUri?.endsWith(".pdf", ignoreCase = true) == true
    val isImage = !isPdf && d.fileUri != null
    val hasAttachment = d.fileUri != null

    val today = LocalDate.now().toEpochDay()
    val expiry = d.expiryDateEpochDay ?: today
    val eval = evaluateDocument(expiry, today, d.reminderLeadDays)

    val statusVariant = when (eval.status) {
        DocumentStatus.EXPIRED -> StatusVariant.ERROR
        DocumentStatus.EXPIRING_SOON -> StatusVariant.WARNING
        DocumentStatus.VALID -> StatusVariant.OK
    }
    val statusIcon = when (eval.status) {
        DocumentStatus.EXPIRED -> Icons.Default.Warning
        DocumentStatus.EXPIRING_SOON -> Icons.Default.Info
        DocumentStatus.VALID -> Icons.Default.CheckCircle
    }
    val statusText = when (eval.status) {
        DocumentStatus.EXPIRED -> stringResource(R.string.docs_expired, -eval.daysRemaining)
        DocumentStatus.EXPIRING_SOON -> stringResource(R.string.docs_expiring, eval.daysRemaining)
        DocumentStatus.VALID -> stringResource(R.string.docs_valid)
    }

    val performDelete: () -> Unit = {
        if (!isDeleting) {
            isDeleting = true
            scope.launch {
                vm.deleteDeferred()
                val result = snackbarHostState.showSnackbar(
                    message = deletedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    vm.restore(d)
                } else {
                    vm.purgeFile(d.fileUri)
                    onBack()
                }
                isDeleting = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doc_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (hasAttachment) {
                        IconButton(onClick = {
                            shareAttachment(context, d.fileUri)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                    IconButton(onClick = { onEditClick(d.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = performDelete, enabled = !isDeleting) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = d.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { },
                            label = { Text(localizedDocumentType(d.type)) }
                        )
                        StatusChip(
                            text = statusText,
                            icon = statusIcon,
                            variant = statusVariant
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (d.issueDateEpochDay != null) {
                        InfoRow(
                            label = stringResource(R.string.doc_detail_issue_date_label),
                            value = formatDate(d.issueDateEpochDay)
                        )
                    }
                    InfoRow(
                        label = stringResource(R.string.doc_detail_expiry_date_label),
                        value = formatDate(d.expiryDateEpochDay)
                    )
                    InfoRow(
                        label = stringResource(R.string.doc_detail_lead_days_label),
                        value = d.reminderLeadDays.toString()
                    )
                }
            }

            if (!d.notes.isNullOrBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.doc_detail_notes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = d.notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (isImage) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.doc_detail_attachment),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))

                        // Usamos FileUtils para obtener la URI correcta de la subcarpeta
                        val imageUri = FileUtils.getContentUri(context, d.fileUri)
                        if (imageUri != null) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = stringResource(R.string.doc_detail_attachment),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .clickable { showFullScreenImage = true },
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.doc_detail_tap_image),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("Imagen no disponible", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else if (isPdf) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.doc_detail_attachment),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    stringResource(R.string.doc_detail_pdf),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    // Usamos FileUtils para obtener la URI segura del PDF
                                    val contentUri = FileUtils.getContentUri(context, d.fileUri)
                                    if (contentUri != null) {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(contentUri, "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        try {
                                            context.startActivity(
                                                Intent.createChooser(intent, context.getString(R.string.doc_detail_open))
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text(stringResource(R.string.doc_detail_open)) }

                            OutlinedButton(
                                onClick = { downloadLauncher.launch("${d.name}.pdf") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text(stringResource(R.string.doc_detail_save)) }
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.doc_detail_attachment),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.doc_detail_no_attachment),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showFullScreenImage && d.fileUri != null) {
        val fullUri = FileUtils.getContentUri(context, d.fileUri)
        if (fullUri != null) {
            FullScreenImageDialog(
                imageUri = fullUri,
                onDismiss = { showFullScreenImage = false }
            )
        }
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

private fun shareAttachment(context: android.content.Context, relativeFileName: String?) {
    val contentUri = FileUtils.getContentUri(context, relativeFileName) ?: return
    try {
        val mimeType = if (relativeFileName?.endsWith(".pdf", ignoreCase = true) == true) "application/pdf" else "image/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.action_share))
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}