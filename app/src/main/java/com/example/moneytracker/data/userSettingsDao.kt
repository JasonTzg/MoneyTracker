package com.example.moneytracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM UserSettings WHERE id = 0")
    suspend fun getSettings(): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: UserSettings)

    @Query("UPDATE UserSettings SET setAmount = :amount WHERE id = 0")
    suspend fun updateSetAmount(amount: Double)

    @Query("UPDATE UserSettings SET payday = :day WHERE id = 0")
    suspend fun updatePayday(day: Int)

    @Query("UPDATE UserSettings SET thresholdAmountPieChart = :threshold WHERE id = 0")
    suspend fun updateThresholdAmountPieChart(threshold: Int)
}
