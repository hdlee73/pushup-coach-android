package com.pushupcoach.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    context: Context,
    private val listener: Listener
) : AutoCloseable {
    interface Listener {
        fun onResult(result: PoseLandmarkerResult, imageWidth: Int, imageHeight: Int)
        fun onError(message: String)
    }

    private val landmarker: PoseLandmarker

    init {
        val base = BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.55f)
            .setMinPosePresenceConfidence(0.55f)
            .setMinTrackingConfidence(0.55f)
            .setResultListener(::returnResult)
            .setErrorListener { listener.onError(it.message ?: "자세 인식 오류") }
            .build()
        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detect(imageProxy: ImageProxy, frontCamera: Boolean) {
        val rotation = imageProxy.imageInfo.rotationDegrees
        val source = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        imageProxy.use { source.copyPixelsFromBuffer(it.planes[0].buffer) }
        val matrix = Matrix().apply {
            postRotate(rotation.toFloat())
            if (frontCamera) postScale(-1f, 1f)
        }
        val bitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        val mpImage = BitmapImageBuilder(bitmap).build()
        landmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
    }

    private fun returnResult(result: PoseLandmarkerResult, input: MPImage) {
        listener.onResult(result, input.width, input.height)
    }

    override fun close() = landmarker.close()
}
