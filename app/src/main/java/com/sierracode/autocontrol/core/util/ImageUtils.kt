package com.sierracode.autocontrol.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageUtils {
    /**
     * Comprime una imagen, corrige su orientación EXIF, la guarda en filesDir/attachments/
     * y devuelve SOLO EL NOMBRE DEL ARCHIVO (ej. img_123.jpg).
     */
    suspend fun compressAndSave(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
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
            var bitmap = context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null

            // Corregir orientación EXIF
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                }
                if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (rotated != bitmap) {
                        bitmap.recycle()
                        bitmap = rotated
                    }
                }
            }

            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val newWidth = if (bitmap.width > maxWidth) maxWidth else bitmap.width
            val newHeight = (newWidth / ratio).toInt()

            val finalBitmap = if (bitmap.width > maxWidth) {
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also { bitmap.recycle() }
            } else {
                bitmap
            }

            val fileName = "img_${UUID.randomUUID()}.jpg"
            val destFile = File(FileUtils.getAttachmentsDir(context), fileName)

            FileOutputStream(destFile).use { outputStream ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            }
            finalBitmap.recycle()

            fileName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copia un archivo (ej. PDF) a filesDir/attachments/ y devuelve SOLO EL NOMBRE.
     */
    suspend fun copyToInternalStorage(context: Context, sourceUri: Uri, extension: String): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "doc_${UUID.randomUUID()}.$extension"
            val destFile = File(FileUtils.getAttachmentsDir(context), fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            fileName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Borra un archivo físico a partir de su nombre relativo.
     */
    fun deleteLocalFile(context: Context, relativeFileName: String?) {
        if (relativeFileName.isNullOrBlank()) return
        try {
            val file = FileUtils.getFile(context, relativeFileName)
            if (file != null && file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Crea un archivo temporal para la cámara en la subcarpeta correcta.
     */
    fun createTempImageUri(context: Context): Uri? {
        return try {
            val fileName = "camera_${UUID.randomUUID()}.jpg"
            val file = File(FileUtils.getAttachmentsDir(context), fileName)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}