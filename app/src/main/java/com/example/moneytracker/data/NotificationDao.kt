// data/NotificationDao.kt
package com.example.moneytracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationItem(notification: NotificationItem)

    @Query("SELECT * FROM NotificationItem")
    fun getAllNotifications(): Flow<List<NotificationItem>>

    @Query("DELETE FROM NotificationItem")
    suspend fun clearNotifications()

    @Delete
    suspend fun deleteNotification(notification: NotificationItem)
}
