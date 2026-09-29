package com.example.trainlog.data.repo

import com.example.trainlog.data.entity.DayPlan
import com.example.trainlog.data.entity.PlanActionWithMetrics
import com.example.trainlog.data.entity.PlanStatus
import com.example.trainlog.data.entity.SyncState
import com.example.trainlog.data.entity.TemplateActionWithMetrics
import com.example.trainlog.data.entity.WorkoutTemplate
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 深拷贝草稿:id 全部归零,由插入时重新生成。
 * 从模板生成日计划、从日计划生成模板,都基于这些草稿做"值拷贝",
 * 保证之后修改模板或日计划互不影响(快照语义)。
 */
data class NewMetricDraft(
    val name: String,
    val targetValue: String?,
    val targetUnit: String,
)

data class NewActionDraft(
    val name: String,
    val targetSets: Int,
    val metrics: List<NewMetricDraft>,
)

data class NewPlanDraft(
    val date: LocalDate,
    val name: String,
    val sourceTemplateId: Long?,
    val sourceTemplateName: String,
    val startEpochMillis: Long,
    val actions: List<NewActionDraft>,
)

data class NewTemplateDraft(
    val name: String,
    val actions: List<NewActionDraft>,
)

/** 纯函数映射器,便于单元测试深拷贝语义。 */
object SnapshotMappers {

    fun defaultStartMillis(date: LocalDate): Long =
        date.atTime(LocalTime.of(8, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * 模板 -> 日计划草稿(深拷贝)。
     * 只拷贝目标值,不携带 id;DayPlan 仅保留 sourceTemplateId 作为追溯信息。
     */
    fun planDraftFromTemplate(
        template: WorkoutTemplate,
        actions: List<TemplateActionWithMetrics>,
        date: LocalDate,
    ): NewPlanDraft = NewPlanDraft(
        date = date,
        name = template.name,
        sourceTemplateId = template.id,
        sourceTemplateName = template.name,
        startEpochMillis = defaultStartMillis(date),
        actions = actions.sortedBy { it.action.sortOrder }.map { taw ->
            NewActionDraft(
                name = taw.action.name,
                targetSets = taw.action.targetSets,
                metrics = taw.metrics.sortedBy { it.sortOrder }.map { m ->
                    NewMetricDraft(
                        name = m.name,
                        targetValue = m.targetValue,
                        targetUnit = m.targetUnit,
                    )
                },
            )
        },
    )

    /** 日计划 -> 模板草稿(深拷贝),只保留目标值,丢弃实际完成值。 */
    fun templateDraftFromPlan(
        plan: DayPlan,
        actions: List<PlanActionWithMetrics>,
        templateName: String,
    ): NewTemplateDraft = NewTemplateDraft(
        name = templateName,
        actions = actions.sortedBy { it.action.sortOrder }.map { paw ->
            NewActionDraft(
                name = paw.action.name,
                targetSets = paw.action.targetSets,
                metrics = paw.metrics.sortedBy { it.sortOrder }.map { m ->
                    NewMetricDraft(
                        name = m.name,
                        targetValue = m.targetValue,
                        targetUnit = m.targetUnit,
                    )
                },
            )
        },
    )
}

/** 草稿 -> DayPlan 实体(插入前,id 为 0)。 */
fun NewPlanDraft.toDayPlan(): DayPlan = DayPlan(
    date = date.toString(),
    name = name,
    status = PlanStatus.NOT_STARTED,
    startEpochMillis = startEpochMillis,
    sourceTemplateId = sourceTemplateId,
    sourceTemplateName = sourceTemplateName,
    calendarSyncState = SyncState.NOT_SYNCED,
)
