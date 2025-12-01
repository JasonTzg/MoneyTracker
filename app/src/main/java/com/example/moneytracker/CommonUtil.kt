package com.example.moneytracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.moneytracker.data.Expense
import com.example.moneytracker.data.ExpenseDao
import com.example.moneytracker.data.MonthlyRecordDao
import com.example.moneytracker.data.UserSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.collections.forEachIndexed

class CommonUtil {
    companion object {

        fun calculateDaysToNextPayday(payday: Int): Int {
            val today = LocalDate.now()
            val currentDay = today.dayOfMonth

            val currentMonth = today.monthValue
            val currentYear = today.year

            val paydayThisMonth = try {
                LocalDate.of(currentYear, currentMonth, payday)
            } catch (e: Exception) {
                // if this month doesn't have that many days (e.g. 30 Feb)
                LocalDate.of(currentYear, currentMonth, today.lengthOfMonth())
            }

            val nextPayday = if (currentDay < payday && paydayThisMonth.dayOfMonth == payday) {
                paydayThisMonth
            } else {
                val nextMonthDate = today.plusMonths(1)
                val nextPaydayDate = try {
                    LocalDate.of(nextMonthDate.year, nextMonthDate.monthValue, payday)
                } catch (e: Exception) {
                    LocalDate.of(nextMonthDate.year, nextMonthDate.monthValue, nextMonthDate.lengthOfMonth())
                }
                nextPaydayDate
            }

            return ChronoUnit.DAYS.between(today, nextPayday).toInt()
        }

        fun formatLeftAmount(amount: Double): String {
            return if (amount < 0 || amount < 10) {
                "$${"%.2f".format(amount)}"
            } else {
                val intPart = amount.toInt().toString()
                val mask = when (intPart.length) {
                    2 -> "-"
                    3 -> "--"
                    4 -> "---"
                    else -> "----"
                }
                "\$${intPart.first()}$mask"
            }
        }

        fun exportExpensesToTxt(context: Context, expenses: List<Expense>) {
            val json = Gson().toJson(expenses)  // Convert to JSON string

            val fileName = "money_tracker_backup_${System.currentTimeMillis()}.txt"
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName)
            file.writeText(json)

            Toast.makeText(context, "Exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }

        fun exportExpensesToExcel(context: Context, expenses: List<Expense>) {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Expenses")
            // Header
            val header = sheet.createRow(0)
            header.createCell(0).setCellValue("Date")
            header.createCell(1).setCellValue("Item")
            header.createCell(2).setCellValue("Cost")
            header.createCell(3).setCellValue("Bank")
            header.createCell(4).setCellValue("Category")

            // Data rows
            expenses.forEachIndexed { index, expense ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(expense.date))
                )
                row.createCell(1).setCellValue(expense.item)
                row.createCell(2).setCellValue(expense.cost)
                row.createCell(3).setCellValue(expense.bank)
                row.createCell(4).setCellValue(expense.categoryId?.toString() ?: "")
            }

            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "money_tracker_export_$time.xlsx"
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )

            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()

