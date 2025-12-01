package com.example.moneytracker

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.example.moneytracker.CommonUtil.Companion.exportExpensesFromJSON
import com.example.moneytracker.CommonUtil.Companion.exportExpensesToExcel
import com.example.moneytracker.CommonUtil.Companion.formatLeftAmount
import com.example.moneytracker.CommonUtil.Companion.handleMonthlyResetIfNeeded
import com.example.moneytracker.CommonUtil.Companion.addBulkExpensefromExcel
import com.example.moneytracker.CommonUtil.Companion.importExpensefromExcel
import com.example.moneytracker.CommonUtil.Companion.requestNotificationAccess
import com.example.moneytracker.SettingsDialog
import com.example.moneytracker.SettingsMonthSelectionToExport
import com.example.moneytracker.ui.theme.MoneyTrackerTheme
import com.example.moneytracker.data.*
import com.github.mikephil.charting.charts.PieChart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)
        setContent {
            MoneyTrackerTheme(
                darkTheme = true
            ) {
                Surface(
                    color = Color(0xFF262626), // dark background
                    modifier = Modifier.fillMaxSize()
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides Color.White // set default text color to white
                    ) {
                        AppContent()
                    }
                }
            }
        }
    }
}

@SuppressLint("ContextCastToActivity")
@Preview
@Composable
fun AppContent() {
    val context = LocalContext.current

    val db = remember { AppDatabase.getDatabase(context) }
    val expenseDao = db.expenseDao()
    val notificationDao = db.notificationDao()
    val monthlyRecordDao = db.monthlyRecordDao()
    val categoryDao = db.categoryDao()
    val allExistingMonths = remember { mutableStateListOf<MonthlyRecord>() }
    val userSettingsDao = db.userSettingsDao()          // only got 1 row of data because its user settings.
    val coroutineScope = rememberCoroutineScope()


    var showCategoryDialog by remember { mutableStateOf(false) }
    var showExpensesDialog by remember { mutableStateOf(false) }
    val expenseList = remember { mutableStateListOf<Expense>() }
    val categoryList = remember { mutableStateListOf<Category>() }
    var userSettings by remember { mutableStateOf<UserSettings?>(null) }

    LaunchedEffect(Unit) {
        // check if this month is added into monthlyRecordDao
        val currentMonth = SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(Date())
        val existingRecord = monthlyRecordDao.getByMonthYear(currentMonth)
        if (existingRecord == null) {
            // If no record exists for this month, create a new one
            monthlyRecordDao.insert(
                MonthlyRecord(
                    monthYear = currentMonth,
                    jsonOfAllExpenses = "[]", // Empty JSON array for expenses
                    budget = 0.0 // Default budget, can be dynamic or user-configurable
                )
            )
        }

        // Load all existing months into the list
        allExistingMonths.clear()
        allExistingMonths.addAll(monthlyRecordDao.getAll().filter { it.monthYear != currentMonth })

        // Load user settings from the database. If no settings exist, create default settings.
        userSettings = userSettingsDao.getSettings()
        if (userSettings == null) {
            // If no settings exist, create default settings
            userSettings = UserSettings(payday = 28, setAmount = 800.0)
            userSettingsDao.saveSettings(userSettings!!)
        }

        // update monthlyRecordDao of the jsonOfAllExpenses by using the expenses table list, when the payday is reached or resets.
        handleMonthlyResetIfNeeded(
            monthlyRecordDao = monthlyRecordDao,
            expenseDao = expenseDao,
            userSettings = userSettings!!,
            coroutineScope = coroutineScope
        )

        // Load all expenses from the database and update the expenseList. This needs to be below because it will constantly update the expenseList.
        expenseDao.getAllExpenses().collectLatest { expensesFromDb ->
            expenseList.clear()
            expenseList.addAll(expensesFromDb)
        }
    }

    // Load all categories from the database. This needs to have another LaunchedEffect because it will constantly update the categoryList.
    LaunchedEffect(Unit) {
        categoryDao.getAll().collectLatest { categoriesFromDb ->
            categoryList.clear()
            categoryList.addAll(categoriesFromDb)
        }
    }

    val notifications by notificationDao.getAllNotifications().collectAsState(initial = emptyList())

    val totalSpent = expenseList.sumOf { it.cost }
    val leftThisMonth = userSettings?.setAmount?.minus(totalSpent) ?: 0.0
    val daysToNextPayday = CommonUtil.calculateDaysToNextPayday(userSettings?.payday ?: 1)

    val activity = LocalContext.current as ComponentActivity

    val filePickerLauncherReplace = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                activity.lifecycleScope.launch {
                    importExpensefromExcel(context, uri, expenseDao)
                }
            }
        }
    )

    val filePickerLauncherAdd = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                activity.lifecycleScope.launch {
                    addBulkExpensefromExcel(context, uri, expenseDao)
                }
            }
        }
    )

    // Manual submit section
    var itemText by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    val bankOptions = listOf("CHOCO", "POSB", "UOB", "GXS", "DBS", "PAYLAH", "GP")
    var selectedBank by remember { mutableStateOf(bankOptions[0]) }

    // "Bank" - Pair of background color and text color - R.drawable bank logo
    val bankColorTextcolorPiclink: Map<String, Pair<Pair<Color, Color>, Int>> = mapOf(
        "GXS" to Pair(Pair(Color(0xFF782FFF), Color.White), R.drawable.gxs), // useless to change color here, cuz is gradient
        "GP" to Pair(Pair(Color(0xFF1778E3), Color.Black), R.drawable.gpay), // useless to change color here, cuz is gradient
        "CHOCO" to Pair(Pair(Color(0xFF493117), Color.White), R.drawable.choco),
        "POSB" to Pair(Pair(Color(0xFF0055A4), Color(0xFFFF8300)), R.drawable.posb),
        "UOB" to Pair(Pair(Color(0xFF0009E8), Color(0xFFFF6969)), R.drawable.uob),
        "DBS" to Pair(Pair(Color(0xFFED1C24), Color.Black), R.drawable.dbs),
        "PAYLAH" to Pair(Pair(Color(0xFFED1C24), Color.Black), R.drawable.paylah),
    )

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // ----------------------------------------------------------------------------------------------- Main
    ModalNavigationDrawer(
        drawerContent = {
            // Your navigation drawer content here
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(240.dp)
                    .background(Color.DarkGray)
                    .padding(16.dp)
            ) {
                // Open a prompt box button to change /update user settings - payday and or setAmount
                var showSettingsDialog by remember { mutableStateOf(false) }
                Button(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Settings")
                }
                if (showSettingsDialog) {
                    Dialog(onDismissRequest = { showSettingsDialog = false }) {
                        SettingsDialog(
                            currentSettings = userSettings,
                            onDismiss = { showSettingsDialog = false },
                            onSave = { newPayday, newSetAmount, newThreshold ->
                                coroutineScope.launch {
                                    userSettingsDao.saveSettings(
                                        UserSettings(id=0, payday = newPayday, setAmount = newSetAmount, thresholdAmountPieChart = newThreshold)
                                    )
                                    userSettings = userSettingsDao.getSettings() // Refresh settings
                                    showSettingsDialog = false
                                }
                            }
                        )
                    }
                }

                //------------------------------------------------------------------------------------ Navigation buttons

                Spacer(modifier = Modifier.height(16.dp))

                var showExportDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {exportExpensesToExcel(context, expenseList) }
                    ){
                        Text("Export this mth Expenses")
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row (
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = { showExportDialog = true }) {
                        Text("Export Expenses")
                    }
                }


                if (showExportDialog) {
                    Dialog(onDismissRequest = { showExportDialog = false }) {
                        SettingsMonthSelectionToExport(
                            monthlyRecords = allExistingMonths,
                            onMonthSelected = { monthYear ->
                                coroutineScope.launch {
                                    val record = monthlyRecordDao.getByMonthYear(monthYear)
                                    if (record != null) {
                                        exportExpensesFromJSON(context, record.jsonOfAllExpenses)
                                    }
                                }
                            },
                            onDismiss = { showExportDialog = false }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = {
                        filePickerLauncherAdd.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    }) {
                        Text("Import Bulk Expenses")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    var showImportWarning by remember { mutableStateOf(false) }
                    Button(onClick = { showImportWarning = true }) {
                        Text("Replace entire Expenses")
                    }
                    if (showImportWarning) {
                        AlertDialog(
                            onDismissRequest = { showImportWarning = false },
                            title = { Text("Warning") },
                            text = { Text("Importing will clear the current expense list. Continue?") },
                            confirmButton = {
                                Button(onClick = {
                                    showImportWarning = false
                                    filePickerLauncherReplace.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                }) { Text("Yes") }
                            },
                            dismissButton = {
                                Button(onClick = { showImportWarning = false }) { Text("No") }
                            }
                        )
                    }
                }
                // ------------------------------------------------------------------------------------------------------------------------

                if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
                    Button(onClick = { requestNotificationAccess(context) }) {
                        Text("Enable Notification Access")
                    }
                }
            }
        },
        drawerState = drawerState,
        gesturesEnabled = true,

    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                    // Left right item with space in between with .between
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    )
                    // ------------------------------------------------------------------------------- Top Row
                    {
                        Column(
                        ) {
                            Text(
                                text = "Left this month: ${formatLeftAmount(leftThisMonth)}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showExpensesDialog = true }
                            )
                            Text(
                                text = "$daysToNextPayday Days to next payday",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Light,
                                fontStyle = FontStyle.Italic
                            )
                        }

                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Settings, // Use your desired icon
                                contentDescription = "Open Navigation"
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // ----------------------------------------------------------------------------------- Notification Box Checklist
                Text("Found in Notification:", fontSize = 18.sp, fontWeight = FontWeight.Medium)

                val scrollState = rememberLazyListState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp) // Slightly taller to allow padding and border
                        .border(1.dp, Color.Gray, shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    NotificationBox(
                        scrollState = scrollState,
                        notifications = notifications,
                        bankColorDictWithTextColor = bankColorTextcolorPiclink,
                        onDeleteNotification = { notification ->
                            coroutineScope.launch {
                                notificationDao.deleteNotification(notification)
                            }
                        },
                        onCategorySubmit = { selectedCategory, newCategoryName, itemText, costText, bankText, notification ->
                            coroutineScope.launch {
                                val categoryId = if (selectedCategory == "New Category" && newCategoryName.isNotBlank()) {
                                    val newId = categoryDao.insert(Category(name = newCategoryName)).toInt()
                                    newId
                                } else {
                                    categoryList.find { it.name == selectedCategory }?.id ?: 0
                                }
                                expenseDao.insertExpense(
                                    Expense(item = itemText, cost = costText.toDouble(), bank = bankText, categoryId = categoryId)
                                )
                                // After submitting, delete the notification
                                notificationDao.deleteNotification(notification)
                            }
                        },
                        expenseList = expenseList,
                        categoryList = categoryList,
                        userSettings = userSettings
                    )
                }

                Spacer(modifier = Modifier.height(35.dp))

                // ----------------------------------------------------------------------------------- Manual Form Submit Section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = itemText,
                        onValueChange = { itemText = it },
                        label = { Text("Item") },
                        modifier = Modifier
                            .weight(1.5f)
                            .padding(end = 1.dp),
                        singleLine = true,
                        maxLines = 1
                    )
                    TextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Cost") },
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(end = 1.dp),
                        singleLine = true,
                        maxLines = 1
                    )
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(.5f)) {
                        val bankKey = selectedBank.uppercase()
                        val bankColor = when (bankKey) {
                            "GXS" -> listOf(
                                Color(0xFF782FFF),
                                Color(0xFFF553B0)
                            )

                            "GP" -> listOf(
                                Color(0xFF1778E3),
                                Color(0xFFCD5050),
                                Color(0xFFE7C16F),
                                Color(0xFF268933)
                            )

                            else -> {
                                listOf(
                                    bankColorTextcolorPiclink[bankKey]?.first?.first
                                        ?: Color.Yellow.copy(alpha = 0.5f),
                                    bankColorTextcolorPiclink[bankKey]?.first?.first
                                        ?: Color.Yellow.copy(alpha = 0.5f)
                                )
                            }
                        }
                        val textColor = bankColorTextcolorPiclink[bankKey]?.first?.second ?: Color.Black

                        Text(
                            text = selectedBank,
                            modifier = Modifier
                                .clickable { expanded = true }
                                .padding(2.dp)
                                .background(
                                    Brush.horizontalGradient(bankColor),
                                    shape = MaterialTheme.shapes.small
                                )
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = textColor,
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            bankOptions.forEach { bank ->
                                DropdownMenuItem(text = { Text(bank) }, onClick = {
                                    selectedBank = bank
                                    expanded = false
                                })
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 15.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = {
                        val cost = costText.toDoubleOrNull()
                        if (itemText.isNotBlank() && cost != null) {
                            showCategoryDialog = true  // open popup
                        }
                    }) {
                        Text("Submit")
                    }
                }

            }

            // --------------------------------------------------------------------------------------- Expense Dialog Popup
            if (showExpensesDialog) {
                Dialog(onDismissRequest = { showExpensesDialog = false }) {
                    ReviewExpenseBox(
                        expenseList = expenseList,
                        bankColorTextcolorPiclink = bankColorTextcolorPiclink,
                        onDismiss = { showExpensesDialog = false },
                        onCategoryUpdate = { selectedCategory, newCategoryName, expenseSelected ->
                            coroutineScope.launch {
                                expenseSelected?.let { expense ->
                                    val categoryId = if (selectedCategory == "New Category" && newCategoryName.isNotBlank()) {
                                        val newId = categoryDao.insert(Category(name = newCategoryName)).toInt()
                                        newId
                                    } else {
                                        categoryList.find { it.name == selectedCategory }?.id ?: 0
                                    }
                                    val updatedExpense = expense.copy(categoryId = categoryId)
                                    expenseDao.updateExpense(updatedExpense)
                                }
                            }
                        },
                        categoryList = categoryList,
                        userSettings = userSettings
                    )
                }
            }

            // --------------------------------------------------------------------------------------- Category Selection Dialog Popup
            if (showCategoryDialog) {
                Dialog(onDismissRequest = { /* Do nothing to prevent accidental close */ }) {
                    ReviewCategoryBox(
                        categoryList = categoryList,
                        onDismiss = { showCategoryDialog = false },
                        onSubmit = { selectedCategory, newCategoryName ->
                            coroutineScope.launch {
                                val categoryId = if (selectedCategory == "New Category" && newCategoryName.isNotBlank()) {
                                    val newId = categoryDao.insert(Category(name = newCategoryName)).toInt()
                                    newId
                                } else {
                                    categoryList.find { it.name == selectedCategory }?.id ?: 0
                                }
                                expenseDao.insertExpense(
                                    Expense(item = itemText, cost = costText.toDouble(), bank = selectedBank, categoryId = categoryId)
                                )
                                itemText = ""
                                costText = ""
                                showCategoryDialog = false
                            }
                        },
                        itemText = itemText,
                        costText = costText,
                        selectedBank = selectedBank,
                        expenseList = expenseList,
                        userSettings = userSettings
                    )
                }
            }
        }
    }
}
@Composable
fun ReviewExpenseBox(
    expenseList: SnapshotStateList<Expense>,
    bankColorTextcolorPiclink: Map<String, Pair<Pair<Color, Color>, Int>>,
    onDismiss: () -> Unit,
    onCategoryUpdate: (selectedCategory: String, newCategoryName: String, expenseSelected: Expense?) -> Unit,
    categoryList: SnapshotStateList<Category> = SnapshotStateList(),
    userSettings: UserSettings? = null
) {
    val dateFormatter = remember {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }

    // Group expenses by day and sort groups descending by date
    val groupedExpenses = expenseList
        .sortedByDescending { it.date } // Ensure sorted within day first
        .groupBy { expense ->
            val calendar = Calendar.getInstance().apply { timeInMillis = expense.date }
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis // key: midnight time
        }
        .toSortedMap(compareByDescending { it }) // Sort by day descending

    var showCategoryDialog by remember { mutableStateOf(false) }
    var expenseSelected by remember { mutableStateOf<Expense?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
//            .background(Color.Black.copy(alpha = 0.4f))
            .padding(top = 40.dp, bottom = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .border(width = 1.dp, color = Color.White, shape = RoundedCornerShape(12.dp))
        ) {
            // --------------------------------------------------------------------------------------- Expense Popup Box Content
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(min = 300.dp, max = 500.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "  Expenses",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(id = R.drawable.outline_close_24),
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 🔹 Scrollable Expenses List (weight 1f)
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false) // take remaining space but allow shrink
                        .verticalScroll(rememberScrollState())
                ) {
                    if (groupedExpenses.isEmpty()) {
                        Text("No expenses yet.")
                    } else {
                        Column {
                            groupedExpenses.forEach { (dateMillis, expenses) ->
                                val dateLabel = dateFormatter.format(Date(dateMillis))
                                Text(
                                    text = dateLabel,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Divider()

                                expenses.forEach { expense ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clickable {
                                                showCategoryDialog = true
                                                expenseSelected = expense
                                                       },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val bankKey = expense.bank.uppercase()
                                        val logo = bankColorTextcolorPiclink[bankKey]?.second

                                        Image(
                                            painter = if (logo != null) painterResource(id = logo)
                                            else painterResource(id = R.drawable.unknown),
                                            contentDescription = expense.bank,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .padding(4.dp)
                                                .clip(RoundedCornerShape(24.dp))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(expense.item, Modifier.weight(2f), fontSize = 14.sp)
                                        Text(
                                            "$${"%.2f".format(expense.cost)}",
                                            Modifier.weight(1f),
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.End
                                        )
                                    }
                                    Divider()
                                }
                            }
                        }
                    }
                }

                if (showCategoryDialog) {
                    Dialog(onDismissRequest = { showCategoryDialog = false }) {
                        ReviewCategoryBox(
                            categoryList = categoryList,
                            onDismiss = { showCategoryDialog = false },
                            onSubmit = { selectedCategory, newCategoryName -> onCategoryUpdate(selectedCategory ?: "", newCategoryName, expenseSelected)
                                       showCategoryDialog = false
                            },
                            itemText = expenseSelected?.item ?: "",
                            costText = expenseSelected?.cost?.toString() ?: "",
                            oldCategoryId = expenseSelected?.categoryId ?: 0,
                            selectedBank = expenseSelected?.bank ?: "",
                            expenseList = expenseList,
                            userSettings = userSettings
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 🔹 Fixed Pie Chart (always visible at bottom)
                AndroidView(
                    factory = { context -> PieChart(context) },
                    update = { chart ->
                        setupDonutPieChart(chart,
                            expenseList
                                .groupBy { it.categoryId }
                                .mapKeys { key ->
                                    // map categoryId to category name
                                    categoryList.find { it.id == key.key }?.name ?: "Uncategorized"
                                }
                                .filterKeys { it != "New Category" && it != "Uncategorized" }
                                .mapValues { entry ->
                                    entry.value.sumOf { it.cost }.toFloat()
                                },
                            threshold = userSettings?.thresholdAmountPieChart ?: 5)
                    },
                    modifier = Modifier.fillMaxWidth().height(250.dp)
                )
            }
            // --------------------------------------------------------------------------------------- Expense Popup Box Content End -------------------
        }
    }
}

@Composable
private fun ReviewCategoryBox(
    categoryList: SnapshotStateList<Category>,
    onDismiss: () -> Unit,
    onSubmit: (
        selectedCategory: String?,
        newCategoryName: String
    ) -> Unit,
    itemText: String = "",
    costText: String = "",
    selectedBank: String = "",
    oldCategoryId: Int? = -999,
    expenseList: SnapshotStateList<Expense> = SnapshotStateList(),
    userSettings: UserSettings? = null,
    fillBackgroundGrey: Float = 0.0f,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = fillBackgroundGrey))
            .padding(top = 40.dp, bottom = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    "Choose a category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                // Expense details
                Text("Item: $itemText")
                Text("Cost: $costText")
                Text("Bank: $selectedBank")
                if (oldCategoryId != null && oldCategoryId != -999) {
                    val oldCategoryName = categoryList.find { it.id == oldCategoryId }?.name ?: "Uncategorized"
                    Text("Current Category: $oldCategoryName", fontStyle = FontStyle.Italic, fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))

                // Category dropdown
                var expanded by remember { mutableStateOf(false) }
                var selectedCategory by remember { mutableStateOf<String?>(null) }
                var newCategoryName by remember { mutableStateOf("") }

                Box {
                    Text(
                        text = selectedCategory ?: "Select Category",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                            .background(
                                Color.Gray.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(12.dp),
                        textAlign = TextAlign.Center
                    )

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categoryList.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategory = category.name
                                    expanded = false
                                    newCategoryName = ""
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("New Category") },
                            onClick = {
                                selectedCategory = "New Category"
                                expanded = false
                                newCategoryName = ""
                            }
                        )
                    }
                }

                // New Category field
                if (selectedCategory == "New Category") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Enter new category") },
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(16.dp))

                // --- PREVIEW DATASET ---
                val previewExpenses by remember(
                    expenseList, categoryList,
                    selectedCategory, newCategoryName,
                    itemText, costText, selectedBank
                ) {
                    derivedStateOf {
                        val baseExpenses = expenseList
                        val baseExpensesFiltered = if (oldCategoryId != null && oldCategoryId != -999) {
                            baseExpenses.filter { it.id != expenseList.find { exp -> exp.categoryId == oldCategoryId }?.id }
                        } else baseExpenses
                        val cost = costText.toDoubleOrNull() ?: 0.0

                        when {
                            // Case 1: no selection → show base only
                            selectedCategory == null -> {
                                baseExpenses
                            }

                            // Case 2: selected "New Category" but no name typed → base only
                            selectedCategory == "New Category" && newCategoryName.isBlank() -> {
                                baseExpenses
                            }

                            // Case 3: new category typed → add preview with typed name
                            selectedCategory == "New Category" && newCategoryName.isNotBlank() -> {
                                val tempCategoryId = -100 // preview-only ID
                                if (oldCategoryId != null && oldCategoryId != -999) {
                                    baseExpensesFiltered + Expense(
                                        item = itemText,
                                        cost = cost,
                                        bank = selectedBank,
                                        categoryId = tempCategoryId
                                    )
                                } else {
                                    baseExpenses + Expense(
                                        item = itemText,
                                        cost = cost,
                                        bank = selectedBank,
                                        categoryId = tempCategoryId
                                    )
                                }
                            }

                            // Case 4: existing category selected → add preview
                            else -> {
                                val tempCategoryId = categoryList.find { it.name == selectedCategory }?.id ?: -1
                                if (oldCategoryId != null && oldCategoryId != -999) {
                                    baseExpensesFiltered + Expense(
                                        item = itemText,
                                        cost = cost,
                                        bank = selectedBank,
                                        categoryId = tempCategoryId
                                    )
                                } else {
                                    baseExpenses + Expense(
                                        item = itemText,
                                        cost = cost,
                                        bank = selectedBank,
                                        categoryId = tempCategoryId
                                    )
                                }
                            }
                        }
                    }
                }


                // Pie chart preview
                if (previewExpenses.isEmpty()) {
                    Text("<No data yet>", color = Color.Gray)
                } else {
                    AndroidView(
                        factory = { context -> PieChart(context) },
                        update = { chart ->
                            setupDonutPieChart(
                                chart,
                                previewExpenses
                                    .groupBy { it.categoryId }
                                    .mapKeys { key ->
                                        categoryList.find { it.id == key.key }?.name
                                            ?: if (selectedCategory == "New Category") newCategoryName.ifBlank { "New Category" }
                                            else "Uncategorized"
                                    }
                                    .filterKeys { it != "New Category" && it != "Uncategorized" }
                                    .mapValues { entry ->
                                        entry.value.sumOf { it.cost }.toFloat()
                                    },
                                threshold = userSettings?.thresholdAmountPieChart ?: 5
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Buttons
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { onSubmit(selectedCategory, newCategoryName) },
                        enabled = (selectedCategory != null &&
                                !(newCategoryName.equals("New Category", ignoreCase = true)) &&
                                !(newCategoryName.equals("Others", ignoreCase = true)) &&
                                !(newCategoryName.equals("Other", ignoreCase = true))) || (
                                (selectedCategory.equals("New Category") && newCategoryName.isNotBlank()))
                    ) {
                        Text("Submit")
                    }

                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}


@Composable
private fun NotificationBox(
    scrollState: LazyListState,
    notifications: List<NotificationItem>,
    bankColorDictWithTextColor: Map<String, Pair<Pair<Color, Color>, Int>>,
    onCategorySubmit: (selectedCategory: String, newCategoryName: String, itemText: String, costText: String, bankText: String, notification: NotificationItem) -> Unit,
    onDeleteNotification: (NotificationItem) -> Unit,
    expenseList: SnapshotStateList<Expense>,
    categoryList: SnapshotStateList<Category>,
    userSettings: UserSettings? = null,
) {
    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(notifications) { notification ->
            Row(
                modifier = Modifier
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bank badge with background gradient
                Text(
                    modifier = Modifier
                        .width(40.dp)
                        .graphicsLayer(rotationZ = 270f)
                        .background(
                            brush = Brush.horizontalGradient(
                                when (notification.bank.uppercase()) {
                                    "GXS" -> listOf(Color(0xFF782FFF), Color(0xFFF553B0))
                                    "GP" -> listOf(
                                        Color(0xFF1778E3),
                                        Color(0xFFCD5050),
                                        Color(0xFFE7C16F),
                                        Color(0xFF268933)
                                    )

                                    else -> {
                                        val color =
                                            (bankColorDictWithTextColor[notification.bank.uppercase()]?.first)?.first
                                                ?: Color.Yellow.copy(alpha = 0.5f)
                                        listOf(color, color)
                                    }
                                }
                            ),
                            shape = MaterialTheme.shapes.small
                        ),
                    text = notification.bank,
                    textAlign = TextAlign.Center,
                    fontSize = 9.sp,
                    color = bankColorDictWithTextColor[notification.bank.uppercase()]?.first?.second
                        ?: Color.Black,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    modifier = Modifier.weight(1.75f),
                    text = notification.item,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    modifier = Modifier.weight(0.75f),
                    text = "$${"%.2f".format(notification.cost)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center
                )
                var showCategoryDialog by remember { mutableStateOf(true) } // opening by default

                Button(
                    onClick = {
                        showCategoryDialog = !showCategoryDialog
                    },
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("✔")
                }

                if (showCategoryDialog) {
                    Dialog(onDismissRequest = {  }) {
                        ReviewCategoryBox(
                            categoryList = categoryList,
                            onDismiss = { showCategoryDialog = false },
                            onSubmit = {
                                       selectedCategory, newCategoryName -> onCategorySubmit(selectedCategory ?: "", newCategoryName, notification.item, notification.cost.toString(), notification.bank, notification)
                                showCategoryDialog = false
                            },
                            itemText = notification.item,
                            costText = notification.cost.toString(),
                            selectedBank = notification.bank,
                            expenseList = expenseList,
                            userSettings = userSettings,
//                            fillBackgroundGrey = 0.4f
                        )
                    }
                }

                Button(
                    onClick = {onDeleteNotification(notification) },
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("✘")
                }
            }
        }
    }
}
