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
    version = 6,
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