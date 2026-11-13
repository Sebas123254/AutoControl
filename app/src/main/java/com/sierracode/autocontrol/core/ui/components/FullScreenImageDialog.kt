package com.sierracode.autocontrol.core.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.sierracode.autocontrol.R
import java.io.File

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