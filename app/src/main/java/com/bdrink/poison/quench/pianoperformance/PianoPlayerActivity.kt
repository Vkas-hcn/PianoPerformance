package com.bdrink.poison.quench.pianoperformance

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PianoPlayerActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnRecord: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var btnZoomIn: ImageButton
    private lateinit var btnZoomOut: ImageButton
    private lateinit var pianoView: PianoView
    private lateinit var pianoNavigation: PianoNavigationView

    private lateinit var audioManager: PianoAudioManager
    private lateinit var recordManager: PianoRecordManager

    // 权限请求
    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_piano_player)

        // 设置横屏
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // 隐藏状态栏和导航栏
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        initViews()
        initManagers()
        setupListeners()
        setupNavigation()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        btnRecord = findViewById(R.id.btn_record)
        btnHistory = findViewById(R.id.btn_history)
        btnZoomIn = findViewById(R.id.btn_zoom_in)
        btnZoomOut = findViewById(R.id.btn_zoom_out)
        pianoView = findViewById(R.id.piano_view)
        pianoNavigation = findViewById(R.id.piano_navigation)
    }

    private fun initManagers() {
        audioManager = PianoAudioManager(this)
        recordManager = PianoRecordManager(this)

        // 检查录音支持
        if (!recordManager.isRecordingSupported()) {
            Toast.makeText(this, "The device does not support the recording function", Toast.LENGTH_LONG).show()
            btnRecord.isEnabled = false
        }

        // 设置钢琴按键点击监听
        pianoView.setOnKeyClickListener { keyIndex ->
            lifecycleScope.launch {
                audioManager.playNote(keyIndex)
            }
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnRecord.setOnClickListener {
            handleRecordButtonClick()
        }

        btnHistory.setOnClickListener {
            // 跳转到历史记录页面
            startActivity(Intent(this, PianoHistoryActivity::class.java))
        }

        btnZoomIn.setOnClickListener {
            pianoView.zoomIn()
        }

        btnZoomOut.setOnClickListener {
            pianoView.zoomOut()
        }
    }

    private fun setupNavigation() {
        // 设置钢琴视图滚动监听
        pianoView.setOnScrollChangeListener { scrollX, totalWidth ->
            val viewWidth = pianoView.getViewWidth()
            pianoNavigation.updateMask(scrollX, totalWidth, viewWidth)
        }

        // 设置导航控件点击监听
        pianoNavigation.setOnPositionChangeListener { ratio ->
            val totalWidth = pianoView.getTotalWidth()
            val viewWidth = pianoView.getViewWidth()
            val maxScrollX = totalWidth - viewWidth
            val targetScrollX = (ratio * maxScrollX).toInt()
            pianoView.scrollToPosition(targetScrollX.coerceAtLeast(0))
        }

        // 初始化蒙版位置
        pianoView.post {
            val scrollX = pianoView.getCurrentScrollX()
            val totalWidth = pianoView.getTotalWidth()
            val viewWidth = pianoView.getViewWidth()
            pianoNavigation.updateMask(scrollX, totalWidth, viewWidth)
        }
    }

    /**
     * 处理录制按钮点击
     */
    private fun handleRecordButtonClick() {
        if (recordManager.isRecording()) {
            // 当前正在录制，点击停止
            stopRecording()
        } else {
            // 当前没有录制，点击开始
            checkRecordPermissionAndRecord()
        }
    }

    private fun checkRecordPermissionAndRecord() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED -> {
                startRecording()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startRecording() {
        // 防止重复点击
        btnRecord.isEnabled = false

        lifecycleScope.launch {
            try {
                val filePath = recordManager.startRecording()

                // 录制开始成功，更新UI
                updateRecordingUI(true)
                Toast.makeText(this@PianoPlayerActivity, "Start recording", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                // 录制失败，恢复UI
                updateRecordingUI(false)
                Toast.makeText(this@PianoPlayerActivity, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // 恢复按钮可点击状态
                btnRecord.isEnabled = true
            }
        }
    }

    private fun stopRecording() {
        // 防止重复点击
        btnRecord.isEnabled = false

        lifecycleScope.launch {
            try {
                val filePath = recordManager.stopRecording()

                // 录制停止成功，更新UI
                updateRecordingUI(false)
                Toast.makeText(this@PianoPlayerActivity, "Recording is complete", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                // 停止失败，保持录制状态
                Toast.makeText(this@PianoPlayerActivity, "Failed to stop recording: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // 恢复按钮可点击状态
                btnRecord.isEnabled = true
            }
        }
    }

    /**
     * 更新录制相关UI状态
     */
    private fun updateRecordingUI(isRecording: Boolean) {
        if (isRecording) {
            // 正在录制状态
            btnRecord.setImageResource(R.drawable.icon_stop)
            // 可以添加录制指示器动画等
        } else {
            // 停止录制状态
            btnRecord.setImageResource(R.drawable.ic_play)
            // 停止录制指示器动画等
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Recording permission is required")
            .setMessage("Recording permission is required to record piano performance, please grant permission to continue.")
            .setPositiveButton("Authorization") { _, _ ->
                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission denied")
            .setMessage("Recording permission is denied and the performance cannot be recorded. You can manually enable permissions in the settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()

        // 如果正在录制，先停止录制
        if (recordManager.isRecording()) {
            lifecycleScope.launch {
                try {
                    recordManager.stopRecording()
                } catch (e: Exception) {
                    // 忽略停止时的异常
                }
            }
        }

        // 释放资源
        audioManager.release()
        recordManager.release()

        // 恢复竖屏
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onPause() {
        super.onPause()
        // 当应用进入后台时，如果正在录制，提示用户
        if (recordManager.isRecording()) {
            Toast.makeText(this, "It's recording, don't quit the app", Toast.LENGTH_SHORT).show()
        }
    }
}