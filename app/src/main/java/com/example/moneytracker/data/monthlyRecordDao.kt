package com.example.moneytracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MonthlyRecordDao {
    @Query("SELECT * FROM MonthlyRecord ORDER BY monthYear DESC")
    suspend fun getAll(): List<MonthlyRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MonthlyRecord)

    @Query("SELECT * FROM MonthlyRecord WHERE monthYear = :monthYear")
    suspend fun getByMonthYear(monthYear: String): MonthlyRecord?

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(record: MonthlyRecord)
}
