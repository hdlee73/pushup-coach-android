package com.pushupcoach.app.pose

import kotlin.math.acos
import kotlin.math.sqrt

data class Point3(val x: Float, val y: Float, val z: Float = 0f, val visibility: Float = 1f)

enum class PushUpPhase { WAITING, UP, DOWN }

data class PushUpResult(
    val phase: PushUpPhase,
    val counted: Boolean,
    val feedback: String,
    val elbowAngle: Float? = null,
    val bodyAngle: Float? = null,
    val formGood: Boolean = false
)

/** Stateful repetition detector with hysteresis, visibility checks and a short debounce. */
class PushUpCounter(
    private val downThreshold: Float = 110f,
    private val upThreshold: Float = 145f,
    private val minimumBodyAngle: Float = 135f,
    private val debounceMs: Long = 450L
) {
    private var phase = PushUpPhase.WAITING
    private var lastRepAt = 0L

    fun reset() { phase = PushUpPhase.WAITING; lastRepAt = 0L }

    fun update(landmarks: List<Point3>, timestampMs: Long): PushUpResult {
        if (landmarks.size < 33) return result("몸 전체가 보이도록 옆으로 놓아주세요")

        val side = chooseSide(landmarks) ?: return result("어깨, 팔, 골반과 발목이 보이게 해주세요")
        val shoulder = landmarks[if (side == 0) 11 else 12]
        val elbow = landmarks[if (side == 0) 13 else 14]
        val wrist = landmarks[if (side == 0) 15 else 16]
        val hip = landmarks[if (side == 0) 23 else 24]
        val ankle = landmarks[if (side == 0) 27 else 28]

        val elbowAngle = angle(shoulder, elbow, wrist)
        val bodyAngle = angle(shoulder, hip, ankle)
        if (bodyAngle < minimumBodyAngle) {
            return result("엉덩이를 낮추고 몸을 일직선으로 만드세요", elbowAngle, bodyAngle)
        }

        var counted = false
        var feedback = "팔을 굽혀 천천히 내려가세요"
        when {
            elbowAngle <= downThreshold -> {
                phase = PushUpPhase.DOWN
                feedback = "좋아요! 이제 끝까지 밀어 올리세요"
            }
            elbowAngle >= upThreshold -> {
                if (phase == PushUpPhase.DOWN && timestampMs - lastRepAt >= debounceMs) {
                    counted = true
                    lastRepAt = timestampMs
                    feedback = "완벽해요!"
                } else if (phase == PushUpPhase.WAITING) {
                    feedback = "준비됐어요. 시작하세요!"
                }
                phase = PushUpPhase.UP
            }
            phase == PushUpPhase.DOWN -> feedback = "조금만 더 밀어 올리세요"
            else -> feedback = "팔꿈치가 90도가 될 때까지 내려가세요"
        }
        return PushUpResult(phase, counted, feedback, elbowAngle, bodyAngle, true)
    }

    private fun chooseSide(points: List<Point3>): Int? {
        fun score(indices: IntArray) = indices.minOf { points[it].visibility }
        val left = score(intArrayOf(11, 13, 15, 23, 27))
        val right = score(intArrayOf(12, 14, 16, 24, 28))
        val best = if (left >= right) 0 else 1
        return if (maxOf(left, right) >= 0.40f) best else null
    }

    private fun result(message: String, elbow: Float? = null, body: Float? = null) =
        PushUpResult(phase, false, message, elbow, body, false)

    private fun angle(a: Point3, vertex: Point3, c: Point3): Float {
        val abx = a.x - vertex.x; val aby = a.y - vertex.y; val abz = a.z - vertex.z
        val cbx = c.x - vertex.x; val cby = c.y - vertex.y; val cbz = c.z - vertex.z
        val dot = abx * cbx + aby * cby + abz * cbz
        val magnitude = sqrt(abx * abx + aby * aby + abz * abz) * sqrt(cbx * cbx + cby * cby + cbz * cbz)
        if (magnitude < 0.000001f) return 0f
        return Math.toDegrees(acos((dot / magnitude).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }
}
