package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE active = 1") fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT COUNT(*) FROM categories") suspend fun count(): Int
    @Upsert suspend fun upsertAll(categories: List<CategoryEntity>)
}