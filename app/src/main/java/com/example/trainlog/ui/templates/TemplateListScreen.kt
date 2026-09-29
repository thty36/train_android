package com.example.trainlog.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.example.trainlog.ServiceLocator
import com.example.trainlog.ui.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(
    navController: NavController,
    viewModel: TemplatesViewModel = viewModel(
        factory = viewModelFactory { initializer { TemplatesViewModel(ServiceLocator.repository) } },
    ),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var applyTargetTemplateId by remember { mutableStateOf<Long?>(null) }
    var deleteTargetTemplateId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("训练模板") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Routes.templateEdit(0L)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新建模板") },
            )
        },
    ) { padding ->
        if (templates.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "还没有模板。可新建模板,或在日计划页把某天计划保存为模板。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(templates, key = { it.template.id }) { template ->
                    val updated = template.template.updatedAt
                    val updatedText = remember(updated) {
                        dateFormat.format(Instant.ofEpochMilli(updated).atZone(ZoneId.systemDefault()))
                    }
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                template.template.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${template.actions.size} 个动作 · 更新于 $updatedText",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            template.actions.sortedBy { it.action.sortOrder }.forEach { taw ->
                                val metricsText = taw.metrics.sortedBy { it.sortOrder }
                                    .joinToString(", ") { m ->
                                        buildString {
                                            append(m.targetValue ?: "—")
                                            if (m.targetUnit.isNotBlank()) append(m.targetUnit)
                                        }
                                    }
                                Text(
                                    "• ${taw.action.name} ×${taw.action.targetSets}组" +
                                        if (metricsText.isBlank()) "" else "($metricsText)",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Row {
                                TextButton(onClick = {
                                    navController.navigate(Routes.templateEdit(template.template.id))
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, Modifier.padding(end = 4.dp))
                                    Text("编辑")
                                }
                                TextButton(onClick = { applyTargetTemplateId = template.template.id }) {
                                    Icon(Icons.Filled.Event, contentDescription = null, Modifier.padding(end = 4.dp))
                                    Text("应用到日期")
                                }
                                TextButton(onClick = { deleteTargetTemplateId = template.template.id }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        Modifier.padding(end = 4.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Text("删除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    applyTargetTemplateId?.let { templateId ->
        PickDateDialog(
            onDismiss = { applyTargetTemplateId = null },
            onConfirm = { date ->
                applyTargetTemplateId = null
                viewModel.applyToDate(templateId, date) { planId ->
                    if (planId != null) {
                        navController.navigate(Routes.plan(date.toString())) {
                            popUpTo(Routes.CALENDAR)
                        }
                    }
                }
            },
        )
    }

    deleteTargetTemplateId?.let { templateId ->
        com.example.trainlog.ui.common.ConfirmDialog(
            title = "删除模板?",
            text = "只删除模板本身;已通过它创建的日计划不受影响。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleteTargetTemplateId = null },
            onConfirm = {
                deleteTargetTemplateId = null
                viewModel.deleteTemplate(templateId)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDateDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val date = state.selectedDateMillis
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }
                    ?: LocalDate.now()
                onConfirm(date)
            }) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state, title = {
            Text("应用到哪一天", modifier = Modifier.padding(start = 24.dp, top = 16.dp))
        })
    }
}
