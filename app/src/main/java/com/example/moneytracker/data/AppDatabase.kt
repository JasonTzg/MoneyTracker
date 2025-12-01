package com.example.moneytracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Expense::class, NotificationItem::class, UserSettings::class, MonthlyRecord::class, Category::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun notificationDao(): NotificationDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun monthlyRecordDao(): MonthlyRecordDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "money_tracker_db"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5).build()
                INSTANCE = instance
                instance
            }
        }

        // migration script for version 3 to 4
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new Category table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL);",
                )
                // Add categoryId column to Expense table
                db.execSQL(
                    "ALTER TABLE `expenses` ADD COLUMN `categoryId` INTEGER"
                )
            }
        }

        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new Category table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL);",
                )
                // Add categoryId column to Expense table
                db.execSQL(
                    "ALTER TABLE `expenses` ADD COLUMN `categoryId` INTEGER"
                )
                // add col for usersetting for threshold for piechart
                db.execSQL(
                    "ALTER TABLE `UserSettings` ADD COLUMN `thresholdAmountPieChart` INTEGER DEFAULT 5"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // add col for usersetting for threshold for piechart
                db.execSQL(
                    "ALTER TABLE `UserSettings` ADD COLUMN `thresholdAmountPieChart` INTEGER DEFAULT 5"
                )
            }
        }
    }

}