package com.pushupcoach.app.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.pushupcoach.app.pose.Point3

class PoseOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(88, 230, 182); strokeWidth = 7f; style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 13f; strokeCap = Paint.Cap.ROUND }
    private var points: List<Point3> = emptyList()
    private val connections = arrayOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16, 11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28)

    fun update(newPoints: List<Point3>) { points = newPoints; postInvalidate() }
    fun clear() { points = emptyList(); postInvalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 33) return
        connections.forEach { (a, b) ->
            if (points[a].visibility > .5f && points[b].visibility > .5f) {
                canvas.drawLine(points[a].x * width, points[a].y * height, points[b].x * width, points[b].y * height, line)
            }
        }
        points.forEach { if (it.visibility > .5f) canvas.drawPoint(it.x * width, it.y * height, dot) }
    }
}
