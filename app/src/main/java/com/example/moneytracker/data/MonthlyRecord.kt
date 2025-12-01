package com.example.moneytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "MonthlyRecord")
data class MonthlyRecord(
    @PrimaryKey val monthYear: String, // e.g., "2025-07"
    val jsonOfAllExpenses: String,     // serialized expense list
    val budget: Double                 // snapshot of setAmount when reset
)