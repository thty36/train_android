package com.example.trainlog.ui.plan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.example.trainlog.ServiceLocator
import com.example.trainlog.data.entity.PlanStatus
import com.example.trainlog.ui.common.ActionEditDialog
import com.example.trainlog.ui.common.ConfirmDialog
import com.example.trainlog.ui.common.NameInputDialog
import com.example.trainlog.ui.common.TimePickerDialog
import com.example.trainlog.ui.common.UiAction
import com.example.trainlog.ui.common.toDraft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val reminderOptions: List<Int?> = listOf(null, 5, 10, 15, 30, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    navController: NavController,
    dateArg: String,
    viewModel: PlanDetailViewModel = planDetailViewModel(dateArg),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    // ---- 日历权限 ----
    val context = LocalContext.current
    fun calendarPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    var hasCalendarPermission by remember { mutableStateOf(calendarPermissionGranted()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val allGranted = grants.values.all { it }
        hasCalendarPermission = allGranted
        if (allGranted) {
            viewModel.syncToCalendar()
        } else {
            viewModel.onCalendarPermissionDenied()
        }
    }

    // ---- 对话框状态 ----
    var showTimePicker by remember { mutableStateOf(false) }
    var showCreatePlan by remember { mutableStateOf(false) }
    var showTemplateSheet by remember { mutableStateOf(false) }
    var editingAction by remember { mutableStateOf<UiAction?>(null) }
    var addingAction by remember { mutableStateOf(false) }
    var showSaveAsTemplate by remember { mutableStateOf(false) }
    var showDeletePlan by remember { mutableStateOf(false) }
    var pendingTemplateId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${state.date} 训练计划") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            !state.loaded -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.planId == null -> EmptyPlanContent(
                modifier = Modifier.padding(padding),
                onCreateEmpty = { showCreatePlan = true },
                onApplyTemplate = { showTemplateSheet = true },
            )

            else -> PlanEditorContent(
                state = state,
                onMetaChange = viewModel::updateMeta,
                onPickTime = { showTimePicker = true },
                onSaveMeta = viewModel::saveMeta,
                onEditAction = { editingAction = it },
                onAddAction = { addingAction = true },
                onDeleteAction = viewModel::deleteAction,
                onMoveAction = viewModel::moveAction,
                onSaveAsTemplate = { showSaveAsTemplate = true },
                onCopy = {
                    clipboard.setText(AnnotatedString(viewModel.buildShareText(state)))
                    scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                },
                onSyncCalendar = {
                    if (hasCalendarPermission) {
                        viewModel.syncToCalendar()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                        )
                    }
                },
                hasCalendarPermission = hasCalendarPermission,
                onDeletePlan = { showDeletePlan = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    // ---- 对话框 ----
    if (showCreatePlan) {
        NameInputDialog(
            title = "新建当天计划",
            initial = "训练计划",
            hint = "为 ${viewModel.date} 创建一份空计划,之后可手动添加动作。",
            onDismiss = { showCreatePlan = false },
            onConfirm = { name ->
                showCreatePlan = false
                viewModel.createEmptyPlan(name)
            },
        )
    }

    if (showTemplateSheet) {
        ModalBottomSheet(onDismissRequest = { showTemplateSheet = false }) {
            Text(
                "选择模板应用",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (templates.isEmpty()) {
                Text(
                    "还没有模板。可先在日历页右上角「模板」中创建。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            templates.forEach { t ->
                val summary = t.actions.sortedBy { it.action.sortOrder }
                    .joinToString("、") { it.action.name }
                ListItem(
                    headlineContent = { Text(t.template.name) },
                    supportingContent = {
                        Text(
                            if (summary.isBlank()) "无动作" else summary,
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .let { m ->
                            m.then(
                                Modifier
                            )
                        },
                    trailingContent = { TextButton(onClick = {
                        showTemplateSheet = false
                        if (state.planId == null) {
                            viewModel.applyTemplate(t.template.id)
                        } else {
                            pendingTemplateId = t.template.id
                        }
                    }) { Text("应用") } },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pendingTemplateId != null) {
        ConfirmDialog(
            title = "替换当天动作?",
            text = "将用所选模板的动作列表替换当前计划的动作。模板本身和其他日期的计划不受影响。",
            onDismiss = { pendingTemplateId = null },
            onConfirm = {
                val id = pendingTemplateId
                pendingTemplateId = null
                if (id != null) viewModel.applyTemplate(id)
            },
        )
    }

    if (showTimePicker) {
        val current = Instant.ofEpochMilli(state.meta.startEpochMillis)
            .atZone(ZoneId.systemDefault()).toLocalTime()
        TimePickerDialog(
            initial = if (state.meta.startEpochMillis > 0) current else LocalTime.of(8, 0),
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                showTimePicker = false
                viewModel.setStartTime(time)
            },
        )
    }

    if (addingAction) {
        ActionEditDialog(
            initial = UiAction(),
            title = "添加动作",
            allowActual = true,
            onDismiss = { addingAction = false },
            onSave = { action ->
                addingAction = false
                viewModel.addAction(action.toDraft())
            },
        )
    }

    editingAction?.let { editing ->
        ActionEditDialog(
            initial = editing,
            title = "编辑动作",
            allowActual = true,
            onDismiss = { editingAction = null },
            onSave = { action ->
                editingAction = null
                viewModel.updateAction(action)
            },
        )
    }

    if (showSaveAsTemplate) {
        NameInputDialog(
            title = "保存为训练模板",
            hint = "将按当前计划的目标值创建新模板(不含实际完成值)。",
            onDismiss = { showSaveAsTemplate = false },
            onConfirm = { name ->
                showSaveAsTemplate = false
                if (name.isNotBlank()) viewModel.saveAsTemplate(name)
            },
        )
    }

    if (showDeletePlan) {
        DeletePlanDialog(
            synced = state.meta.calendarEventId != null,
            onDismiss = { showDeletePlan = false },
            onConfirm = { deleteEvent ->
                showDeletePlan = false
                viewModel.deletePlan(deleteEvent)
            },
        )
    }
}

@Composable
private fun planDetailViewModel(dateArg: String): PlanDetailViewModel {
    val date = remember(dateArg) {
        runCatching { LocalDate.parse(dateArg) }.getOrDefault(LocalDate.now())
    }
    return viewModel(
        key = "plan_$date",
        factory = viewModelFactory {
            initializer { PlanDetailViewModel(date, ServiceLocator.repository) }
        },
    )
}

@Composable
private fun EmptyPlanContent(
    modifier: Modifier = Modifier,
    onCreateEmpty: () -> Unit,
    onApplyTemplate: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("这一天还没有训练计划", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "可以新建空计划手动添加动作,或从模板快速生成。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onApplyTemplate) { Text("从模板应用") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCreateEmpty) { Text("新建空计划") }
    }
}

@Composable
private fun PlanEditorContent(
    state: PlanDetailUiState,
    onMetaChange: ((PlanMetaUi) -> PlanMetaUi) -> Unit,
    onPickTime: () -> Unit,
    onSaveMeta: () -> Unit,
    onEditAction: (UiAction) -> Unit,
    onAddAction: () -> Unit,
    onDeleteAction: (Long) -> Unit,
    onMoveAction: (Int, Int) -> Unit,
    onSaveAsTemplate: () -> Unit,
    onCopy: () -> Unit,
    onSyncCalendar: () -> Unit,
    hasCalendarPermission: Boolean,
    onDeletePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MetaCard(state, onMetaChange, onPickTime, onSaveMeta) }
        item {
            ActionsCard(
                actions = state.actions,
                onEdit = onEditAction,
                onAdd = onAddAction,
                onDelete = onDeleteAction,
                onMove = onMoveAction,
            )
        }
        item {
            OpsCard(
                state = state,
                onSaveAsTemplate = onSaveAsTemplate,
                onCopy = onCopy,
                onSyncCalendar = onSyncCalendar,
                hasCalendarPermission = hasCalendarPermission,
                onDeletePlan = onDeletePlan,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaCard(
    state: PlanDetailUiState,
    onMetaChange: ((PlanMetaUi) -> PlanMetaUi) -> Unit,
    onPickTime: () -> Unit,
    onSaveMeta: () -> Unit,
) {
    val meta = state.meta
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("计划信息", style = MaterialTheme.typography.titleMedium)
            if (meta.sourceTemplateName.isNotBlank()) {
                Text(
                    "来源模板:${meta.sourceTemplateName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = meta.name,
                onValueChange = { name -> onMetaChange { it.copy(name = name) } },
                label = { Text("计划名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("状态", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PlanStatus.entries.forEachIndexed { index, status ->
                    SegmentedButton(
                        selected = meta.status == status,
                        onClick = { onMetaChange { it.copy(status = status) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PlanStatus.entries.size),
                    ) {
                        Text(status.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            val startTimeText = if (meta.startEpochMillis > 0) {
                Instant.ofEpochMilli(meta.startEpochMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            } else {
                "08:00"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("开始时间", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onPickTime) { Text(startTimeText) }
                Spacer(Modifier.width(12.dp))
                Text(
                    "预计 ${meta.durationMinutes} 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = if (meta.durationMinutes == 0) "" else meta.durationMinutes.toString(),
                onValueChange = { text ->
                    val minutes = text.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
                    onMetaChange { it.copy(durationMinutes = minutes) }
                },
                label = { Text("预计时长(分钟)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("日历提醒", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                reminderOptions.forEach { option ->
                    FilterChip(
                        selected = meta.reminderMinutes == option,
                        onClick = { onMetaChange { it.copy(reminderMinutes = option) } },
                        label = {
                            Text(
                                option?.let { "$it 分钟前" } ?: "不提醒",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            OutlinedTextField(
                value = meta.note,
                onValueChange = { note -> onMetaChange { it.copy(note = note) } },
                label = { Text("备注(身体状态、未完成原因等)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = onSaveMeta) { Text("保存计划信息") }
        }
    }
}

@Composable
private fun ActionsCard(
    actions: List<UiAction>,
    onEdit: (UiAction) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("动作(${actions.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = "添加动作")
                }
            }
            if (actions.isEmpty()) {
                Text(
                    "还没有动作,点击右上角 + 添加。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            actions.forEachIndexed { index, action ->
                ActionRow(
                    index = index,
                    action = action,
                    total = actions.size,
                    onEdit = { onEdit(action) },
                    onDelete = { onDelete(action.id) },
                    onMoveUp = { if (index > 0) onMove(index, index - 1) },
                    onMoveDown = { if (index < actions.lastIndex) onMove(index, index + 1) },
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    index: Int,
    action: UiAction,
    total: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${index + 1}. ${action.name}(${action.targetSets} 组)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                action.metrics.forEach { m ->
                    val target = buildString {
                        append(m.name)
                        append(":目标 ")
                        append(m.targetValue.ifBlank { "—" })
                        if (m.targetUnit.isNotBlank()) append(" ").append(m.targetUnit)
                        if (m.actualValue.isNotBlank()) {
                            append(";实际 ")
                            append(m.actualValue)
                            val unit = m.actualUnit.ifBlank { m.targetUnit }
                            if (unit.isNotBlank()) append(" ").append(unit)
                        }
                    }
                    Text(
                        target,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column {
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                }
            }
        }
        Row {
            TextButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = null, Modifier.width(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("编辑")
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null, Modifier.width(18.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun OpsCard(
    state: PlanDetailUiState,
    onSaveAsTemplate: () -> Unit,
    onCopy: () -> Unit,
    onSyncCalendar: () -> Unit,
    hasCalendarPermission: Boolean,
    onDeletePlan: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("操作", style = MaterialTheme.typography.titleMedium)
            if (state.meta.calendarEventId != null) {
                AssistChip(
                    onClick = {},
                    label = { Text("已同步系统日历(事件 #${state.meta.calendarEventId}),修改后可更新") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSaveAsTemplate, modifier = Modifier.weight(1f)) {
                    Text("保存为模板")
                }
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                    Text("复制计划")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSyncCalendar, modifier = Modifier.weight(1f)) {
                    Text(if (state.meta.calendarEventId == null) "同步到手机日历" else "更新日历事件")
                }
                OutlinedButton(onClick = onDeletePlan, modifier = Modifier.weight(1f)) {
                    Text("删除计划", color = MaterialTheme.colorScheme.error)
                }
            }
            if (!hasCalendarPermission) {
                Text(
                    "尚未授予日历权限:点击同步时将弹出申请;如被拒绝,需到系统设置中手动开启后才能同步。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeletePlanDialog(
    synced: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (deleteEvent: Boolean) -> Unit,
) {
    var deleteEvent by remember { mutableStateOf(synced) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除当天计划?") },
        text = {
            Column {
                Text("删除后当天动作和实际记录都会移除,且不可恢复。")
                if (synced) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteEvent, onCheckedChange = { deleteEvent = it })
                        Text("同时删除已同步的系统日历事件")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteEvent) }) {
                Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
