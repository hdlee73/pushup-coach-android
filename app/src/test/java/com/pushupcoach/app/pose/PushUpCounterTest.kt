package com.pushupcoach.app.pose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushUpCounterTest {
    @Test fun `counts only after down then full extension`() {
        val counter = PushUpCounter(debounceMs = 0, setupHoldMs = 0)
        assertFalse(counter.update(pose(elbowY = .5f), 1000).counted)
        assertFalse(counter.update(pose(elbowY = .68f), 1100).counted)
        assertTrue(counter.update(pose(elbowY = .5f), 1200).counted)
    }

    @Test fun `counts without visible hips knees or ankles`() {
        val counter = PushUpCounter(debounceMs = 0, setupHoldMs = 0)
        counter.update(pose(.68f), 1000)
        assertTrue(counter.update(pose(.5f), 1200).counted)
    }

    private fun pose(elbowY: Float): List<Point3> {
        val p = MutableList(33) { Point3(0f, 0f, visibility = 0f) }
        p[0] = Point3(.5f, .2f)
        p[11] = Point3(.3f, .5f); p[13] = Point3(.4f, elbowY); p[15] = Point3(.5f, .5f)
        p[23] = Point3(.5f, .5f); p[27] = Point3(.8f, .5f)
        return p
    }
}
