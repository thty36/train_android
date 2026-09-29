package com.example.trainlog

import com.example.trainlog.data.calendar.CalendarSyncService
import com.example.trainlog.data.calendar.CalendarSyncer
import com.example.trainlog.data.entity.DayPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日历同步的创建 / 更新分支测试。
 * 通过 Fake CalendarSyncer 验证:已有 eventId 时更新原事件、不重复插入;
 * 事件被系统侧删除时回退重建。
 */
class CalendarSyncServiceTest {

    private class FakeSyncer : CalendarSyncer {
        val insertedTitles = mutableListOf<String>()
        val updatedEventIds = mutableListOf<Pair<Long, String>>()
        val deletedEventIds = mutableListOf<Long>()
        var nextInsertId = 555L
        var updateSucceeds = true
        var insertFails = false

        override fun insertEvent(
            title: String,
            description: String,
            startMillis: Long,
            endMillis: Long,
            reminderMinutes: Int?,
        ): Long? {
            insertedTitles += title
            return if (insertFails) null else nextInsertId
        }

        override fun updateEvent(
            eventId: Long,
            title: String,
            description: String,
            startMillis: Long,
            endMillis: Long,
            reminderMinutes: Int?,
        ): Boolean {
            updatedEventIds += eventId to title
            return updateSucceeds
        }

        override fun deleteEvent(eventId: Long): Boolean {
            deletedEventIds += eventId
            return true
        }
    }

    private fun plan(eventId: Long? = null) = DayPlan(
        id = 1,
        date = "2026-09-28",
        name = "跑步计划 1",
        startEpochMillis = 1_000_000L,
        durationMinutes = 60,
        reminderMinutes = 15,
        calendarEventId = eventId,
    )

    @Test
    fun `首次同步没有 eventId 时创建新事件`() {
        val fake = FakeSyncer()
        val service = CalendarSyncService(fake)

        val outcome = service.syncPlan(plan(eventId = null), actions = emptyList(), descriptionText = "desc")

        assertTrue(outcome is CalendarSyncService.Outcome.Created)
        assertEquals(555L, (outcome as CalendarSyncService.Outcome.Created).eventId)
        assertEquals(listOf("跑步计划 1"), fake.insertedTitles)
        assertTrue(fake.updatedEventIds.isEmpty())
    }

    @Test
    fun `再次同步同一计划时更新原事件且不重复创建`() {
        val fake = FakeSyncer()
        val service = CalendarSyncService(fake)

        val outcome = service.syncPlan(plan(eventId = 42), actions = emptyList(), descriptionText = "desc v2")

        assertTrue(outcome is CalendarSyncService.Outcome.Updated)
        assertEquals(42L, (outcome as CalendarSyncService.Outcome.Updated).eventId)
        assertEquals(listOf(42L to "跑步计划 1"), fake.updatedEventIds)
        // 关键:不能再插入新事件
        assertTrue(fake.insertedTitles.isEmpty())
    }

    @Test
    fun `原事件被系统日历删除后回退为重新创建`() {
        val fake = FakeSyncer().apply { updateSucceeds = false }
        val service = CalendarSyncService(fake)

        val outcome = service.syncPlan(plan(eventId = 42), actions = emptyList(), descriptionText = "desc")

        assertTrue(outcome is CalendarSyncService.Outcome.Created)
        assertEquals(555L, (outcome as CalendarSyncService.Outcome.Created).eventId)
        assertEquals(listOf(42L to "跑步计划 1"), fake.updatedEventIds)
        assertEquals(listOf("跑步计划 1"), fake.insertedTitles)
    }

    @Test
    fun `插入失败时返回失败结果`() {
        val fake = FakeSyncer().apply { insertFails = true }
        val service = CalendarSyncService(fake)

        val outcome = service.syncPlan(plan(eventId = null), actions = emptyList(), descriptionText = "desc")

        assertTrue(outcome is CalendarSyncService.Outcome.Failed)
    }

    @Test
    fun `删除已同步计划时删除对应事件`() {
        val fake = FakeSyncer()
        val service = CalendarSyncService(fake)

        val deleted = service.deletePlanEvent(plan(eventId = 42))

        assertTrue(deleted)
        assertEquals(listOf(42L), fake.deletedEventIds)
    }

    @Test
    fun `未同步过的计划删除时不调用日历`() {
        val fake = FakeSyncer()
        val service = CalendarSyncService(fake)

        val deleted = service.deletePlanEvent(plan(eventId = null))

        assertEquals(false, deleted)
        assertTrue(fake.deletedEventIds.isEmpty())
    }
}
