package com.sierracode.autocontrol.core.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object FileUtils {
    private const val ATTACHMENTS_DIR = "attachments"

    fun getAttachmentsDir(context: Context): File {
        val dir = File(context.filesDir, ATTACHMENTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dado un nombre de archivo relativo (ej. img_123.jpg), devuelve el objeto File físico.
     */
    fun getFile(context: Context, relativeFileName: String?): File? {
        if (relativeFileName.isNullOrBlank()) return null
        // Si por compatibilidad viene con un path viejo, extraemos solo el nombre
        val fileName = relativeFileName.substringAfterLast("/")
        return File(getAttachmentsDir(context), fileName)
    }

    /**
     * Devuelve una URI segura (content://) usando FileProvider para poder pasarla a Coil o Intent.
     */
    fun getContentUri(context: Context, relativeFileName: String?): Uri? {
        val file = getFile(context, relativeFileName) ?: return null
        if (!file.exists()) return null
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}