package com.example.trainlog.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime

/** 常用指标预设:名称 -> 单位 */
val MetricPresets: List<Pair<String, String>> = listOf(
    "距离" to "km",
    "重量" to "kg",
    "次数" to "次",
    "配速" to "分/公里",
    "时长" to "分钟",
    "RPE" to "",
)

/**
 * 动作编辑对话框:名称、组数、任意数量的通用指标。
 * [allowActual] = true 时(日计划)可同时录入实际值;模板编辑时只填目标。
 */
@Composable
fun ActionEditDialog(
    initial: UiAction,
    title: String,
    allowActual: Boolean,
    onDismiss: () -> Unit,
    onSave: (UiAction) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var setsText by remember { mutableStateOf(initial.targetSets.coerceAtLeast(1).toString()) }
    var metrics by remember { mutableStateOf(initial.metrics.toMutableList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("动作名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = setsText,
                    onValueChange = { setsText = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("目标组数") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("指标(可自由增改)", style = MaterialTheme.typography.labelLarge)

                metrics.forEachIndexed { index, metric ->
                    MetricRow(
                        metric = metric,
                        allowActual = allowActual,
                        onUpdate = { updated ->
                            metrics = metrics.toMutableList().also { it[index] = updated }
                        },
                        onDelete = {
                            metrics = metrics.toMutableList().also { it.removeAt(index) }
                        },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetricPresets.forEach { (presetName, presetUnit) ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                metrics = metrics.toMutableList().also {
                                    it.add(UiMetric(name = presetName, targetUnit = presetUnit))
                                }
                            },
                            label = { Text(presetName, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                TextButton(onClick = {
                    metrics = metrics.toMutableList().also { it.add(UiMetric()) }
                }) { Text("+ 自定义指标") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val sets = setsText.toIntOrNull()?.coerceIn(1, 999) ?: 1
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            targetSets = sets,
                            metrics = metrics.filter { it.name.isNotBlank() || it.targetValue.isNotBlank() },
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun MetricRow(
    metric: UiMetric,
    allowActual: Boolean,
    onUpdate: (UiMetric) -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = metric.name,
                onValueChange = { onUpdate(metric.copy(name = it)) },
                label = { Text("指标名") },
                singleLine = true,
                modifier = Modifier.weight(1.4f),
            )
            IconButton(onClick = onDelete) {
                Text("✕", color = MaterialTheme.colorScheme.error)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = metric.targetValue,
                onValueChange = { onUpdate(metric.copy(targetValue = it)) },
                label = { Text("目标值") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = metric.targetUnit,
                onValueChange = { onUpdate(metric.copy(targetUnit = it)) },
                label = { Text("单位") },
                singleLine = true,
                modifier = Modifier.weight(0.8f),
            )
        }
        if (allowActual) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = metric.actualValue,
                    onValueChange = { onUpdate(metric.copy(actualValue = it)) },
                    label = { Text("实际值") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = metric.actualUnit,
                    onValueChange = { onUpdate(metric.copy(actualUnit = it)) },
                    label = { Text("实际单位") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f),
                )
            }
        }
    }
}

/** 单字段名称输入(计划名、模板名等)。 */
@Composable
fun NameInputDialog(
    title: String,
    initial: String = "",
    hint: String = "",
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hint.isNotBlank()) {
                    Text(hint, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开始时间") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "确定",
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmText,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
