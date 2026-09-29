package com.example.trainlog

import android.app.Application
import android.content.Context
import com.example.trainlog.data.calendar.SystemCalendarSyncer
import com.example.trainlog.data.db.AppDatabase
import com.example.trainlog.data.repo.TrainRepository

class TrainLogApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}

/** 轻量服务定位器,MVP 阶段避免引入 DI 框架。 */
object ServiceLocator {
    @Volatile
    private var initialized = false

    lateinit var repository: TrainRepository
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            repository = TrainRepository(
                db = AppDatabase.build(context),
                calendarSyncer = SystemCalendarSyncer(context),
            )
            initialized = true
        }
    }
}
