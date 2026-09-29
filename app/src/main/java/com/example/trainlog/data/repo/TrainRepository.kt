package com.example.trainlog.data.repo

import com.example.trainlog.data.calendar.CalendarSyncService
import com.example.trainlog.data.calendar.CalendarSyncer
import com.example.trainlog.data.db.AppDatabase
import com.example.trainlog.data.entity.ActionMetric
import com.example.trainlog.data.entity.DayPlan
import com.example.trainlog.data.entity.PlanAction
import com.example.trainlog.data.entity.PlanStatus
import com.example.trainlog.data.entity.SyncState
import com.example.trainlog.data.entity.TemplateAction
import com.example.trainlog.data.entity.TemplateMetric
import com.example.trainlog.data.entity.WorkoutTemplate
import com.example.trainlog.util.PlanTextFormatter
import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 唯一的数据入口。所有"模板 <-> 日计划"的拷贝都在 Room 事务中完成,
 * 保证快照原子性。
 */
class TrainRepository(
    private val db: AppDatabase,
    private val calendarSyncer: CalendarSyncer,
) {
    private val templateDao = db.templateDao()
    private val planDao = db.planDao()
    private val calendarSyncService = CalendarSyncService(calendarSyncer)

    // ---------- 查询 ----------

    fun observeMonthPlans(year: Int, month: Int): Flow<List<DayPlan>> {
        val first = LocalDate.of(year, month, 1)
        val last = first.withDayOfMonth(first.lengthOfMonth())
        return planDao.observeBetween(first.toString(), last.toString())
    }

    fun observePlanDetail(planId: Long): Flow<com.example.trainlog.data.entity.PlanWithActions?> =
        planDao.observeWithActions(planId)

    fun observeTemplates() = templateDao.observeAllWithActions()

    suspend fun getTemplate(templateId: Long) = templateDao.getWithActions(templateId)

    suspend fun findPlanByDate(date: LocalDate): DayPlan? = planDao.findByDate(date.toString())

    // ---------- 日计划:创建 / 应用模板 ----------

    suspend fun createEmptyPlan(date: LocalDate, name: String): Long = db.withTransaction {
        val existing = planDao.findByDate(date.toString())
        if (existing != null) return@withTransaction existing.id
        planDao.insertPlan(
            DayPlan(
                date = date.toString(),
                name = name,
                startEpochMillis = SnapshotMappers.defaultStartMillis(date),
            )
        )
    }

    /**
     * 将模板应用到日期。事务内深拷贝模板动作和指标。
     * 该日期已有计划且 replaceExistingActions = true 时,只替换动作列表,
     * 计划本身的备注、状态、日历关联保持不变。
     */
    suspend fun applyTemplateToDate(
        templateId: Long,
        date: LocalDate,
        replaceExistingActions: Boolean = false,
    ): Long = db.withTransaction {
        val loaded = templateDao.getWithActions(templateId)
            ?: throw IllegalArgumentException("模板不存在: $templateId")
        val draft = SnapshotMappers.planDraftFromTemplate(loaded.template, loaded.actions, date)
        val existing = planDao.findByDate(date.toString())
        val planId: Long
        if (existing == null) {
            planId = planDao.insertPlan(draft.toDayPlan())
        } else {
            if (!replaceExistingActions) return@withTransaction existing.id
            planDao.updatePlan(
                existing.copy(
                    name = loaded.template.name,
                    sourceTemplateId = templateId,
                    sourceTemplateName = loaded.template.name,
                )
            )
            planDao.deletePlanActions(existing.id)
            planId = existing.id
        }
        insertDraftActions(planId, draft.actions)
        planId
    }

    private suspend fun insertDraftActions(planId: Long, drafts: List<NewActionDraft>) {
        drafts.forEachIndexed { index, a ->
            val actionId = planDao.insertPlanAction(
                PlanAction(planId = planId, sortOrder = index, name = a.name, targetSets = a.targetSets)
            )
            planDao.insertMetrics(
                a.metrics.mapIndexed { mIndex, m ->
                    ActionMetric(
                        actionId = actionId,
                        sortOrder = mIndex,
                        name = m.name,
                        targetValue = m.targetValue,
                        targetUnit = m.targetUnit,
                    )
                }
            )
        }
    }

    /** 把日计划另存为新模板(深拷贝目标值,丢弃实际值)。 */
    suspend fun savePlanAsTemplate(planId: Long, templateName: String): Long = db.withTransaction {
        val loaded = planDao.getWithActions(planId)
            ?: throw IllegalArgumentException("计划不存在: $planId")
        val draft = SnapshotMappers.templateDraftFromPlan(loaded.plan, loaded.actions, templateName)
        insertTemplateDraft(draft)
    }

    // ---------- 模板 CRUD ----------

    suspend fun createTemplate(name: String, actions: List<NewActionDraft>): Long =
        db.withTransaction { insertTemplateDraft(NewTemplateDraft(name, actions)) }

    private suspend fun insertTemplateDraft(draft: NewTemplateDraft): Long {
        val now = System.currentTimeMillis()
        val templateId = templateDao.insertTemplate(WorkoutTemplate(name = draft.name, createdAt = now, updatedAt = now))
        draft.actions.forEachIndexed { index, a ->
            val actionId = templateDao.insertTemplateAction(
                TemplateAction(templateId = templateId, sortOrder = index, name = a.name, targetSets = a.targetSets)
            )
            templateDao.insertTemplateMetrics(
                a.metrics.mapIndexed { mIndex, m ->
                    TemplateMetric(
                        templateActionId = actionId,
                        sortOrder = mIndex,
                        name = m.name,
                        targetValue = m.targetValue,
                        targetUnit = m.targetUnit,
                    )
                }
            )
        }
        return templateId
    }

    /** 编辑模板:只影响之后新建的日计划,不追溯修改历史。 */
    suspend fun updateTemplate(templateId: Long, name: String, actions: List<NewActionDraft>) {
        db.withTransaction {
            val current = templateDao.getById(templateId) ?: return@withTransaction
            templateDao.updateTemplate(current.copy(name = name, updatedAt = System.currentTimeMillis()))
            templateDao.deleteTemplateActions(templateId)
            val drafts = NewTemplateDraft(name, actions)
            drafts.actions.forEachIndexed { index, a ->
                val actionId = templateDao.insertTemplateAction(
                    TemplateAction(templateId = templateId, sortOrder = index, name = a.name, targetSets = a.targetSets)
                )
                templateDao.insertTemplateMetrics(
                    a.metrics.mapIndexed { mIndex, m ->
                        TemplateMetric(
                            templateActionId = actionId,
                            sortOrder = mIndex,
                            name = m.name,
                            targetValue = m.targetValue,
                            targetUnit = m.targetUnit,
                        )
                    }
                )
            }
        }
    }

    suspend fun deleteTemplate(templateId: Long) = templateDao.deleteTemplate(templateId)

    // ---------- 日计划编辑 ----------

    suspend fun updatePlanMeta(
        planId: Long,
        name: String,
        status: PlanStatus,
        startEpochMillis: Long,
        durationMinutes: Int,
        reminderMinutes: Int?,
        note: String,
    ) {
        db.withTransaction {
            val plan = planDao.getWithActions(planId)?.plan ?: return@withTransaction
            planDao.updatePlan(
                plan.copy(
                    name = name,
                    status = status,
                    startEpochMillis = startEpochMillis,
                    durationMinutes = durationMinutes,
                    reminderMinutes = reminderMinutes,
                    note = note,
                )
            )
        }
    }

    suspend fun addAction(planId: Long, draft: NewActionDraft) {
        db.withTransaction {
            val sortOrder = planDao.nextSortOrder(planId)
            val actionId = planDao.insertPlanAction(
                PlanAction(planId = planId, sortOrder = sortOrder, name = draft.name, targetSets = draft.targetSets)
            )
            planDao.insertMetrics(
                draft.metrics.mapIndexed { mIndex, m ->
                    ActionMetric(
                        actionId = actionId,
                        sortOrder = mIndex,
                        name = m.name,
                        targetValue = m.targetValue,
                        targetUnit = m.targetUnit,
                        actualValue = null,
                    )
                }
            )
        }
    }

    /** 编辑动作:更新名称/组数,指标整体替换(metrics 由调用方构建,含目标与实际值)。 */
    suspend fun updateAction(
        planId: Long,
        actionId: Long,
        name: String,
        targetSets: Int,
        metrics: List<ActionMetric>,
    ) {
        db.withTransaction {
            val existing = planDao.actionsOf(planId).firstOrNull { it.id == actionId } ?: return@withTransaction
            planDao.updatePlanAction(existing.copy(name = name, targetSets = targetSets))
            planDao.deleteMetricsOf(actionId)
            planDao.insertMetrics(metrics)
        }
    }

    suspend fun deleteAction(planId: Long, actionId: Long) {
        db.withTransaction {
            planDao.deletePlanAction(actionId)
            val remaining = planDao.actionsOf(planId).sortedBy { it.sortOrder }
            remaining.forEachIndexed { index, action ->
                if (action.sortOrder != index) {
                    planDao.updatePlanAction(action.copy(sortOrder = index))
                }
            }
        }
    }

    suspend fun moveAction(planId: Long, fromIndex: Int, toIndex: Int) {
        db.withTransaction {
            val actions = planDao.actionsOf(planId).sortedBy { it.sortOrder }.toMutableList()
            if (fromIndex !in actions.indices || toIndex !in actions.indices) return@withTransaction
            val moved = actions.removeAt(fromIndex)
            actions.add(toIndex, moved)
            actions.forEachIndexed { index, action ->
                if (action.sortOrder != index) {
                    planDao.updatePlanAction(action.copy(sortOrder = index))
                }
            }
        }
    }

    // ---------- 日历同步 ----------

    suspend fun syncPlanToCalendar(planId: Long): CalendarSyncService.Outcome =
        withContext(Dispatchers.IO) {
            val loaded = planDao.getWithActions(planId)
                ?: return@withContext CalendarSyncService.Outcome.Failed("计划不存在")
            val description = PlanTextFormatter.format(
                header = "${loaded.plan.date} ${loaded.plan.name}",
                actions = loaded.actions.sortedBy { it.action.sortOrder }.map { paw ->
                    PlanTextFormatter.FormatAction(
                        name = paw.action.name,
                        targetSets = paw.action.targetSets,
                        metrics = paw.metrics.sortedBy { it.sortOrder }.map { m ->
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
                note = loaded.plan.note,
            )
            try {
                val outcome = calendarSyncService.syncPlan(loaded.plan, loaded.actions, description)
                when (outcome) {
                    is CalendarSyncService.Outcome.Created ->
                        planDao.updateCalendarRef(planId, outcome.eventId, SyncState.SYNCED)
                    is CalendarSyncService.Outcome.Updated ->
                        planDao.updateCalendarRef(planId, outcome.eventId, SyncState.SYNCED)
                    is CalendarSyncService.Outcome.Failed -> Unit
                }
                outcome
            } catch (e: SecurityException) {
                CalendarSyncService.Outcome.Failed("未授予日历权限")
            }
        }

    /** 删除日计划;deleteCalendarEvent = true 且已同步时,先删除系统日历事件。 */
    suspend fun deletePlan(planId: Long, deleteCalendarEvent: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val plan = planDao.getWithActions(planId)?.plan
            var eventDeleted = false
            if (plan != null && deleteCalendarEvent && plan.calendarEventId != null) {
                runCatching { calendarSyncService.deletePlanEvent(plan) }
                    .onSuccess { eventDeleted = it }
            }
            db.withTransaction {
                planDao.deletePlan(planId)
            }
            eventDeleted
        }
}
