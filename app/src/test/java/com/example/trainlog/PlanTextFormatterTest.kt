package com.example.trainlog

import com.example.trainlog.util.PlanTextFormatter
import com.example.trainlog.util.PlanTextFormatter.FormatAction
import com.example.trainlog.util.PlanTextFormatter.FormatMetric
import org.junit.Assert.assertEquals
import org.junit.Test

/** 剪贴板 / 日历描述的纯文本格式测试。 */
class PlanTextFormatterTest {

    @Test
    fun `验收示例格式完全一致`() {
        val text = PlanTextFormatter.format(
            header = "2026-09-28 跑步计划 1",
            actions = listOf(
                FormatAction(
                    name = "长跑",
                    targetSets = 1,
                    metrics = listOf(FormatMetric("距离", "3", "km", "3.2", "km")),
                ),
                FormatAction(
                    name = "短跑",
                    targetSets = 8,
                    metrics = listOf(FormatMetric("距离", "100", "m", "100", "m")),
                ),
            ),
        )

        assertEquals(
            """
            2026-09-28 跑步计划 1
            1. 长跑:目标 3 km;实际 3.2 km
            2. 短跑:目标 100 m x 8;实际 100 m x 8
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `未录入实际值时不输出实际段`() {
        val text = PlanTextFormatter.format(
            header = "2026-09-28 跑步计划 1",
            actions = listOf(
                FormatAction(name = "长跑", targetSets = 1, metrics = listOf(FormatMetric("距离", "3", "km"))),
            ),
        )
        assertEquals(
            """
            2026-09-28 跑步计划 1
            1. 长跑:目标 3 km
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `多个指标用逗号连接`() {
        val text = PlanTextFormatter.format(
            header = "2026-09-29 器械计划",
            actions = listOf(
                FormatAction(
                    name = "卧推",
                    targetSets = 5,
                    metrics = listOf(
                        FormatMetric("重量", "60", "kg", "62.5", "kg"),
                        FormatMetric("RPE", "7", "", "8", ""),
                    ),
                ),
            ),
        )
        assertEquals(
            """
            2026-09-29 器械计划
            1. 卧推:目标 60 kg, 7 x 5;实际 62.5 kg, 8 x 5
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `无指标时显示组数`() {
        val text = PlanTextFormatter.format(
            header = "2026-09-29 拉伸",
            actions = listOf(FormatAction(name = "全身拉伸", targetSets = 3)),
        )
        assertEquals(
            """
            2026-09-29 拉伸
            1. 全身拉伸:目标 3 组
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `备注单独成行`() {
        val text = PlanTextFormatter.format(
            header = "2026-09-29 拉伸",
            actions = listOf(FormatAction(name = "全身拉伸", targetSets = 1)),
            note = "膝盖不适,降低强度",
        )
        assertEquals(
            """
            2026-09-29 拉伸
            1. 全身拉伸:目标 1 组
            备注:膝盖不适,降低强度
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `部分指标有实际值时只输出有值的部分`() {
        val text = PlanTextFormatter.format(
            header = "2026-09-30 跑步",
            actions = listOf(
                FormatAction(
                    name = "间歇跑",
                    targetSets = 6,
                    metrics = listOf(
                        FormatMetric("距离", "400", "m", "400", "m"),
                        FormatMetric("配速", "1:40", "分/公里", null, null),
                    ),
                ),
            ),
        )
        assertEquals(
            """
            2026-09-30 跑步
            1. 间歇跑:目标 400 m, 1:40 分/公里 x 6;实际 400 m x 6
            """.trimIndent(),
            text,
        )
    }
}
