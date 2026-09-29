package com.example.trainlog.data.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** 计划状态:未开始 / 进行中 / 已完成 / 跳过 */
enum class PlanStatus(val label: String) {
    NOT_STARTED("未开始"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成"),
    SKIPPED("跳过");
}

/** 日历同步状态 */
enum class SyncState { NOT_SYNCED, SYNCED }

@Entity(tableName = "workout_templates")
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "template_actions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("templateId")],
)
data class TemplateAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val sortOrder: Int,
    val name: String,
    val targetSets: Int = 1,
)

/** 模板动作的目标指标定义(与应用到日计划后生成的 ActionMetric 对应) */
@Entity(
    tableName = "template_metrics",
    foreignKeys = [
        ForeignKey(
            entity = TemplateAction::class,
            parentColumns = ["id"],
            childColumns = ["templateActionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("templateActionId")],
)
data class TemplateMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateActionId: Long,
    val sortOrder: Int = 0,
    /** 指标名称,例如:距离、重量、次数、配速、时长、RPE */
    val name: String,
    val targetValue: String? = null,
    val targetUnit: String = "",
)

@Entity(
    tableName = "day_plans",
    indices = [Index(value = ["date"], unique = true)],
)
data class DayPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 计划日期,ISO 格式 yyyy-MM-dd,一个日期只有一份计划 */
    val date: String,
    val name: String,
    val status: PlanStatus = PlanStatus.NOT_STARTED,
    /** 开始时间( epoch 毫秒),默认为当天 08:00 */
    val startEpochMillis: Long,
    /** 预计时长(分钟) */
    val durationMinutes: Int = 60,
    /** 提前提醒的分钟数,null 表示不提醒 */
    val reminderMinutes: Int? = null,
    val note: String = "",
    /** 来源模板 id,仅作为追溯信息;计划内容不依赖模板实时数据 */
    val sourceTemplateId: Long? = null,
    val sourceTemplateName: String = "",
    /** 系统日历事件 id,已同步后保存,避免重复创建 */
    val calendarEventId: Long? = null,
    val calendarSyncState: SyncState = SyncState.NOT_SYNCED,
)

@Entity(
    tableName = "plan_actions",
    foreignKeys = [
        ForeignKey(
            entity = DayPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("planId")],
)
data class PlanAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val sortOrder: Int,
    val name: String,
    val targetSets: Int = 1,
)

/** 通用指标:目标值和实际值都保存在这里,单位随指标自由定义(kg、次、km、分/公里、RPE…) */
@Entity(
    tableName = "action_metrics",
    foreignKeys = [
        ForeignKey(
            entity = PlanAction::class,
            parentColumns = ["id"],
            childColumns = ["actionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("actionId")],
)
data class ActionMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionId: Long,
    val sortOrder: Int = 0,
    val name: String,
    val targetValue: String? = null,
    val targetUnit: String = "",
    val actualValue: String? = null,
    val actualUnit: String? = null,
)

data class TemplateActionWithMetrics(
    @Embedded val action: TemplateAction,
    @Relation(parentColumn = "id", entityColumn = "templateActionId")
    val metrics: List<TemplateMetric>,
)

data class TemplateWithActions(
    @Embedded val template: WorkoutTemplate,
    @Relation(entity = TemplateAction::class, parentColumn = "id", entityColumn = "templateId")
    val actions: List<TemplateActionWithMetrics>,
)

data class PlanActionWithMetrics(
    @Embedded val action: PlanAction,
    @Relation(parentColumn = "id", entityColumn = "actionId")
    val metrics: List<ActionMetric>,
)

data class PlanWithActions(
    @Embedded val plan: DayPlan,
    @Relation(entity = PlanAction::class, parentColumn = "id", entityColumn = "planId")
    val actions: List<PlanActionWithMetrics>,
)
