package com.example.moneytracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moneytracker.data.MonthlyRecord
import com.example.moneytracker.data.UserSettings

@Composable
fun SettingsDialog(
    currentSettings: UserSettings?,
    onDismiss: () -> Unit,
    onSave: (Int, Double, Int) -> Unit
) {
    var payday by remember { mutableStateOf(currentSettings?.payday?.toString() ?: "") }
    var setAmount by remember { mutableStateOf(currentSettings?.setAmount?.toString() ?: "") }
    var thresholdAmount by remember { mutableStateOf(currentSettings?.thresholdAmountPieChart?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val newPayday = payday.toIntOrNull() ?: return@Button
                val newSetAmount = setAmount.toDoubleOrNull() ?: return@Button
                val threshold = thresholdAmount.toIntOrNull() ?: 5
                onSave(newPayday, newSetAmount, threshold)
            }) {
                Text("Save")
            }
        },
        title = { Text("Update Settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = payday,
                    onValueChange = { payday = it },
                    label = { Text("Payday (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = setAmount,
                    onValueChange = { setAmount = it },
                    label = { Text("Monthly Budget") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = thresholdAmount,
                    onValueChange = { thresholdAmount = it },
                    label = { Text("Pie Chart Threshold % (default 5%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
    )
}

@Composable
fun SettingsMonthSelectionToExport(
    monthlyRecords: List<MonthlyRecord>,
    onMonthSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Month to Export") },
        text = {
            if (monthlyRecords.isEmpty()) {
                Text(
                    text = "There is no data on previous months expenses.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light
                )
            } else {
                LazyColumn {
                    items(monthlyRecords) { record ->
                        Button(
                            onClick = { onMonthSelected(record.monthYear) },
                            modifier = Modifier
                                .padding(8.dp)
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = record.monthYear,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
