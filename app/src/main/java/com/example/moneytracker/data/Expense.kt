package com.example.moneytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val item: String,
    val cost: Double,
    val bank: String,
    val date: Long = System.currentTimeMillis(),
    val categoryId: Int? = null // Nullable to allow expenses without a category
)
