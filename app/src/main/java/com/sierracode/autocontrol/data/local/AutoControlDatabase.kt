package com.sierracode.autocontrol.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration // <-- IMPORTANTE
import androidx.sqlite.db.SupportSQLiteDatabase // <-- IMPORTANTE
import com.sierracode.autocontrol.data.local.dao.*
import com.sierracode.autocontrol.data.local.entity.*

@Database(
    entities = [
        VehicleEntity::class, CategoryEntity::class, ExpenseEntity::class,
        FuelDetailEntity::class, MaintenanceDetailEntity::class,
        MaintenanceScheduleEntity::class, DocumentEntity::class,
        AttachmentEntity::class, ReminderEntity::class
    ],
    version = 3, // <-- CAMBIADO A 2
    exportSchema = true
)
abstract class AutoControlDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun fuelDao(): FuelDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun documentDao(): DocumentDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun reminderDao(): ReminderDao
}

// ESTO ES LO NUEVO: El puente entre la versión 1 y 2
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE documents ADD COLUMN reminderLeadDays INTEGER NOT NULL DEFAULT 30")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Borramos la tabla vieja para evitar conflictos con la nueva estructura
        database.execSQL("DROP TABLE IF EXISTS reminders")

        // Creamos la tabla nueva
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `reminders` (
                `id` TEXT NOT NULL, 
                `vehicleId` TEXT NOT NULL, 
                `sourceType` TEXT NOT NULL, 
                `sourceId` TEXT NOT NULL, 
                `title` TEXT NOT NULL, 
                `body` TEXT NOT NULL, 
                `triggerAtEpochMillis` INTEGER NOT NULL, 
                `enabled` INTEGER NOT NULL, 
                `lastNotifiedAt` INTEGER, 
                PRIMARY KEY(`id`), 
                FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_vehicleId` ON `reminders` (`vehicleId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_triggerAtEpochMillis` ON `reminders` (`triggerAtEpochMillis`)")
    }
}