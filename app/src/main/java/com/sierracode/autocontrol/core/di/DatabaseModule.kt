package com.sierracode.autocontrol.core.di

import android.content.Context
import androidx.room.Room
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.data.local.MIGRATION_1_2
import com.sierracode.autocontrol.data.local.MIGRATION_2_3
import com.sierracode.autocontrol.data.local.MIGRATION_3_4
import com.sierracode.autocontrol.data.local.MIGRATION_4_5
import com.sierracode.autocontrol.data.local.MIGRATION_5_6 // <-- IMPORT NECESARIO
import com.sierracode.autocontrol.data.local.seed.CategorySeed
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDb(
        @ApplicationContext ctx: Context,
        @AppScope scope: CoroutineScope
    ): AutoControlDatabase {
        val db = Room.databaseBuilder(ctx, AutoControlDatabase::class.java, "autocontrol.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

        scope.launch(Dispatchers.IO) {
            val dao = db.categoryDao()
            if (dao.count() == 0) dao.upsertAll(CategorySeed.all())
        }

        return db
    }

    @Provides fun vehicleDao(db: AutoControlDatabase) = db.vehicleDao()
    @Provides fun categoryDao(db: AutoControlDatabase) = db.categoryDao()
    @Provides fun expenseDao(db: AutoControlDatabase) = db.expenseDao()
    @Provides fun fuelDao(db: AutoControlDatabase) = db.fuelDao()
    @Provides fun maintenanceDao(db: AutoControlDatabase) = db.maintenanceDao()
    @Provides fun scheduleDao(db: AutoControlDatabase) = db.scheduleDao()
    @Provides fun documentDao(db: AutoControlDatabase) = db.documentDao()
    @Provides fun reminderDao(db: AutoControlDatabase) = db.reminderDao()
    @Provides fun attachmentDao(db: AutoControlDatabase) = db.attachmentDao()
    @Provides fun goalDao(db: AutoControlDatabase) = db.goalDao()
}