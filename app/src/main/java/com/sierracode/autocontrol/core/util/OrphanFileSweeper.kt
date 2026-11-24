package com.sierracode.autocontrol.core.util

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SweepResult(val deletedFiles: Int, val freedBytes: Long)

private const val TAG = "OrphanFileSweeper"

@Singleton
class OrphanFileSweeper @Inject constructor(
    private val db: AutoControlDatabase,
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()

    /**
     * Barre y elimina archivos huérfanos dentro de attachmentsDir.
     * Solo borra archivos no referenciados por ninguna entidad de la BD
     * cuya última fecha de modificación sea anterior a (now - minAgeMs).
     *
     * @param minAgeMs ventana de gracia mínima (por defecto 24h = 86_400_000 ms).
     */
    suspend fun sweep(minAgeMs: Long = 24L * 60 * 60 * 1000): SweepResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val attachmentsDir = FileUtils.getAttachmentsDir(context)
            val filesOnDisk = attachmentsDir.listFiles()?.filter { it.isFile } ?: emptyList()
            if (filesOnDisk.isEmpty()) {
                return@withLock SweepResult(0, 0L)
            }

            // 1. Leer conjunto de nombres referenciados dentro de una transacción Room
            val referencedNames = db.withTransaction {
                val names = mutableSetOf<String>()

                // attachments.uri
                db.attachmentDao().getAllOnce().forEach { att ->
                    if (att.uri.isNotBlank()) {
                        names.add(att.uri.substringAfterLast("/"))
                    }
                }

                // documents.fileUri
                db.documentDao().getAllOnce().forEach { doc ->
                    doc.fileUri?.let { uri ->
                        if (uri.isNotBlank()) {
                            names.add(uri.substringAfterLast("/"))
                        }
                    }
                }

                // vehicles.photoUri
                db.vehicleDao().getAllOnce().forEach { vehicle ->
                    vehicle.photoUri?.let { uri ->
                        if (uri.isNotBlank()) {
                            names.add(uri.substringAfterLast("/"))
                        }
                    }
                }

                names
            }

            // 2. Filtrar y eliminar archivos no referenciados con antigüedad mayor a minAgeMs
            val cutoffTime = System.currentTimeMillis() - minAgeMs
            var deletedCount = 0
            var freedBytes = 0L

            filesOnDisk.forEach { file ->
                val fileName = file.name
                if (!referencedNames.contains(fileName)) {
                    val lastModified = file.lastModified()
                    if (lastModified < cutoffTime) {
                        val size = file.length()
                        if (file.delete()) {
                            deletedCount++
                            freedBytes += size
                        }
                    }
                }
            }

            Log.i(TAG, "Barrido completado: $deletedCount archivos eliminados, $freedBytes bytes liberados")
            SweepResult(deletedCount, freedBytes)
        }
    }
}