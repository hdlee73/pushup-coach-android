package com.pushupcoach.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {
    @Test fun `incomplete today preserves consecutive streak through yesterday`() {
        val today = LocalDate.of(2026, 9, 4)
        val entries = listOf(
            DailyWorkout("2026-09-04", 10, 30),
            DailyWorkout("2026-09-03", 30, 30),
            DailyWorkout("2026-09-02", 40, 30)
        )
        assertEquals(2, WorkoutRepository.calculateStreak(entries, today))
    }
}
