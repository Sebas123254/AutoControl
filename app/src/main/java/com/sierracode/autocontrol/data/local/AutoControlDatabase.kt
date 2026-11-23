package com.sierracode.autocontrol.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sierracode.autocontrol.data.local.dao.*
import com.sierracode.autocontrol.data.local.entity.*

@Database(
    entities = [
        VehicleEntity::class, CategoryEntity::class, ExpenseEntity::class,
        FuelDetailEntity::class, MaintenanceDetailEntity::class,
        MaintenanceScheduleEntity::class, DocumentEntity::class,
        AttachmentEntity::class, ReminderEntity::class, GoalEntity::class
    ],
    version = 10,
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
    abstract fun goalDao(): GoalDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE documents ADD COLUMN reminderLeadDays INTEGER NOT NULL DEFAULT 30")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS reminders")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `reminders` (
                `id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `sourceType` TEXT NOT NULL, 
                `sourceId` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, 
                `triggerAtEpochMillis` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, 
                `lastNotifiedAt` INTEGER, PRIMARY KEY(`id`), 
                FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_vehicleId` ON `reminders` (`vehicleId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_triggerAtEpochMillis` ON `reminders` (`triggerAtEpochMillis`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `goals` (
                `id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `title` TEXT NOT NULL, 
                `notes` TEXT, `estimatedCents` INTEGER NOT NULL, `estimatedMaxCents` INTEGER, 
                `priority` TEXT NOT NULL, `targetDateEpochDay` INTEGER, `status` TEXT NOT NULL, 
                `categoryId` TEXT, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER, 
                `linkedExpenseId` TEXT, PRIMARY KEY(`id`), 
                FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_vehicleId` ON `goals` (`vehicleId`)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("UPDATE expenses SET categoryId = 'other_misc' WHERE categoryId = 'rep_other'")
        database.execSQL("DELETE FROM categories WHERE id = 'rep_other'")
        database.execSQL("UPDATE categories SET name = 'Otro' WHERE id = 'other_misc'")
        database.execSQL("ALTER TABLE expenses ADD COLUMN customCategoryName TEXT")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE vehicles ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'MXN'")
        database.execSQL("ALTER TABLE vehicles ADD COLUMN unitSystem TEXT NOT NULL DEFAULT 'METRIC'")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE reminders ADD COLUMN templateKey TEXT")
        database.execSQL("ALTER TABLE reminders ADD COLUMN templateArgs TEXT")
        database.execSQL(
            "UPDATE reminders SET templateKey = 'document_expiring' " +
                    "WHERE sourceType = 'DOCUMENT' AND templateKey IS NULL"
        )
        database.execSQL(
            "UPDATE reminders SET templateKey = 'maintenance_due' " +
                    "WHERE sourceType = 'MAINTENANCE' AND templateKey IS NULL"
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. Nuevo campo para evitar repetir el mismo hito de notificación.
        // NULL = nunca notificado en su ciclo actual.
        database.execSQL("ALTER TABLE reminders ADD COLUMN lastNotifiedStage TEXT")

        // 2. Backfill: como el bug anterior hacía que se notificara CADA DÍA
        // después del trigger, marcamos todos los reminders viejos como
        // "ya notificados" para detener el spam inmediatamente. Si el usuario
        // edita el reminder (cambia fecha), el registro se re-crea con stage=null
        // y empieza a notificar de nuevo.
        database.execSQL("UPDATE reminders SET lastNotifiedStage = 'legacy_migrated' WHERE enabled = 1")

        // 3. Deduplicación: el bug anterior creaba un reminder NUEVO cada vez
        // que se editaba un mantenimiento, sin borrar el anterior. Nos quedamos
        // solo con el reminder que tenga el triggerAtEpochMillis MÁS RECIENTE
        // (el que refleja el próximo servicio real).
        database.execSQL("""
            DELETE FROM reminders
            WHERE id NOT IN (
                SELECT r.id FROM reminders r
                WHERE r.triggerAtEpochMillis = (
                    SELECT MAX(r2.triggerAtEpochMillis)
                    FROM reminders r2
                    WHERE r2.sourceType = r.sourceType AND r2.sourceId = r.sourceId
                )
            )
        """.trimIndent())
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE expenses ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'MXN'")
        database.execSQL("""
            UPDATE expenses
            SET currencyCode = COALESCE(
                (SELECT currencyCode FROM vehicles WHERE vehicles.id = expenses.vehicleId),
                'MXN'
            )
        """.trimIndent())
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE goals ADD COLUMN savedCents INTEGER NOT NULL DEFAULT 0")
    }
}