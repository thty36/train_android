package com.example.trainlog.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.example.trainlog.ServiceLocator
import com.example.trainlog.ui.common.ActionEditDialog
import com.example.trainlog.ui.common.UiAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditScreen(
    navController: NavController,
    templateId: Long,
    viewModel: TemplateEditViewModel = viewModel(
        key = "templateEdit_$templateId",
        factory = viewModelFactory {
            initializer { TemplateEditViewModel(templateId, ServiceLocator.repository) }
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingAction by remember { mutableStateOf<UiAction?>(null) }
    var addingAction by remember { mutableStateOf(false) }
    // 本地编辑中的动作 id 自增,避免与已保存动作冲突
    var localId by remember { mutableLongStateOf(-1L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (templateId > 0) "编辑模板" else "新建模板") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save { navController.popBackStack() } },
                        enabled = state.loaded && state.name.isNotBlank(),
                    ) { Text("保存") }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = viewModel::setName,
                            label = { Text("模板名称,例如「跑步计划 1」") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "修改模板只影响之后新建的日计划,不会改动已创建的计划。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("动作(${state.actions.size})", style = MaterialTheme.typography.titleMedium)
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            IconButton(onClick = { addingAction = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "添加动作")
                            }
                        }
                        if (state.actions.isEmpty()) {
                            Text(
                                "模板还没有动作,点击 + 添加。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            itemsIndexed(state.actions, key = { _, a -> a.id }) { index, action ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${index + 1}. ${action.name}(${action.targetSets} 组)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                action.metrics.forEach { m ->
                                    Text(
                                        "• ${m.name}:目标 ${m.targetValue.ifBlank { "—" }} ${m.targetUnit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column {
                                IconButton(
                                    onClick = { viewModel.moveAction(index, index - 1) },
                                    enabled = index > 0,
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                                }
                                IconButton(
                                    onClick = { viewModel.moveAction(index, index + 1) },
                                    enabled = index < state.actions.lastIndex,
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                                }
                            }
                        }
                        Row {
                            TextButton(onClick = { editingAction = action }) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                                Text("编辑")
                            }
                            TextButton(onClick = { viewModel.removeAction(action.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { addingAction = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("添加动作") }
                OutlinedButton(
                    onClick = { viewModel.save { navController.popBackStack() } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = state.name.isNotBlank(),
                ) { Text("保存模板") }
            }
        }
    }

    if (addingAction) {
        ActionEditDialog(
            initial = UiAction(id = localId),
            title = "添加动作",
            allowActual = false,
            onDismiss = { addingAction = false },
            onSave = { action ->
                localId -= 1
                addingAction = false
                viewModel.upsertAction(action)
            },
        )
    }

    editingAction?.let { editing ->
        ActionEditDialog(
            initial = editing,
            title = "编辑动作",
            allowActual = false,
            onDismiss = { editingAction = null },
            onSave = { action ->
                editingAction = null
                viewModel.upsertAction(action)
            },
        )
    }
}
