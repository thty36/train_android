package com.example.trainlog.util

/**
 * 计划 -> 可读纯文本。用于剪贴板复制和日历事件描述。
 * 纯 Kotlin 对象,便于单元测试。
 *
 * 示例输出:
 * ```
 * 2026-09-28 跑步计划 1
 * 1. 长跑:目标 3 km;实际 3.2 km
 * 2. 短跑:目标 100 m x 8;实际 100 m x 8
 * ```
 */
object PlanTextFormatter {

    data class FormatMetric(
        val name: String,
        val targetValue: String? = null,
        val targetUnit: String = "",
        val actualValue: String? = null,
        val actualUnit: String? = null,
    )

    data class FormatAction(
        val name: String,
        val targetSets: Int,
        val metrics: List<FormatMetric> = emptyList(),
    )

    fun format(header: String, actions: List<FormatAction>, note: String = ""): String {
        val lines = mutableListOf(header)
        actions.forEachIndexed { index, action ->
            lines += actionLine(index + 1, action)
        }
        if (note.isNotBlank()) {
            lines += "备注:$note"
        }
        return lines.joinToString("\n")
    }

    private fun actionLine(index: Int, action: FormatAction): String {
        val setsSuffix = if (action.targetSets > 1) " x ${action.targetSets}" else ""
        val goals = action.metrics.mapNotNull { metricText(it, actual = false) }
        val targetSegment = if (goals.isEmpty()) {
            "${action.targetSets} 组"
        } else {
            goals.joinToString(", ") + setsSuffix
        }
        var line = "$index. ${action.name}:目标 $targetSegment"
        val actuals = action.metrics.mapNotNull { metricText(it, actual = true) }
        if (actuals.isNotEmpty()) {
            line += ";实际 " + actuals.joinToString(", ") + setsSuffix
        }
        return line
    }

    private fun metricText(metric: FormatMetric, actual: Boolean): String? {
        val value = if (actual) metric.actualValue else metric.targetValue
        if (value.isNullOrBlank()) return null
        val unit = if (actual) {
            metric.actualUnit?.takeIf { it.isNotBlank() } ?: metric.targetUnit
        } else {
            metric.targetUnit
        }
        return if (unit.isBlank()) value.trim() else "${value.trim()} ${unit.trim()}"
    }
}
