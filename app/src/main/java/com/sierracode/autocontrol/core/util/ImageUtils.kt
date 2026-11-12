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
     * y devuelve el nuevo Uri local. Corre en Dispatchers.IO y usa inSampleSize para evitar OOM.
     */
    suspend fun compressAndSave(context: Context, sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val maxWidth = 1280

            // 1. Calcular el tamaño sin cargar la imagen en memoria (evita OOM)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // Si la imagen es inválida o está corrupta, salimos
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return@withContext null
            }

            // 2. Calcular inSampleSize
            var inSampleSize = 1
            if (options.outWidth > maxWidth) {
                inSampleSize = options.outWidth / maxWidth
            }

            // 3. Decodificar el bitmap reducido
            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val originalBitmap = context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null // Manejo de null si decodeStream falla

            // 4. Redimensionar si aún es muy grande
            val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            val newWidth = if (originalBitmap.width > maxWidth) maxWidth else originalBitmap.width
            val newHeight = (newWidth / ratio).toInt()

            val scaledBitmap = if (originalBitmap.width > maxWidth) {
                Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            } else {
                originalBitmap
            }

            // 5. Guardar en almacenamiento interno
            val file = File(context.filesDir, "img_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { outputStream ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            }

            // 6. Devolver la URI local
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}