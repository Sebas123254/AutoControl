package com.sierracode.autocontrol.data.repository

import com.sierracode.autocontrol.data.local.dao.VehicleDao
import com.sierracode.autocontrol.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface VehicleRepository {
    fun observeAll(): Flow<List<VehicleEntity>>
    fun observe(id: String): Flow<VehicleEntity?>
    suspend fun get(id: String): VehicleEntity?
    suspend fun upsert(v: VehicleEntity)
    suspend fun delete(id: String)
    suspend fun bumpOdometerIfHigher(id: String, odo: Long)
    suspend fun count(): Int

    suspend fun deleteMultiple(ids: List<String>)
}

@Singleton
class VehicleRepositoryImpl @Inject constructor(private val dao: VehicleDao) : VehicleRepository {
    override fun observeAll() = dao.observeAll()
    override fun observe(id: String) = dao.observe(id)
    override suspend fun get(id: String) = dao.get(id)
    override suspend fun upsert(v: VehicleEntity) = dao.upsert(v.copy(updatedAt = System.currentTimeMillis()))
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun bumpOdometerIfHigher(id: String, odo: Long) = dao.bumpOdometer(id, odo)
    override suspend fun count() = dao.count()
    override suspend fun deleteMultiple(ids: List<String>) = dao.deleteAllByIds(ids)
}