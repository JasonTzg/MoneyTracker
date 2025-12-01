// file: NotificationListener.kt
package com.example.moneytracker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.moneytracker.data.Expense
import com.example.moneytracker.data.AppDatabase
import com.example.moneytracker.data.NotificationItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val pkg = sbn.packageName
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            Log.d("NotifListener", "From: $pkg - $title: $text")

            // Very basic keyword-based check
            if (text.contains("$") || text.contains("SGD")) {
                val item = extractItem(title)
                val (cost, last4) = extractCostAndLast4(text)
                val bank = extractBank(pkg, last4 ?: "")

                if (item != null && cost != null && bank.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = AppDatabase.getDatabase(applicationContext).notificationDao()
                        dao.insertNotificationItem(NotificationItem(item=item, cost=cost, bank=bank))
                    }
                }
            }
        }
    }

    private fun extractItem(title: String): String? {
        // Example: extract "Shopee" or "McDonald's" from "You spent $12 at McDonald's"
        // You may want to improve this logic with Regex or NLP
        return title
    }

    private fun extractCostAndLast4(text: String): Pair<Double?, String?> {
        // Match amount like SGD17.90 or $17.90
        val amountRegex = Regex("""(?:SGD|\$)\s?(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(text)
        val amount = amountMatch?.groups?.get(1)?.value?.toDoubleOrNull()

        // Match last 4 digits (common pattern: ends with space + 4 digits)
        val last4Regex = Regex("""\b(\d{4})\b(?!.*\d)""")
        val last4Match = last4Regex.find(text)
        val last4 = last4Match?.groups?.get(1)?.value

        // Return amount (or null) and last 4 digits (or null if not valid)
        val last4Valid = last4?.all { it.isDigit() } == true
        return Pair(amount, if (last4Valid) last4 else null)
    }

    private fun extractBank(pkg: String, bankNumber: String): String {
        val smallerPkg = pkg.lowercase()

        when {
            "dbs" in smallerPkg -> return "DBS"
            "ocbc" in smallerPkg -> return "OCBC"
            "uob" in smallerPkg -> return "UOB"
            "posb" in smallerPkg -> return "POSB"
            "gxs" in smallerPkg -> return "GXS"
            "google.android.apps.walletnfcrel" in smallerPkg -> return "GP $bankNumber"
            "chocolate" in smallerPkg -> return "choco"
            "choco" in smallerPkg -> return "Choco"
//            "moneytracker" in smallerPkg -> return ""
//            else -> return smallerPkg
            else -> return ""
        }

//        return when {
//            pkg.contains("google.android.apps.walletnfcrel", true) -> "Gg $banknumber"
//            pkg.contains("posb", true) -> "POSB"
//            pkg.contains("uob", true) -> "UOB"
//            pkg.contains("dbs", true) -> "DBS"
//            pkg.contains("gxs", true) -> "GXS"
//            pkg.contains("OCBC", true) -> "OCBC"
//            else -> "Unknown"
//        }
    }
}
