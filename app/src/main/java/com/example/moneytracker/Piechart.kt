package com.example.moneytracker

import android.graphics.Color
import android.graphics.Typeface
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import androidx.core.graphics.toColorInt

fun setupDonutPieChart(pieChart: PieChart, categoryData: Map<String, Float>, threshold: Int) {
    // make anything below threshold "Other"
    val totalValue = categoryData.values.sum()
    val filteredData = categoryData.filter { (it.value / totalValue * 100) >= threshold }
    val otherTotal = categoryData.filter { (it.value / totalValue * 100) < threshold }.values.sum()
    val finalData = if (otherTotal > 0) {
        filteredData + mapOf("Others" to otherTotal)
    } else {
        filteredData
    }

    val entries = finalData.map { (category, value) ->
        PieEntry(value, category)
    }

    val dataSet = PieDataSet(entries, "")

    // Custom unique colors. Up to 15 unique colors
    val customColors = listOf(
        "#FF6F61".toColorInt(),
        "#6B5B95".toColorInt(),
        "#88B04B".toColorInt(),
        "#FFA500".toColorInt(),
        "#009688".toColorInt(),
        "#D65076".toColorInt(),
        "#45B8AC".toColorInt(),
        "#EFC050".toColorInt(),
        "#5B5EA6".toColorInt(),
        "#9B2335".toColorInt(),
        "#DFCFBE".toColorInt(),
        "#BC243C".toColorInt(),
        "#92A8D1".toColorInt(),
        "#F7CAC9".toColorInt(),
        "#034F84".toColorInt()
    )
    dataSet.colors = customColors

    // Values inside slices
    dataSet.valueTextColor = Color.WHITE
    dataSet.valueTextSize = 12f
    dataSet.valueTypeface = Typeface.DEFAULT_BOLD

    // Format values as percentage with category name
    dataSet.valueFormatter = object : ValueFormatter() {
        override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
            return "${"%.1f".format(value)}%"
        }
    }

    val data = PieData(dataSet)
    pieChart.data = data

    // Donut style
//    pieChart.isDrawHoleEnabled = true
    pieChart.setHoleColor(Color.TRANSPARENT)
//    pieChart.setTransparentCircleAlpha(0)

    // Disable description + legend
//    pieChart.description.isEnabled = false // asdsa
//    pieChart.legend.isEnabled = false
    pieChart.legend.textColor = Color.WHITE

    // Entry labels styling (category + % inside chart)
    pieChart.setUsePercentValues(true)
    pieChart.setEntryLabelColor(Color.WHITE)
    pieChart.setEntryLabelTextSize(12f)
    pieChart.setEntryLabelTypeface(Typeface.DEFAULT_BOLD)

//    pieChart.setDrawEntryLabels(false) // turn off inside labels
    pieChart.setDrawEntryLabels(true)
    dataSet.xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
    pieChart.invalidate()
}
