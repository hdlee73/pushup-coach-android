package com.pushupcoach.app.data

import android.content.Context
import java.time.LocalDate

data class DashboardData(
    val todayCount: Int,
    val goal: Int,
    val streak: Int,
    val total: Int,
    val history: List<DailyWorkout>
)

class WorkoutRepository(context: Context) {
    private val dao = WorkoutDatabase.get(context).workoutDao()
    private val preferences = context.getSharedPreferences("coach_settings", Context.MODE_PRIVATE)

    fun goal(): Int = preferences.getInt("daily_goal", 30)

    suspend fun setGoal(value: Int) {
        preferences.edit().putInt("daily_goal", value).apply()
        val today = LocalDate.now().toString()
        val current = dao.find(today)
        dao.save(DailyWorkout(today, current?.count ?: 0, value))
    }

    suspend fun addRep(): Int {
        val today = LocalDate.now().toString()
        val current = dao.find(today)
        val updated = (current?.count ?: 0) + 1
        dao.save(DailyWorkout(today, updated, current?.goal ?: goal()))
        return updated
    }

    suspend fun dashboard(): DashboardData {
        val all = dao.all()
        val today = LocalDate.now()
        val todayEntry = all.firstOrNull { it.date == today.toString() }
        return DashboardData(
            todayCount = todayEntry?.count ?: 0,
            goal = goal(),
            streak = calculateStreak(all, today),
            total = all.sumOf { it.count },
            history = all.take(14)
        )
    }

    companion object {
        /** Today remains eligible until the day ends; an incomplete today does not erase yesterday's streak. */
        fun calculateStreak(entries: List<DailyWorkout>, today: LocalDate): Int {
            val completed = entries.filter { it.count >= it.goal }.associateBy { LocalDate.parse(it.date) }
            var cursor = if (completed.containsKey(today)) today else today.minusDays(1)
            var streak = 0
            while (completed.containsKey(cursor)) {
                streak++
                cursor = cursor.minusDays(1)
            }
            return streak
        }
    }
}
