package com.example.trainlog.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import com.example.trainlog.data.entity.DayPlan
import com.example.trainlog.data.entity.PlanActionWithMetrics

/**
 * 系统日历同步的可替换接口。单元测试用 Fake 实现,
 * 生产实现 [SystemCalendarSyncer] 走 ContentResolver + CalendarContract。
 */
interface CalendarSyncer {

    /** 新建日历事件,成功返回事件 id,失败返回 null */
    fun insertEvent(
        title: String,
        description: String,
        startMillis: Long,
        endMillis: Long,
        reminderMinutes: Int?,
    ): Long?

    /** 更新已有事件;事件已被用户在系统日历中删除时返回 false */
    fun updateEvent(
        eventId: Long,
        title: String,
        description: String,
        startMillis: Long,
        endMillis: Long,
        reminderMinutes: Int?,
    ): Boolean

    /** 删除事件 */
    fun deleteEvent(eventId: Long): Boolean
}

/**
 * 日历同步编排:封装"创建 vs 更新"分支。
 * 已有 eventId 时优先更新原事件,更新失败(事件被删)则回退为重新创建,
 * 保证同一计划不产生重复事件。
 */
class CalendarSyncService(private val syncer: CalendarSyncer) {

    sealed interface Outcome {
        /** 首次同步,创建了新事件 */
        data class Created(val eventId: Long) : Outcome

        /** 计划已有 eventId,更新了原事件 */
        data class Updated(val eventId: Long) : Outcome

        data class Failed(val reason: String) : Outcome
    }

    fun syncPlan(
        plan: DayPlan,
        actions: List<PlanActionWithMetrics>,
        descriptionText: String,
    ): Outcome {
        val endMillis = plan.startEpochMillis + plan.durationMinutes * 60_000L
        if (plan.calendarEventId != null) {
            val ok = syncer.updateEvent(
                eventId = plan.calendarEventId,
                title = plan.name,
                description = descriptionText,
                startMillis = plan.startEpochMillis,
                endMillis = endMillis,
                reminderMinutes = plan.reminderMinutes,
            )
            if (ok) return Outcome.Updated(plan.calendarEventId)
        }
        val newId = syncer.insertEvent(
            title = plan.name,
            description = descriptionText,
            startMillis = plan.startEpochMillis,
            endMillis = endMillis,
            reminderMinutes = plan.reminderMinutes,
        ) ?: return Outcome.Failed("写入系统日历失败")
        return Outcome.Created(newId)
    }

    /** 删除计划对应的日历事件;没有同步过则无需删除,返回 false */
    fun deletePlanEvent(plan: DayPlan): Boolean {
        val eventId = plan.calendarEventId ?: return false
        return syncer.deleteEvent(eventId)
    }
}

/** 基于 CalendarContract 的生产实现。必须在持有日历权限后调用。 */
class SystemCalendarSyncer(context: Context) : CalendarSyncer {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

    /** 选择第一个可写日历;优先同步型日历,否则退回第一个。 */
    private fun pickCalendarId(): Long? {
        val cursor = try {
            resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                ),
                null,
                null,
                "${CalendarContract.Calendars._ID} ASC",
            )
        } catch (e: Exception) {
            return null
        } ?: return null
        return cursor.use { c ->
            var firstId: Long? = null
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (firstId == null) firstId = id
                if (c.getInt(1) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) return id
            }
            firstId
        }
    }

    private fun baseValues(
        title: String,
        description: String,
        startMillis: Long,
        endMillis: Long,
    ) = android.content.ContentValues().apply {
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DESCRIPTION, description)
        put(CalendarContract.Events.DTSTART, startMillis)
        put(CalendarContract.Events.DTEND, endMillis)
        put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        put(CalendarContract.Events.ALL_DAY, 0)
    }

    private fun replaceReminders(eventId: Long, reminderMinutes: Int?) {
        resolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
        )
        if (reminderMinutes != null) {
            val values = android.content.ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_DEFAULT)
            }
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
        }
    }

    override fun insertEvent(
        title: String,
        description: String,
        startMillis: Long,
        endMillis: Long,
        reminderMinutes: Int?,
    ): Long? {
        return try {
            val calendarId = pickCalendarId() ?: return null
            val uri: Uri? = resolver.insert(
                CalendarContract.Events.CONTENT_URI,
                baseValues(title, description, startMillis, endMillis).apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                },
            )
            val eventId = uri?.lastPathSegment?.toLongOrNull() ?: return null
            replaceReminders(eventId, reminderMinutes)
            eventId
        } catch (e: Exception) {
            null
        }
    }

    override fun updateEvent(
        eventId: Long,
        title: String,
        description: String,
        startMillis: Long,
        endMillis: Long,
        reminderMinutes: Int?,
    ): Boolean {
        return try {
            val rows = resolver.update(
                CalendarContract.Events.CONTENT_URI,
                baseValues(title, description, startMillis, endMillis),
                "${CalendarContract.Events._ID} = ?",
                arrayOf(eventId.toString()),
            )
            if (rows <= 0) return false
            replaceReminders(eventId, reminderMinutes)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun deleteEvent(eventId: Long): Boolean {
        return try {
            resolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
            )
            resolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events._ID} = ?",
                arrayOf(eventId.toString()),
            ) > 0
        } catch (e: Exception) {
            false
        }
    }
}
