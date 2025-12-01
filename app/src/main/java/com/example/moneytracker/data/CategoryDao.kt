package com.example.moneytracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert suspend fun insert(category: Category): Long

    @Query("SELECT * FROM categories") fun getAll(): Flow<List<Category>>

    @Update suspend fun update(category: Category)

    @Query("DELETE FROM categories WHERE id = :categoryId") suspend fun deleteById(categoryId: Long)
}
