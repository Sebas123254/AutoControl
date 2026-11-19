package com.sierracode.autocontrol

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sierracode.autocontrol.core.di.AppScope
import com.sierracode.autocontrol.core.util.FileUtils
import com.sierracode.autocontrol.core.util.OrphanFileSweeper
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.worker.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "AutoControlApp"

@HiltAndroidApp
class AutoControlApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var database: AutoControlDatabase
    @Inject lateinit var orphanFileSweeper: OrphanFileSweeper
    @Inject @field:AppScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.schedule(this)

        // Migración de archivos viejos y barrido de huérfanos
        appScope.launch {
            try {
                migrateFilesAndDatabase()
                orphanFileSweeper.sweep()
            } catch (e: Exception) {
                Log.e(TAG, "Error en migración inicial o barrido de huérfanos", e)
            }
        }
    }

    private suspend fun migrateFilesAndDatabase() {
        val attachmentsDir = FileUtils.getAttachmentsDir(this)
        val rootFiles = filesDir.listFiles() ?: return

        // 1. Mover archivos sueltos a la subcarpeta
        rootFiles.forEach { file ->
            if (file.isFile && (file.name.startsWith("img_") || file.name.startsWith("doc_") || file.name.startsWith("camera_"))) {
                val dest = File(attachmentsDir, file.name)
                if (!dest.exists()) file.renameTo(dest) else file.delete()
            }
        }

        // 2. Normalizar rutas en la Base de Datos (quitar file:///...)
        val attachments = database.attachmentDao().getAllOnce()
        attachments.forEach { att ->
            if (att.uri.contains("/")) {
                val fileName = att.uri.substringAfterLast("/")
                database.attachmentDao().upsert(att.copy(uri = fileName))
            }
        }

        val documents = database.documentDao().getAllOnce()
        documents.forEach { doc ->
            if (doc.fileUri?.contains("/") == true) {
                val fileName = doc.fileUri.substringAfterLast("/")
                database.documentDao().upsert(doc.copy(fileUri = fileName))
            }
        }
    }
}