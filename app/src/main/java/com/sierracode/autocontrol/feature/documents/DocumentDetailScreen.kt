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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.ui.components.FullScreenImageDialog
import com.sierracode.autocontrol.data.local.entity.DocumentEntity
import com.sierracode.autocontrol.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val repo: DocumentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val documentId: String = savedStateHandle["documentId"] ?: ""

    private val _document = MutableStateFlow<DocumentEntity?>(null)
    val document = _document.asStateFlow()

    init {
        viewModelScope.launch {
            _document.value = repo.get(documentId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    onBack: () -> Unit,
    onEditClick: (String) -> Unit,
    vm: DocumentDetailViewModel = hiltViewModel()
) {
    val doc by vm.document.collectAsState()
    val context = LocalContext.current
    var showFullScreenImage by remember { mutableStateOf(false) }

    // Launcher para "Guardar PDF" (Crea un archivo en el dispositivo)
    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && doc?.fileUri != null) {
            try {
                val sourceFile = File(Uri.parse(doc!!.fileUri).path!!)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (doc == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val d = doc!!
    val isPdf = d.fileUri?.endsWith(".pdf", ignoreCase = true) == true
    val isImage = !isPdf && d.fileUri != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Documento") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { onEditClick(d.id) }) {
                        Icon(Icons.Default.Edit, "Editar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(d.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            AssistChip(onClick = { }, label = { Text(d.type) })

            HorizontalDivider()

            if (d.issueDateEpochDay != null) {
                Text("Expedición: ${formatDate(d.issueDateEpochDay)}", style = MaterialTheme.typography.bodyLarge)
            }
            Text("Vencimiento: ${formatDate(d.expiryDateEpochDay)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)

            if (!d.notes.isNullOrBlank()) {
                Text("Notas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(d.notes, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))

            if (isImage) {
                Text("Archivo adjunto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AsyncImage(
                    model = d.fileUri,
                    contentDescription = "Documento adjunto",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clickable { showFullScreenImage = true },
                    contentScale = ContentScale.Fit
                )
                Text("Toca la imagen para verla completa y compartirla", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            } else if (isPdf) {
                Text("Archivo adjunto (PDF)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Documento PDF", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(d.fileUri), "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Abrir") }

                    OutlinedButton(
                        onClick = { downloadLauncher.launch("${d.name}.pdf") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Guardar") }
                }
            } else {
                Text("Este documento no tiene un archivo adjunto.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showFullScreenImage && d.fileUri != null) {
        FullScreenImageDialog(
            imageUri = Uri.parse(d.fileUri),
            onDismiss = { showFullScreenImage = false }
        )
    }
}