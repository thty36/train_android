package com.example.trainlog

import com.example.trainlog.data.entity.ActionMetric
import com.example.trainlog.data.entity.DayPlan
import com.example.trainlog.data.entity.PlanActionWithMetrics
import com.example.trainlog.data.entity.PlanStatus
import com.example.trainlog.data.entity.TemplateAction
import com.example.trainlog.data.entity.TemplateActionWithMetrics
import com.example.trainlog.data.entity.TemplateMetric
import com.example.trainlog.data.entity.WorkoutTemplate
import com.example.trainlog.data.repo.NewMetricDraft
import com.example.trainlog.data.repo.SnapshotMappers
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模板 -> 日计划、日计划 -> 模板的深拷贝语义测试。
 * 这是"修改模板不影响已创建日计划"的根基。
 */
class SnapshotMappersTest {

    private val date = LocalDate.of(2026, 9, 28)

    private fun runningTemplate(): Pair<WorkoutTemplate, List<TemplateActionWithMetrics>> {
        val template = WorkoutTemplate(id = 7, name = "跑步计划 1", createdAt = 1L, updatedAt = 2L)
        val actions = listOf(
            TemplateActionWithMetrics(
                action = TemplateAction(id = 101, templateId = 7, sortOrder = 0, name = "长跑", targetSets = 1),
                metrics = listOf(
                    TemplateMetric(id = 901, templateActionId = 101, sortOrder = 0, name = "距离", targetValue = "3", targetUnit = "km"),
                ),
            ),
            TemplateActionWithMetrics(
                action = TemplateAction(id = 100, templateId = 7, sortOrder = 1, name = "短跑", targetSets = 8),
                metrics = listOf(
                    TemplateMetric(id = 900, templateActionId = 100, sortOrder = 0, name = "距离", targetValue = "100", targetUnit = "m"),
                ),
            ),
        )
        return template to actions
    }

    @Test
    fun `模板转日计划草稿按排序拷贝动作和指标`() {
        val (template, actions) = runningTemplate()

        val draft = SnapshotMappers.planDraftFromTemplate(template, actions, date)

        assertEquals(date, draft.date)
        assertEquals("跑步计划 1", draft.name)
        assertEquals(7L, draft.sourceTemplateId)
        assertEquals("跑步计划 1", draft.sourceTemplateName)
        // 按 sortOrder 排序:长跑在前
        assertEquals(listOf("长跑", "短跑"), draft.actions.map { it.name })
        assertEquals(1, draft.actions[0].targetSets)
        assertEquals(8, draft.actions[1].targetSets)
        assertEquals(listOf(NewMetricDraft("距离", "3", "km")), draft.actions[0].metrics)
        assertEquals(listOf(NewMetricDraft("距离", "100", "m")), draft.actions[1].metrics)
    }

    @Test
    fun `模板转日计划草稿与模板数据完全解耦`() {
        val (template, _) = runningTemplate()
        val mutableMetrics = mutableListOf(
            TemplateMetric(id = 902, templateActionId = 101, sortOrder = 0, name = "距离", targetValue = "3", targetUnit = "km"),
        )
        val actions = listOf(
            TemplateActionWithMetrics(
                action = TemplateAction(id = 101, templateId = 7, sortOrder = 0, name = "长跑", targetSets = 1),
                metrics = mutableMetrics,
            ),
        )

        val draft = SnapshotMappers.planDraftFromTemplate(template, actions, date)

        // 拷贝完成后修改(甚至清空)模板侧数据,不影响草稿 —— 值拷贝而非引用共享
        mutableMetrics.clear()
        assertTrue(draft.actions[0].metrics.isNotEmpty())
    }

    @Test
    fun `日计划存为模板草稿保留目标值`() {
        val plan = DayPlan(
            id = 5,
            date = "2026-09-28",
            name = "跑步计划 1",
            status = PlanStatus.COMPLETED,
            startEpochMillis = 1_000_000L,
            note = "状态不错",
        )
        val actions = listOf(
            PlanActionWithMetrics(
                action = com.example.trainlog.data.entity.PlanAction(id = 20, planId = 5, sortOrder = 0, name = "长跑", targetSets = 1),
                metrics = listOf(
                    ActionMetric(
                        id = 30,
                        actionId = 20,
                        sortOrder = 0,
                        name = "距离",
                        targetValue = "3",
                        targetUnit = "km",
                        actualValue = "3.2",
                        actualUnit = "km",
                    ),
                ),
            ),
        )

        val draft = SnapshotMappers.templateDraftFromPlan(plan, actions, "周末跑步计划")

        assertEquals("周末跑步计划", draft.name)
        assertEquals(1, draft.actions.size)
        val metric = draft.actions[0].metrics.single()
        // 模板只保留目标值;NewMetricDraft 类型本身没有实际值字段,实际值无法带入
        assertEquals("距离", metric.name)
        assertEquals("3", metric.targetValue)
        assertEquals("km", metric.targetUnit)
    }

    @Test
    fun `日计划存为模板草稿不共享指标列表`() {
        val plan = DayPlan(id = 5, date = "2026-09-28", name = "跑步计划 1", startEpochMillis = 1L)
        val metrics = mutableListOf(
            ActionMetric(id = 30, actionId = 20, sortOrder = 0, name = "距离", targetValue = "3", targetUnit = "km"),
        )
        val actions = listOf(
            PlanActionWithMetrics(
                action = com.example.trainlog.data.entity.PlanAction(id = 20, planId = 5, sortOrder = 0, name = "长跑", targetSets = 1),
                metrics = metrics,
            ),
        )

        val draft = SnapshotMappers.templateDraftFromPlan(plan, actions, "模板 A")

        metrics.clear()
        assertEquals(1, draft.actions[0].metrics.size)
    }
}
