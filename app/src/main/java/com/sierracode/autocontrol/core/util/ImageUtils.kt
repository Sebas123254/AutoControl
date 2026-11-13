package com.sierracode.autocontrol.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageUtils {
    /**
     * Comprime una imagen desde un Uri, la guarda en el almacenamiento interno
     * y devuelve el nuevo Uri local.
     */
    suspend fun compressAndSave(context: Context, sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val maxWidth = 1280
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null

            var inSampleSize = 1
            if (options.outWidth > maxWidth) {
                inSampleSize = options.outWidth / maxWidth
            }

            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val originalBitmap = context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null

            val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            val newWidth = if (originalBitmap.width > maxWidth) maxWidth else originalBitmap.width
            val newHeight = (newWidth / ratio).toInt()

            val scaledBitmap = if (originalBitmap.width > maxWidth) {
                Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            } else {
                originalBitmap
            }

            val file = File(context.filesDir, "img_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { outputStream ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            }

            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copia un archivo (ej. PDF) desde un Uri de contenido al almacenamiento interno.
     */
    suspend fun copyToInternalStorage(context: Context, sourceUri: Uri, extension: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, "doc_${UUID.randomUUID()}.$extension")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Borra un archivo físico del almacenamiento interno a partir de su URI.
     */
    fun deleteLocalFile(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        try {
            val path = Uri.parse(uriString).path ?: return
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}