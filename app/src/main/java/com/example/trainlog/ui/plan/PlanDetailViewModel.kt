package com.example.trainlog.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trainlog.data.entity.DayPlan
import com.example.trainlog.data.entity.PlanStatus
import com.example.trainlog.data.repo.NewActionDraft
import com.example.trainlog.data.repo.TrainRepository
import com.example.trainlog.ui.common.UiAction
import com.example.trainlog.ui.common.toDraft
import com.example.trainlog.ui.common.toMetricRows
import com.example.trainlog.ui.common.toUi
import com.example.trainlog.util.PlanTextFormatter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlanMetaUi(
    val name: String = "",
    val status: PlanStatus = PlanStatus.NOT_STARTED,
    val startEpochMillis: Long = 0L,
    val durationMinutes: Int = 60,
    val reminderMinutes: Int? = null,
    val note: String = "",
    val calendarEventId: Long? = null,
    val sourceTemplateName: String = "",
)

data class PlanDetailUiState(
    val date: LocalDate,
    val planId: Long? = null,
    val loaded: Boolean = false,
    val meta: PlanMetaUi = PlanMetaUi(),
    val actions: List<UiAction> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanDetailViewModel(
    val date: LocalDate,
    private val repo: TrainRepository,
) : ViewModel() {

    private val planId = MutableStateFlow<Long?>(null)
    private val loadedFlag = MutableStateFlow(false)
    private val meta = MutableStateFlow(PlanMetaUi())

    private val actionsFlow = planId.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            repo.observePlanDetail(id).map { pwa ->
                pwa?.actions?.sortedBy { it.action.sortOrder }?.map { it.toUi() } ?: emptyList()
            }
        }
    }

    val uiState: StateFlow<PlanDetailUiState> = combine(
        planId, loadedFlag, meta, actionsFlow,
    ) { pid, loaded, m, actions ->
        PlanDetailUiState(date = date, planId = pid, loaded = loaded, meta = m, actions = actions)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlanDetailUiState(date = date))

    val templates = repo.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repo.findPlanByDate(date)?.let { existing ->
                seedMeta(existing)
                planId.value = existing.id
            }
            loadedFlag.value = true
        }
    }

    private fun seedMeta(plan: DayPlan) {
        meta.value = PlanMetaUi(
            name = plan.name,
            status = plan.status,
            startEpochMillis = plan.startEpochMillis,
            durationMinutes = plan.durationMinutes,
            reminderMinutes = plan.reminderMinutes,
            note = plan.note,
            calendarEventId = plan.calendarEventId,
            sourceTemplateName = plan.sourceTemplateName,
        )
    }

    private suspend fun refreshMeta() {
        repo.findPlanByDate(date)?.let { seedMeta(it) }
    }

    fun updateMeta(transform: (PlanMetaUi) -> PlanMetaUi) {
        meta.value = transform(meta.value)
    }

    fun setStartTime(time: LocalTime) {
        val millis = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        meta.value = meta.value.copy(startEpochMillis = millis)
    }

    private suspend fun persistMeta(pid: Long) {
        val m = meta.value
        repo.updatePlanMeta(
            planId = pid,
            name = m.name.ifBlank { "训练计划" },
            status = m.status,
            startEpochMillis = m.startEpochMillis,
            durationMinutes = m.durationMinutes.coerceIn(1, 24 * 60),
            reminderMinutes = m.reminderMinutes,
            note = m.note,
        )
        refreshMeta()
    }

    fun saveMeta() {
        val pid = planId.value ?: return
        viewModelScope.launch {
            persistMeta(pid)
            _events.send("已保存")
        }
    }

    fun createEmptyPlan(name: String) {
        viewModelScope.launch {
            val id = repo.createEmptyPlan(date, name)
            refreshMeta()
            planId.value = id
            _events.send("已创建计划")
        }
    }

    /** 从模板应用:无计划时创建快照;已有计划时替换动作列表。 */
    fun applyTemplate(templateId: Long) {
        viewModelScope.launch {
            try {
                val replace = planId.value != null
                val id = repo.applyTemplateToDate(templateId, date, replaceExistingActions = replace)
                refreshMeta()
                planId.value = id
                _events.send(if (replace) "已用模板替换当天动作" else "已应用模板")
            } catch (e: Exception) {
                _events.send("应用模板失败:${e.message ?: "未知错误"}")
            }
        }
    }

    fun addAction(draft: NewActionDraft) {
        val pid = planId.value ?: return
        viewModelScope.launch { repo.addAction(pid, draft) }
    }

    fun updateAction(action: UiAction) {
        val pid = planId.value ?: return
        viewModelScope.launch {
            repo.updateAction(
                planId = pid,
                actionId = action.id,
                name = action.name,
                targetSets = action.targetSets,
                metrics = action.toMetricRows(),
            )
        }
    }

    fun deleteAction(actionId: Long) {
        val pid = planId.value ?: return
        viewModelScope.launch { repo.deleteAction(pid, actionId) }
    }

    fun moveAction(fromIndex: Int, toIndex: Int) {
        val pid = planId.value ?: return
        viewModelScope.launch { repo.moveAction(pid, fromIndex, toIndex) }
    }

    fun saveAsTemplate(templateName: String) {
        val pid = planId.value ?: return
        viewModelScope.launch {
            try {
                repo.savePlanAsTemplate(pid, templateName)
                _events.send("已保存模板「$templateName」")
            } catch (e: Exception) {
                _events.send("保存模板失败:${e.message ?: "未知错误"}")
            }
        }
    }

    /** 同步前先把编辑中的元信息落库,保证事件内容与界面一致。 */
    fun syncToCalendar() {
        val pid = planId.value ?: return
        viewModelScope.launch {
            try {
                persistMeta(pid)
            } catch (_: Exception) {
                // 元信息保存失败不阻塞同步,继续用库里最新内容
            }
            val outcome = repo.syncPlanToCalendar(pid)
            val message = when (outcome) {
                is com.example.trainlog.data.calendar.CalendarSyncService.Outcome.Created ->
                    "已创建日历事件 #${outcome.eventId}"
                is com.example.trainlog.data.calendar.CalendarSyncService.Outcome.Updated ->
                    "已更新日历事件 #${outcome.eventId}"
                is com.example.trainlog.data.calendar.CalendarSyncService.Outcome.Failed ->
                    "同步失败:${outcome.reason}"
            }
            refreshMeta()
            _events.send(message)
        }
    }

    fun onCalendarPermissionDenied() {
        viewModelScope.launch { _events.send("未授予日历权限,无法同步;可在系统设置中开启") }
    }

    fun deletePlan(deleteCalendarEvent: Boolean) {
        val pid = planId.value ?: return
        viewModelScope.launch {
            try {
                val eventDeleted = repo.deletePlan(pid, deleteCalendarEvent)
                planId.value = null
                meta.value = PlanMetaUi()
                _events.send(
                    when {
                        eventDeleted -> "计划与对应日历事件已删除"
                        else -> "计划已删除"
                    }
                )
            } catch (e: Exception) {
                _events.send("删除失败:${e.message ?: "未知错误"}")
            }
        }
    }

    /** 生成剪贴板/分享用纯文本;也用于展示"将同步到日历"的内容预览。 */
    fun buildShareText(state: PlanDetailUiState): String = PlanTextFormatter.format(
        header = "${state.date} ${state.meta.name.ifBlank { "训练计划" }}",
        actions = state.actions.map { ui ->
            PlanTextFormatter.FormatAction(
                name = ui.name,
                targetSets = ui.targetSets,
                metrics = ui.metrics.map { m ->
                    PlanTextFormatter.FormatMetric(
                        name = m.name,
                        targetValue = m.targetValue,
                        targetUnit = m.targetUnit,
                        actualValue = m.actualValue,
                        actualUnit = m.actualUnit,
                    )
                },
            )
        },
        note = state.meta.note,
    )
}
