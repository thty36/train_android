package com.example.trainlog.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trainlog.data.entity.TemplateWithActions
import com.example.trainlog.data.repo.NewActionDraft
import com.example.trainlog.data.repo.TrainRepository
import com.example.trainlog.ui.common.UiAction
import com.example.trainlog.ui.common.toDraft
import com.example.trainlog.ui.common.toUi
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplatesViewModel(private val repo: TrainRepository) : ViewModel() {

    val templates: StateFlow<List<TemplateWithActions>> = repo.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun deleteTemplate(templateId: Long) {
        viewModelScope.launch {
            repo.deleteTemplate(templateId)
            _events.send("模板已删除")
        }
    }

    /** 将模板应用到指定日期,成功后通过回调通知(用于跳转到当天计划)。 */
    fun applyToDate(templateId: Long, date: LocalDate, onResult: (Long?) -> Unit) {
        viewModelScope.launch {
            val existing = repo.findPlanByDate(date)
            val id = runCatching {
                repo.applyTemplateToDate(templateId, date, replaceExistingActions = false)
            }.getOrNull()
            when {
                id == null -> _events.send("应用失败")
                existing != null -> _events.send("$date 已有计划,未替换动作;可在当天计划页选择模板替换")
            }
            onResult(id)
        }
    }
}

data class TemplateEditUiState(
    val templateId: Long,
    val name: String = "",
    val loaded: Boolean = false,
    val actions: List<UiAction> = emptyList(),
)

class TemplateEditViewModel(
    private val templateId: Long,
    private val repo: TrainRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TemplateEditUiState(templateId = templateId))
    val state: StateFlow<TemplateEditUiState> = _state.asStateFlow()

    init {
        if (templateId <= 0L) {
            _state.value = TemplateEditUiState(templateId = 0L, loaded = true)
        } else {
            viewModelScope.launch {
                val loaded = repo.getTemplate(templateId)
                _state.value = if (loaded == null) {
                    TemplateEditUiState(templateId = 0L, loaded = true)
                } else {
                    TemplateEditUiState(
                        templateId = templateId,
                        name = loaded.template.name,
                        loaded = true,
                        actions = loaded.actions.sortedBy { it.action.sortOrder }.map { it.toUi() },
                    )
                }
            }
        }
    }

    fun setName(name: String) {
        _state.value = _state.value.copy(name = name)
    }

    fun upsertAction(action: UiAction) {
        val current = _state.value.actions.toMutableList()
        val index = current.indexOfFirst { it.id == action.id && action.id != 0L }
        if (index >= 0) {
            current[index] = action
        } else {
            current.add(action)
        }
        _state.value = _state.value.copy(actions = current)
    }

    fun removeAction(actionId: Long) {
        _state.value = _state.value.copy(actions = _state.value.actions.filterNot { it.id == actionId })
    }

    fun moveAction(fromIndex: Int, toIndex: Int) {
        val actions = _state.value.actions.toMutableList()
        if (fromIndex !in actions.indices || toIndex !in actions.indices) return
        val moved = actions.removeAt(fromIndex)
        actions.add(toIndex, moved)
        _state.value = _state.value.copy(actions = actions)
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) return
        viewModelScope.launch {
            val drafts: List<NewActionDraft> = s.actions.map { it.toDraft() }
            if (s.templateId > 0L) {
                repo.updateTemplate(s.templateId, s.name.trim(), drafts)
            } else {
                repo.createTemplate(s.name.trim(), drafts)
            }
            onDone()
        }
    }
}
