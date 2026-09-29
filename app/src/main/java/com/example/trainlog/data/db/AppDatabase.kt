package com.example.trainlog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.trainlog.data.entity.ActionMetric
import com.example.trainlog.data.entity.DayPlan
import com.example.trainlog.data.entity.PlanAction
import com.example.trainlog.data.entity.PlanStatus
import com.example.trainlog.data.entity.SyncState
import com.example.trainlog.data.entity.TemplateAction
import com.example.trainlog.data.entity.TemplateMetric
import com.example.trainlog.data.entity.WorkoutTemplate

class Converters {
    @TypeConverter
    fun planStatusToString(status: PlanStatus): String = status.name

    @TypeConverter
    fun stringToPlanStatus(value: String): PlanStatus = PlanStatus.valueOf(value)

    @TypeConverter
    fun syncStateToString(state: SyncState): String = state.name

    @TypeConverter
    fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)
}

@Database(
    entities = [
        WorkoutTemplate::class,
        TemplateAction::class,
        TemplateMetric::class,
        DayPlan::class,
        PlanAction::class,
        ActionMetric::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun planDao(): PlanDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "trainlog.db")
                .build()
    }
}
