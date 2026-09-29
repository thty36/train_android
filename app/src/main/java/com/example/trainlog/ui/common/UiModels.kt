package com.example.trainlog.ui.common

import androidx.compose.ui.graphics.Color
import com.example.trainlog.data.entity.ActionMetric
import com.example.trainlog.data.entity.PlanActionWithMetrics
import com.example.trainlog.data.entity.PlanStatus
import com.example.trainlog.data.entity.TemplateActionWithMetrics
import com.example.trainlog.data.repo.NewActionDraft
import com.example.trainlog.data.repo.NewMetricDraft

/** 编辑态指标:目标值 + 实际值都由用户自由填写,单位不限。 */
data class UiMetric(
    val id: Long = 0,
    val name: String = "",
    val targetValue: String = "",
    val targetUnit: String = "",
    val actualValue: String = "",
    val actualUnit: String = "",
)

data class UiAction(
    val id: Long = 0,
    val name: String = "",
    val targetSets: Int = 1,
    val metrics: List<UiMetric> = emptyList(),
)

fun PlanActionWithMetrics.toUi(): UiAction = UiAction(
    id = action.id,
    name = action.name,
    targetSets = action.targetSets,
    metrics = metrics.sortedBy { it.sortOrder }.map { m ->
        UiMetric(
            id = m.id,
            name = m.name,
            targetValue = m.targetValue.orEmpty(),
            targetUnit = m.targetUnit,
            actualValue = m.actualValue.orEmpty(),
            actualUnit = m.actualUnit.orEmpty(),
        )
    },
)

fun TemplateActionWithMetrics.toUi(): UiAction = UiAction(
    id = action.id,
    name = action.name,
    targetSets = action.targetSets,
    metrics = metrics.sortedBy { it.sortOrder }.map { m ->
        UiMetric(
            id = m.id,
            name = m.name,
            targetValue = m.targetValue.orEmpty(),
            targetUnit = m.targetUnit,
        )
    },
)

/** 编辑态动作 -> 落库用的指标行(保留实际值)。 */
fun UiAction.toMetricRows(): List<ActionMetric> = metrics.mapIndexed { index, m ->
    ActionMetric(
        id = 0,
        actionId = id,
        sortOrder = index,
        name = m.name.trim(),
        targetValue = m.targetValue.trim().ifBlank { null },
        targetUnit = m.targetUnit.trim(),
        actualValue = m.actualValue.trim().ifBlank { null },
        actualUnit = m.actualUnit.trim().ifBlank { null },
    )
}

fun UiAction.toDraft(): NewActionDraft = NewActionDraft(
    name = name.trim(),
    targetSets = targetSets,
    metrics = metrics.map { NewMetricDraft(it.name.trim(), it.targetValue.trim().ifBlank { null }, it.targetUnit.trim()) },
)

fun PlanStatus.statusColor(): Color = when (this) {
    PlanStatus.NOT_STARTED -> Color(0xFF1E88E5)
    PlanStatus.IN_PROGRESS -> Color(0xFFFB8C00)
    PlanStatus.COMPLETED -> Color(0xFF43A047)
    PlanStatus.SKIPPED -> Color(0xFF9E9E9E)
}
