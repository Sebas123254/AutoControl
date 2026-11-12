package com.sierracode.autocontrol.data.repository

import com.sierracode.autocontrol.data.local.dao.*
import com.sierracode.autocontrol.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// --- ATTACHMENT REPOSITORY ---
interface AttachmentRepository {
    fun observeByOwner(type: String, id: String): Flow<List<AttachmentEntity>>
    suspend fun getForOwner(type: String, id: String): AttachmentEntity?
    suspend fun upsert(attachment: AttachmentEntity)
    suspend fun deleteById(id: String)
    suspend fun getAllOnce(): List<AttachmentEntity>
    suspend fun deleteAll()
}

@Singleton
class AttachmentRepositoryImpl @Inject constructor(private val dao: AttachmentDao) : AttachmentRepository {
    override fun observeByOwner(type: String, id: String) = dao.observeByOwner(type, id)
    override suspend fun getForOwner(type: String, id: String) = dao.getForOwner(type, id)
    override suspend fun upsert(attachment: AttachmentEntity) { dao.upsert(attachment) }
    override suspend fun deleteById(id: String) = dao.deleteById(id)
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun deleteAll() = dao.deleteAll()
}

// --- CATEGORY REPOSITORY ---
interface CategoryRepository {
    fun observeAll(): Flow<List<CategoryEntity>>
    suspend fun count(): Int
    suspend fun upsertAll(categories: List<CategoryEntity>)
}

@Singleton
class CategoryRepositoryImpl @Inject constructor(private val dao: CategoryDao) : CategoryRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun count() = dao.count()
    override suspend fun upsertAll(categories: List<CategoryEntity>) = dao.upsertAll(categories)
}

// --- FUEL REPOSITORY ---
interface FuelRepository {
    fun observeFuel(vehicleId: String): Flow<List<FuelDao.FuelWithExpense>>
    suspend fun getAllOnce(): List<FuelDetailEntity>
    suspend fun deleteAll()
    suspend fun upsert(detail: FuelDetailEntity)
}

@Singleton
class FuelRepositoryImpl @Inject constructor(private val dao: FuelDao) : FuelRepository {
    override fun observeFuel(vehicleId: String) = dao.observeFuel(vehicleId)
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun upsert(detail: FuelDetailEntity) = dao.upsert(detail)
}

// --- SCHEDULE REPOSITORY ---
interface ScheduleRepository {
    fun observeActive(vehicleId: String): Flow<List<MaintenanceScheduleEntity>>
    suspend fun getActiveByName(vehicleId: String, name: String): MaintenanceScheduleEntity?
    suspend fun upsert(schedule: MaintenanceScheduleEntity)
    suspend fun delete(scheduleId: String)
    suspend fun getAllOnce(): List<MaintenanceScheduleEntity>
    suspend fun deleteAll()
}

@Singleton
class ScheduleRepositoryImpl @Inject constructor(private val dao: ScheduleDao) : ScheduleRepository {
    override fun observeActive(vehicleId: String) = dao.observeActive(vehicleId)
    override suspend fun getActiveByName(vehicleId: String, name: String) = dao.getActiveByName(vehicleId, name)
    override suspend fun upsert(schedule: MaintenanceScheduleEntity) = dao.upsert(schedule)
    override suspend fun delete(scheduleId: String) = dao.deleteById(scheduleId)
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun deleteAll() = dao.deleteAll()
}

// --- REMINDER REPOSITORY ---
interface ReminderRepository {
    suspend fun getDueReminders(now: Long, startOfDay: Long): List<ReminderEntity>
    fun observeByVehicle(vehicleId: String): Flow<List<ReminderEntity>>
    suspend fun upsert(reminder: ReminderEntity)
    suspend fun deleteBySource(type: String, sourceId: String)
    suspend fun deleteById(id: String)
    suspend fun deleteAll()
    suspend fun markNotified(id: String, time: Long)
    suspend fun getAllOnce(): List<ReminderEntity>
}

@Singleton
class ReminderRepositoryImpl @Inject constructor(private val dao: ReminderDao) : ReminderRepository {
    override suspend fun getDueReminders(now: Long, startOfDay: Long) = dao.getDueReminders(now, startOfDay)
    override fun observeByVehicle(vehicleId: String) = dao.observeByVehicle(vehicleId)
    override suspend fun upsert(reminder: ReminderEntity) = dao.upsert(reminder)
    override suspend fun deleteBySource(type: String, sourceId: String) = dao.deleteBySource(type, sourceId)
    override suspend fun deleteById(id: String) = dao.deleteById(id)
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun markNotified(id: String, time: Long) = dao.markNotified(id, time)
    override suspend fun getAllOnce() = dao.getAllOnce()
}