package com.pushupcoach.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.pushupcoach.app.camera.PoseLandmarkerHelper
import com.pushupcoach.app.data.DailyWorkout
import com.pushupcoach.app.data.WorkoutRepository
import com.pushupcoach.app.databinding.ActivityMainBinding
import com.pushupcoach.app.pose.Point3
import com.pushupcoach.app.pose.PushUpCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), PoseLandmarkerHelper.Listener, TextToSpeech.OnInitListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: WorkoutRepository
    private lateinit var cameraExecutor: ExecutorService
    private var landmarker: PoseLandmarkerHelper? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val counter = PushUpCounter()
    private var tts: TextToSpeech? = null
    private var sessionCount = 0
    private var todayCount = 0
    private var currentGoal = 30
    private var workoutVisible = false

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openWorkout() else Snackbar.make(binding.root, "카메라 권한이 있어야 자세를 인식할 수 있어요", Snackbar.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = WorkoutRepository(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        tts = TextToSpeech(this, this)

        binding.dateText.text = LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
        binding.startButton.setOnClickListener { requestWorkout() }
        binding.finishButton.setOnClickListener { closeWorkout() }
        binding.goalButton.setOnClickListener { showGoalDialog() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (workoutVisible) closeWorkout() else finish() }
        })
        refreshDashboard()
    }

    private fun requestWorkout() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openWorkout()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun openWorkout() {
        workoutVisible = true
        sessionCount = 0
        counter.reset()
        binding.dashboard.visibility = View.GONE
        binding.workout.visibility = View.VISIBLE
        binding.liveCount.text = "0"
        binding.liveGoal.text = "오늘 목표 ${currentGoal}개 · 현재 ${todayCount}개"
        binding.feedbackText.text = "몸 전체가 보이도록 휴대폰을 옆에 놓아주세요"
        startCamera()
    }

    private fun startCamera() {
        try { landmarker = PoseLandmarkerHelper(this, this) }
        catch (e: Exception) {
            binding.feedbackText.text = "자세 인식 모델을 불러오지 못했어요"
            return
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = binding.previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { useCase ->
                    useCase.setAnalyzer(cameraExecutor) { image ->
                        try { landmarker?.detect(image, true) ?: image.close() }
                        catch (_: Exception) { image.close() }
                    }
                }
            try {
                cameraProvider?.unbindAll()
                // Use the selfie camera so the athlete can see live feedback while exercising.
                // CameraX resolves the correct front-facing camera for the active Fold display.
                cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            } catch (e: Exception) { binding.feedbackText.text = "카메라를 시작하지 못했어요" }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResult(result: PoseLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        val source = result.landmarks().firstOrNull()
        val points = source?.map { Point3(it.x(), it.y(), it.z(), it.visibility().orElse(0f)) }.orEmpty()
        val evaluation = counter.update(points, result.timestampMs())
        runOnUiThread {
            binding.poseOverlay.update(points)
            binding.feedbackText.text = evaluation.feedback
            binding.feedbackText.setTextColor(ContextCompat.getColor(this, if (evaluation.formGood) R.color.white else R.color.warning))
            if (evaluation.counted) recordRep()
        }
    }

    override fun onError(message: String) = runOnUiThread { binding.feedbackText.text = message }

    private fun recordRep() {
        sessionCount++
        binding.liveCount.text = sessionCount.toString()
        lifecycleScope.launch {
            todayCount = withContext(Dispatchers.IO) { repository.addRep() }
            binding.liveGoal.text = "오늘 목표 ${currentGoal}개 · 현재 ${todayCount}개"
            tts?.speak(sessionCount.toString(), TextToSpeech.QUEUE_FLUSH, null, "rep-$sessionCount")
            if (todayCount == currentGoal) tts?.speak("오늘 목표 달성!", TextToSpeech.QUEUE_ADD, null, "goal")
        }
    }

    private fun closeWorkout() {
        workoutVisible = false
        cameraProvider?.unbindAll()
        landmarker?.close(); landmarker = null
        binding.poseOverlay.clear()
        binding.workout.visibility = View.GONE
        binding.dashboard.visibility = View.VISIBLE
        refreshDashboard()
    }

    private fun refreshDashboard() = lifecycleScope.launch {
        val data = withContext(Dispatchers.IO) { repository.dashboard() }
        todayCount = data.todayCount; currentGoal = data.goal
        binding.todayCount.text = data.todayCount.toString()
        binding.goalCount.text = " / ${data.goal}개"
        val percentage = if (data.goal == 0) 0 else (data.todayCount * 100 / data.goal).coerceAtMost(100)
        binding.progress.progress = percentage
        binding.progressLabel.text = if (data.todayCount >= data.goal) "오늘 목표를 달성했어요 ✓" else "${data.goal - data.todayCount}개 남았어요"
        binding.streakText.text = "${data.streak}일"
        binding.totalText.text = "${data.total}개"
        renderHistory(data.history)
    }

    private fun renderHistory(history: List<DailyWorkout>) {
        binding.historyList.removeAllViews()
        if (history.isEmpty()) {
            binding.historyList.addView(historyRow("아직 기록이 없어요", "첫 운동을 시작해보세요", false))
            return
        }
        history.take(7).forEach { day ->
            val date = LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
            binding.historyList.addView(historyRow(date, "${day.count} / ${day.goal}개", day.count >= day.goal))
        }
    }

    private fun historyRow(left: String, right: String, completed: Boolean): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(18.dp, 16.dp, 18.dp, 16.dp)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.card_bg)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = 8.dp; layoutParams = params
            addView(TextView(context).apply { text = left; setTextColor(ContextCompat.getColor(context, R.color.white)); textSize = 15f }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply { text = if (completed) "$right  ✓" else right; setTextColor(ContextCompat.getColor(context, if (completed) R.color.mint else R.color.slate)); textSize = 15f })
        }
    }

    private fun showGoalDialog() {
        val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(currentGoal.toString()); selectAll() }
        AlertDialog.Builder(this).setTitle("하루 목표 설정").setMessage("꾸준히 달성 가능한 개수를 정해보세요.").setView(input)
            .setPositiveButton("저장") { _, _ ->
                val value = input.text.toString().toIntOrNull()?.coerceIn(1, 1000) ?: return@setPositiveButton
                lifecycleScope.launch { withContext(Dispatchers.IO) { repository.setGoal(value) }; refreshDashboard() }
            }.setNegativeButton("취소", null).show()
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN }
    override fun onDestroy() { cameraProvider?.unbindAll(); landmarker?.close(); cameraExecutor.shutdown(); tts?.shutdown(); super.onDestroy() }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
