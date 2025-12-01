// data/NotificationItem.kt
package com.example.moneytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "NotificationItem")
data class NotificationItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val item: String,
    val cost: Double,
    val bank: String,
    val date: Long = System.currentTimeMillis()
)
