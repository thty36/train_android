package com.example.trainlog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.trainlog.data.entity.ActionMetric
import com.example.trainlog.data.entity.DayPlan
import com.example.trainlog.data.entity.PlanAction
import com.example.trainlog.data.entity.PlanActionWithMetrics
import com.example.trainlog.data.entity.PlanStatus
import com.example.trainlog.data.entity.PlanWithActions
import com.example.trainlog.data.entity.SyncState
import com.example.trainlog.data.entity.TemplateAction
import com.example.trainlog.data.entity.TemplateMetric
import com.example.trainlog.data.entity.TemplateWithActions
import com.example.trainlog.data.entity.WorkoutTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Transaction
    @Query("SELECT * FROM workout_templates ORDER BY updatedAt DESC")
    fun observeAllWithActions(): Flow<List<TemplateWithActions>>

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun getWithActions(id: Long): TemplateWithActions?

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun getById(id: Long): WorkoutTemplate?

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplate): Long

    @Update
    suspend fun updateTemplate(template: WorkoutTemplate)

    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Insert
    suspend fun insertTemplateAction(action: TemplateAction): Long

    @Insert
    suspend fun insertTemplateActions(actions: List<TemplateAction>): List<Long>

    @Insert
    suspend fun insertTemplateMetrics(metrics: List<TemplateMetric>): List<Long>

    @Query("DELETE FROM template_actions WHERE templateId = :templateId")
    suspend fun deleteTemplateActions(templateId: Long)
}

@Dao
interface PlanDao {

    @Query("SELECT * FROM day_plans WHERE date BETWEEN :fromDate AND :toDate ORDER BY date")
    fun observeBetween(fromDate: String, toDate: String): Flow<List<DayPlan>>

    @Query("SELECT * FROM day_plans WHERE date = :date LIMIT 1")
    suspend fun findByDate(date: String): DayPlan?

    @Transaction
    @Query("SELECT * FROM day_plans WHERE id = :planId")
    fun observeWithActions(planId: Long): Flow<PlanWithActions?>

    @Transaction
    @Query("SELECT * FROM day_plans WHERE id = :planId")
    suspend fun getWithActions(planId: Long): PlanWithActions?

    @Insert
    suspend fun insertPlan(plan: DayPlan): Long

    @Update
    suspend fun updatePlan(plan: DayPlan)

    @Query("DELETE FROM day_plans WHERE id = :planId")
    suspend fun deletePlan(planId: Long)

    @Query("UPDATE day_plans SET calendarEventId = :eventId, calendarSyncState = :syncState WHERE id = :planId")
    suspend fun updateCalendarRef(planId: Long, eventId: Long?, syncState: SyncState)

    @Insert
    suspend fun insertPlanAction(action: PlanAction): Long

    @Insert
    suspend fun insertPlanActions(actions: List<PlanAction>): List<Long>

    @Insert
    suspend fun insertMetrics(metrics: List<ActionMetric>): List<Long>

    @Query("DELETE FROM plan_actions WHERE planId = :planId")
    suspend fun deletePlanActions(planId: Long)

    @Update
    suspend fun updatePlanAction(action: PlanAction)

    @Query("DELETE FROM plan_actions WHERE id = :actionId")
    suspend fun deletePlanAction(actionId: Long)

    @Query("SELECT * FROM plan_actions WHERE planId = :planId ORDER BY sortOrder")
    suspend fun actionsOf(planId: Long): List<PlanAction>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM plan_actions WHERE planId = :planId")
    suspend fun nextSortOrder(planId: Long): Int

    @Insert
    suspend fun insertMetric(metric: ActionMetric): Long

    @Insert
    suspend fun insertMetricList(metrics: List<ActionMetric>): List<Long>

    @Update
    suspend fun updateMetric(metric: ActionMetric)

    @Query("DELETE FROM action_metrics WHERE id = :metricId")
    suspend fun deleteMetric(metricId: Long)

    @Query("DELETE FROM action_metrics WHERE actionId = :actionId")
    suspend fun deleteMetricsOf(actionId: Long)

    @Query("SELECT * FROM action_metrics WHERE actionId = :actionId ORDER BY sortOrder")
    suspend fun metricsOf(actionId: Long): List<ActionMetric>

    @Query("SELECT COUNT(*) FROM day_plans WHERE status = :status AND date BETWEEN :fromDate AND :toDate")
    suspend fun countByStatus(status: PlanStatus, fromDate: String, toDate: String): Int
}
