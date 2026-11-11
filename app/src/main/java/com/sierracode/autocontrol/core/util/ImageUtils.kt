package com.sierracode.autocontrol.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

object ImageUtils {
    /**
     * Comprime una imagen desde un Uri, la guarda en el almacenamiento interno
     * y devuelve el nuevo Uri local.
     */
    fun compressAndSave(context: Context, sourceUri: Uri): Uri? {
        return try {
            // 1. Leer la imagen original
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // 2. Redimensionar si es muy grande (máximo 1280px de ancho)
            val maxWidth = 1280
            val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            val newWidth = if (originalBitmap.width > maxWidth) maxWidth else originalBitmap.width
            val newHeight = (newWidth / ratio).toInt()

            val scaledBitmap = if (originalBitmap.width > maxWidth) {
                Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            } else {
                originalBitmap
            }

            // 3. Crear el archivo destino
            val file = File(context.filesDir, "img_${UUID.randomUUID()}.jpg")
            val outputStream = FileOutputStream(file)

            // 4. Comprimir a JPEG con calidad 80%
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.flush()
            outputStream.close()

            // 5. Devolver el URI local (file://)
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}