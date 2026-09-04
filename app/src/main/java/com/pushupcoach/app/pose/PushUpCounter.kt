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
    val formGood: Boolean = false,
    val setupReady: Boolean = false
)

/** Stateful repetition detector with hysteresis, visibility checks and a short debounce. */
class PushUpCounter(
    private val downThreshold: Float = 110f,
    private val upThreshold: Float = 145f,
    private val debounceMs: Long = 450L,
    private val setupHoldMs: Long = 700L,
    private val trackingGraceMs: Long = 1200L
) {
    private var phase = PushUpPhase.WAITING
    private var lastRepAt = 0L
    private var setupStartedAt = 0L
    private var lastTrackedAt = 0L
    private var setupReady = false

    fun reset() {
        phase = PushUpPhase.WAITING
        lastRepAt = 0L
        setupStartedAt = 0L
        lastTrackedAt = 0L
        setupReady = false
    }

    fun update(landmarks: List<Point3>, timestampMs: Long): PushUpResult {
        if (landmarks.size < 33) return trackingLost(timestampMs)

        val side = chooseSide(landmarks) ?: return trackingLost(timestampMs)
        val shoulder = landmarks[if (side == 0) 11 else 12]
        val elbow = landmarks[if (side == 0) 13 else 14]
        val wrist = landmarks[if (side == 0) 15 else 16]

        lastTrackedAt = timestampMs
        if (!setupReady) {
            if (setupStartedAt == 0L) setupStartedAt = timestampMs
            if (timestampMs - setupStartedAt < setupHoldMs) {
                return result("상체 자세를 확인하고 있어요…", setup = false)
            }
            setupReady = true
        }

        val elbowAngle = angle(shoulder, elbow, wrist)

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
        return PushUpResult(phase, counted, feedback, elbowAngle, null, true, true)
    }

    private fun chooseSide(points: List<Point3>): Int? {
        fun score(indices: IntArray) = indices.minOf { points[it].visibility }
        if (points[0].visibility < 0.30f) return null
        val left = score(intArrayOf(11, 13, 15))
        val right = score(intArrayOf(12, 14, 16))
        val best = if (left >= right) 0 else 1
        return if (maxOf(left, right) >= 0.35f) best else null
    }

    private fun trackingLost(timestampMs: Long): PushUpResult {
        if (setupReady && timestampMs - lastTrackedAt <= trackingGraceMs) {
            return result("잠시 가려졌어요. 자세를 유지하세요", setup = true)
        }
        setupReady = false
        setupStartedAt = 0L
        phase = PushUpPhase.WAITING
        return result("머리, 어깨와 팔이 보이게 해주세요", setup = false)
    }

    private fun result(message: String, elbow: Float? = null, setup: Boolean = setupReady) =
        PushUpResult(phase, false, message, elbow, null, setup, setup)

    private fun angle(a: Point3, vertex: Point3, c: Point3): Float {
        val abx = a.x - vertex.x; val aby = a.y - vertex.y; val abz = a.z - vertex.z
        val cbx = c.x - vertex.x; val cby = c.y - vertex.y; val cbz = c.z - vertex.z
        val dot = abx * cbx + aby * cby + abz * cbz
        val magnitude = sqrt(abx * abx + aby * aby + abz * abz) * sqrt(cbx * cbx + cby * cby + cbz * cbz)
        if (magnitude < 0.000001f) return 0f
        return Math.toDegrees(acos((dot / magnitude).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }
}
