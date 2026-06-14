package com.cheradip.ailanguagetutor.feature.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.billing.AdminEarningsSnapshot
import com.cheradip.ailanguagetutor.core.network.AdminEarningsMetrics
import com.cheradip.ailanguagetutor.core.network.AdminEarningsRow
import com.cheradip.ailanguagetutor.ui.theme.CheradipTeal
import java.util.Calendar
import java.util.Locale

enum class EarningsReportPeriod(val apiValue: String, val label: String) {
    DAILY("daily", "Daily"),
    MONTHLY("monthly", "Monthly"),
    QUARTERLY("quarterly", "Quarterly"),
    HALF_YEARLY("half_yearly", "Half Yearly"),
    YEARLY("yearly", "Yearly"),
    BI_ANNUALLY("bi_annually", "Bi Annually"),
    LIFETIME("lifetime", "Lifetime"),
    CUSTOM("custom", "Custom"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminEarningsReportSection(
    loading: Boolean,
    error: String?,
    report: AdminEarningsSnapshot?,
    selectedPeriod: EarningsReportPeriod,
    customFromMs: Long?,
    customToMs: Long?,
    onPeriodSelected: (EarningsReportPeriod) -> Unit,
    onCustomFromSelected: (Long) -> Unit,
    onCustomToSelected: (Long) -> Unit,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    val tealColors = DatePickerDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = CheradipTeal,
        headlineContentColor = CheradipTeal,
        weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        yearContentColor = MaterialTheme.colorScheme.onSurface,
        currentYearContentColor = CheradipTeal,
        selectedYearContainerColor = CheradipTeal,
        selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
        dayContentColor = MaterialTheme.colorScheme.onSurface,
        selectedDayContainerColor = CheradipTeal,
        selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
        todayContentColor = CheradipTeal,
        todayDateBorderColor = CheradipTeal,
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Earnings report",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Pending = paid subscriptions still in period. Available = period ended. " +
                "Net columns deduct referral commission. Total Referral = pending + available referral.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EarningsReportPeriod.entries.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { onPeriodSelected(period) },
                    label = { Text(period.label) },
                )
            }
        }

        if (selectedPeriod == EarningsReportPeriod.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { showFromPicker = true }) {
                    Text("From: ${formatPickerDate(customFromMs)}")
                }
                OutlinedButton(onClick = { showToPicker = true }) {
                    Text("To: ${formatPickerDate(customToMs)}")
                }
            }
        }

        Button(onClick = onLoad, enabled = !loading) {
            Text(
                when {
                    loading -> "Loading…"
                    selectedPeriod == EarningsReportPeriod.CUSTOM -> "Load custom range"
                    else -> "Refresh earnings"
                },
            )
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (loading && report == null) {
            CircularProgressIndicator(
                modifier = Modifier.padding(vertical = 8.dp),
                color = CheradipTeal,
            )
        }

        report?.let { data ->
            Text(
                "${data.period.replace('_', ' ').replaceFirstChar { it.uppercase() }} · ${data.from} → ${data.to}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EarningsSmartTable(
                rows = data.rows,
                totals = data.totals,
            )
        }
    }

    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customFromMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let(onCustomFromSelected)
                        showFromPicker = false
                    },
                ) { Text("OK", color = CheradipTeal) }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state, colors = tealColors)
        }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customToMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let(onCustomToSelected)
                        showToPicker = false
                    },
                ) { Text("OK", color = CheradipTeal) }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state, colors = tealColors)
        }
    }
}

@Composable
private fun EarningsSmartTable(
    rows: List<AdminEarningsRow>,
    totals: AdminEarningsMetrics,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val columns = listOf(
        "Period" to 120.dp,
        "Pending" to 96.dp,
        "Available" to 96.dp,
        "Net Pending" to 108.dp,
        "Net Available" to 112.dp,
        "Total Referral" to 112.dp,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            columns.forEach { (title, width) ->
                EarningsTableCell(text = title, width = width, header = true, bold = true)
            }
        }

        if (rows.isEmpty()) {
            Text(
                "No earnings in this range yet.",
                modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            rows.forEach { row ->
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    EarningsTableCell(row.label, columns[0].second, header = false)
                    EarningsTableCell(formatUsd(row.pending), columns[1].second, alignEnd = true)
                    EarningsTableCell(formatUsd(row.available), columns[2].second, alignEnd = true)
                    EarningsTableCell(formatUsd(row.netPending), columns[3].second, alignEnd = true)
                    EarningsTableCell(formatUsd(row.netAvailable), columns[4].second, alignEnd = true)
                    EarningsTableCell(formatUsd(row.totalReferral), columns[5].second, alignEnd = true)
                }
            }
        }

        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(top = 4.dp),
        ) {
            EarningsTableCell("Total", columns[0].second, header = true, bold = true)
            EarningsTableCell(formatUsd(totals.pending), columns[1].second, header = true, bold = true, alignEnd = true)
            EarningsTableCell(formatUsd(totals.available), columns[2].second, header = true, bold = true, alignEnd = true)
            EarningsTableCell(formatUsd(totals.netPending), columns[3].second, header = true, bold = true, alignEnd = true)
            EarningsTableCell(formatUsd(totals.netAvailable), columns[4].second, header = true, bold = true, alignEnd = true)
            EarningsTableCell(formatUsd(totals.totalReferral), columns[5].second, header = true, bold = true, alignEnd = true)
        }
    }
}

@Composable
private fun EarningsTableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    header: Boolean = false,
    bold: Boolean = false,
    alignEnd: Boolean = false,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(
                if (header) CheradipTeal.copy(alpha = if (bold) 0.18f else 0.10f)
                else MaterialTheme.colorScheme.surface,
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = if (bold) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            else MaterialTheme.typography.bodySmall,
            color = if (header) CheradipTeal else MaterialTheme.colorScheme.onSurface,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatUsd(value: Double): String = "$${"%.2f".format(value)}"

private fun formatPickerDate(ms: Long?): String = formatLocalDate(ms) ?: "Pick date"

fun formatEarningsApiDate(ms: Long): String = formatLocalDate(ms) ?: ""

private fun formatLocalDate(ms: Long?): String? {
    if (ms == null) return null
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    )
}
