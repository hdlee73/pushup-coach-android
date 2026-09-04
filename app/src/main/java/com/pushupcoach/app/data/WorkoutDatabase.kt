package com.pushupcoach.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "daily_workouts", primaryKeys = ["date"])
data class DailyWorkout(
    val date: String,
    val count: Int,
    val goal: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM daily_workouts WHERE date = :date LIMIT 1")
    suspend fun find(date: String): DailyWorkout?

    @Query("SELECT * FROM daily_workouts ORDER BY date DESC")
    suspend fun all(): List<DailyWorkout>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(day: DailyWorkout)
}

@Database(entities = [DailyWorkout::class], version = 1, exportSchema = false)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var instance: WorkoutDatabase? = null

        fun get(context: Context): WorkoutDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                WorkoutDatabase::class.java,
                "pushup-coach.db"
            ).build().also { instance = it }
        }
    }
}
