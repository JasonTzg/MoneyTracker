package com.example.moneytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "UserSettings")
data class UserSettings(
    @PrimaryKey val id: Int = 0, // singleton pattern
    val payday: Int,             // e.g., 28 means 28th of every month
    val setAmount: Double,
    val thresholdAmountPieChart: Int? = 5
)