            Toast.makeText(context, "Exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }

        fun importExpensefromExcel(context: Context, fileUri: Uri, expenseDao: ExpenseDao) {
            val inputStream = context.contentResolver.openInputStream(fileUri)
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            val expenses = mutableListOf<Expense>()
            for (rowIndex in 1..sheet.lastRowNum) { // Skip header
                val row = sheet.getRow(rowIndex) ?: continue

                val dateCell = row.getCell(0)
                val itemCell = row.getCell(1)
                val costCell = row.getCell(2)
                val bankCell = row.getCell(3)
                val categoryCell = row.getCell(4)

                if (itemCell != null && costCell != null && bankCell != null) {
                    // ✅ Parse date flexibly
                    val date: Long = when (dateCell?.cellType) {
                        CellType.NUMERIC -> dateCell.dateCellValue?.time ?: System.currentTimeMillis()
                        CellType.STRING -> {
                            try {
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                    .parse(dateCell.stringCellValue)?.time ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }
                        }
                        else -> System.currentTimeMillis()
                    }

                    val item = itemCell.stringCellValue
                    val cost = when (costCell.cellType) {
                        CellType.NUMERIC -> costCell.numericCellValue
                        CellType.STRING -> costCell.stringCellValue.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }

                    val bank = bankCell.stringCellValue

                    val categoryId: Int? = when (categoryCell?.cellType) {
                        CellType.NUMERIC -> categoryCell.numericCellValue.toInt()
                        CellType.STRING -> categoryCell.stringCellValue.toIntOrNull()
                        else -> null
                    }

                    expenses.add(
                        Expense(
                            item = item,
                            cost = cost,
                            bank = bank,
                            date = date,
                            categoryId = categoryId
                        )
                    )
                }
            }
            workbook.close()

            // 🚀 Replace entire DB contents
            CoroutineScope(Dispatchers.IO).launch {
                expenseDao.clearAll()
                expenseDao.insertAllExpenses(expenses)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Replaced with ${expenses.size} expenses", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun addBulkExpensefromExcel(context: Context, fileUri: Uri, expenseDao: ExpenseDao) {
            val inputStream = context.contentResolver.openInputStream(fileUri)
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            val expenses = mutableListOf<Expense>()
            for (rowIndex in 1..sheet.lastRowNum) { // Skip header
                val row = sheet.getRow(rowIndex) ?: continue

                val dateCell = row.getCell(0)
                val itemCell = row.getCell(1)
                val costCell = row.getCell(2)
                val bankCell = row.getCell(3)
                val categoryCell = row.getCell(4)

                if (itemCell != null && costCell != null && bankCell != null) {
                    // ✅ Fix for date
                    val date: Long = when (dateCell?.cellType) {
                        CellType.NUMERIC -> dateCell.dateCellValue?.time ?: System.currentTimeMillis()
                        CellType.STRING -> {
                            try {
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                    .parse(dateCell.stringCellValue)?.time ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }
                        }
                        else -> System.currentTimeMillis()
                    }

                    val item = itemCell.stringCellValue
                    val cost = when (costCell.cellType) {
                        CellType.NUMERIC -> costCell.numericCellValue
                        CellType.STRING -> costCell.stringCellValue.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }

                    val bank = bankCell.stringCellValue

                    // ✅ Fix for category
                    val categoryId: Int? = when (categoryCell?.cellType) {
                        CellType.NUMERIC -> categoryCell.numericCellValue.toInt()
                        CellType.STRING -> categoryCell.stringCellValue.toIntOrNull()
                        else -> null
                    }

                    expenses.add(
                        Expense(
                            item = item,
                            cost = cost,
                            bank = bank,
                            date = date,
                            categoryId = categoryId
                        )
                    )
                }
            }
            workbook.close()

            CoroutineScope(Dispatchers.IO).launch {
                expenseDao.insertAllExpenses(expenses)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Imported ${expenses.size} expenses", Toast.LENGTH_SHORT).show()
                }
            }
        }


        fun importExpensesFromTxt(context: Context, fileUri: Uri, expenseDao: ExpenseDao) {
            val inputStream = context.contentResolver.openInputStream(fileUri)
            inputStream?.bufferedReader().use { reader ->
                val json = reader?.readText() ?: return
                val type = object : TypeToken<List<Expense>>() {}.type
                val expenses: List<Expense> = Gson().fromJson(json, type)

                // Insert expenses into the database
                CoroutineScope(Dispatchers.IO).launch {
                    expenseDao.insertAllExpenses(expenses)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Imported ${expenses.size} expenses", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        suspend fun exportExpensesFromJSON(context: Context, expenses: String) {
            // convert JSON string to List<Expense>, then list down to excel
            val wrapperType = object : TypeToken<Map<String, List<Expense>>>() {}.type
            val wrapper: Map<String, List<Expense>> = Gson().fromJson(expenses, wrapperType)
            val expensesList = wrapper["expenses"] ?: emptyList()
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Expenses")
            // Header
            val header = sheet.createRow(0)
            header.createCell(0).setCellValue("Date")
            header.createCell(1).setCellValue("Item")
            header.createCell(2).setCellValue("Cost")
            header.createCell(3).setCellValue("Bank")
            header.createCell(4).setCellValue("Category")

            // Data rows
            expensesList.forEachIndexed { index, expense ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(expense.date))
                )
                row.createCell(1).setCellValue(expense.item)
                row.createCell(2).setCellValue(expense.cost)
                row.createCell(3).setCellValue(expense.bank)
                row.createCell(4).setCellValue(expense.categoryId?.toString() ?: "")
            }

            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "money_tracker_export_$time.xlsx"
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )

            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        }

        fun requestNotificationAccess(context: Context) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            context.startActivity(intent)
        }
        fun sendTestNotification(context: Context) {
            val channelId = "test_channel"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create channel for Android 8+
            val channel =
                NotificationChannel(channelId, "Test Channel", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle("KOO KEE - AMK")
                .setContentText("SGD17.90 paid with Mastercard ... 2468")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        }

        fun handleMonthlyResetIfNeeded(
            expenseDao: ExpenseDao,
            monthlyRecordDao: MonthlyRecordDao,
            userSettings: UserSettings,
            coroutineScope: CoroutineScope
        ) {
            coroutineScope.launch {
                val today = Calendar.getInstance()
                val resetCheckDate = today.clone() as Calendar

                // Get this month's payday date
                resetCheckDate.set(Calendar.DAY_OF_MONTH, userSettings.payday)

                // If today is the day after payday, do the reset
                resetCheckDate.add(Calendar.DAY_OF_MONTH, 1)  // Move to the day *after* payday

                val isResetDay = today.get(Calendar.YEAR) == resetCheckDate.get(Calendar.YEAR) &&
                        today.get(Calendar.MONTH) == resetCheckDate.get(Calendar.MONTH) &&
                        today.get(Calendar.DAY_OF_MONTH) == resetCheckDate.get(Calendar.DAY_OF_MONTH)

                if (isResetDay) {
                    val monthYear = SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(today.time)
                    val existingRecord = monthlyRecordDao.getByMonthYear(monthYear)

                    // Only reset if not yet done (budget is still 0.0)
                    if (existingRecord != null && existingRecord.budget == 0.0) {
                        val expenses = expenseDao.getAllExpenses()
                        val gson = Gson()
                        val json = gson.toJson(expenses)

                        // Update the monthly record with expense data and budget
                        monthlyRecordDao.update(
                            existingRecord.copy(
                                jsonOfAllExpenses = json,
                                budget = userSettings.setAmount
                            )
                        )

                        // Clear the expense table
                        expenseDao.clearAll()
                    }
                }
            }
        }

    }
}