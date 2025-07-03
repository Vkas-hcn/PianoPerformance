package com.bdrink.poison.quench.pianoperformance

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PianoHistoryActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmpty: LinearLayout

    private lateinit var adapter: PianoHistoryAdapter
    private lateinit var pcmPlayer: PcmPlayerManager

    private val records = mutableListOf<PianoRecord>()
    private var currentPlayingRecord: PianoRecord? = null

    // 存储权限请求
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予后重新执行下载
            currentDownloadRecord?.let { record ->
                downloadToPublicStorage(record)
            }
        } else {
            showStoragePermissionDeniedDialog()
        }
    }

    private var currentDownloadRecord: PianoRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_piano_history)

        initViews()
        initPlayer()
        setupRecyclerView()
        loadRecordHistory()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.back1)
        recyclerView = findViewById(R.id.recycler_view_history)
        layoutEmpty = findViewById(R.id.layout_empty)

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun initPlayer() {
        pcmPlayer = PcmPlayerManager()

        // 设置播放状态监听
        pcmPlayer.setOnPlayStateChangeListener { isPlaying, currentPosition, totalDuration ->
            // 更新播放进度
            if (isPlaying && totalDuration > 0) {
                val progress = ((currentPosition * 100) / totalDuration).toInt()
                currentPlayingRecord?.let { record ->
                    val position = adapter.findPositionByFile(record.file)
                    if (position >= 0) {
                        adapter.updatePlayProgress(position, progress)
                    }
                }
            }
        }

        // 设置播放完成监听
        pcmPlayer.setOnPlayCompleteListener {
            currentPlayingRecord = null
            adapter.stopPlaying()
        }
    }

    private fun setupRecyclerView() {
        adapter = PianoHistoryAdapter(
            onItemClick = { record -> playOrPauseRecord(record) },
            onPlayPauseClick = { record -> playOrPauseRecord(record) },
            onEditClick = { record -> showEditDialog(record) },
            onDownloadClick = { record -> downloadRecord(record) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadRecordHistory() {
        lifecycleScope.launch {
            try {
                val recordFiles = withContext(Dispatchers.IO) {
                    val recordDir = File(getExternalFilesDir(null), "piano_records")
                    Log.d("PianoHistory", "录制目录路径: ${recordDir.absolutePath}")
                    Log.d("PianoHistory", "目录是否存在: ${recordDir.exists()}")

                    if (!recordDir.exists()) {
                        val created = recordDir.mkdirs()
                        Log.d("PianoHistory", "创建目录结果: $created")
                    }

                    val allFiles = recordDir.listFiles()
                    Log.d("PianoHistory", "目录下所有文件数量: ${allFiles?.size ?: 0}")

                    allFiles?.forEach { file ->
                        Log.d("PianoHistory", "文件: ${file.name}, 大小: ${file.length()}, 扩展名: ${file.extension}")
                    }

                    val wavFiles = allFiles
                        ?.filter { it.extension.toLowerCase() == "wav" && it.length() > 44 }
                        ?.sortedByDescending { it.lastModified() }
                        ?: emptyList()

                    Log.d("PianoHistory", "符合条件的WAV文件数量: ${wavFiles.size}")
                    wavFiles
                }

                val pianoRecords = recordFiles.map { PianoRecord.fromFile(it) }
                Log.d("PianoHistory", "转换后的记录数量: ${pianoRecords.size}")

                // 清空Activity的records（仅用于计数）
                records.clear()
                records.addAll(pianoRecords)
                Log.d("PianoHistory", "Activity records大小: ${records.size}")

                // 更新适配器数据
                adapter.updateRecords(pianoRecords)
                Log.d("PianoHistory", "适配器更新后，记录数量: ${adapter.getAllRecords().size}")

                // 显示/隐藏空状态 - 基于适配器中的实际数据
                val adapterRecordCount = adapter.getAllRecords().size
                if (adapterRecordCount == 0) {
                    recyclerView.visibility = View.GONE
                    layoutEmpty.visibility = View.VISIBLE
                    Log.d("PianoHistory", "显示空状态")
                } else {
                    recyclerView.visibility = View.VISIBLE
                    layoutEmpty.visibility = View.GONE
                    Log.d("PianoHistory", "显示录制列表，共${adapterRecordCount}条记录")
                }

            } catch (e: Exception) {
                Log.e("PianoHistory", "Failed to load recording history", e)
                Toast.makeText(this@PianoHistoryActivity, "Failed to load recording history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 播放或暂停录制文件
     */
    private fun playOrPauseRecord(record: PianoRecord) {
        lifecycleScope.launch {
            try {
                if (currentPlayingRecord == record && pcmPlayer.isPlaying()) {
                    // 当前正在播放这个文件，暂停
                    pcmPlayer.pause()
                    adapter.stopPlaying()
                } else if (currentPlayingRecord == record && !pcmPlayer.isPlaying()) {
                    // 当前文件已暂停，恢复播放
                    pcmPlayer.resume()
                    val position = adapter.findPositionByFile(record.file)
                    adapter.setCurrentPlayingPosition(position)
                } else {
                    // 播放新文件
                    if (pcmPlayer.isPlaying()) {
                        pcmPlayer.stop()
                    }

                    currentPlayingRecord = record
                    val position = adapter.findPositionByFile(record.file)
                    adapter.setCurrentPlayingPosition(position)

                    pcmPlayer.play(record.file)
                }
            } catch (e: Exception) {
                Toast.makeText(this@PianoHistoryActivity, "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
                currentPlayingRecord = null
                adapter.stopPlaying()
            }
        }
    }

    /**
     * 显示编辑文件名对话框
     */
    private fun showEditDialog(record: PianoRecord) {
        val editText = EditText(this).apply {
            setText(record.displayName)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("编辑文件名")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != record.displayName) {
                    renameRecord(record, newName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 重命名录制文件
     */
    private fun renameRecord(record: PianoRecord, newName: String) {
        lifecycleScope.launch {
            try {
                // 通过适配器更新名称
                val position = adapter.findPositionByFile(record.file)
                if (position >= 0) {
                    adapter.updateItemName(position, newName)
                    Toast.makeText(this@PianoHistoryActivity, "The file name has been updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@PianoHistoryActivity, "File not found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PianoHistoryActivity, "Rename failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 下载录制文件到手机存储
     */
    private fun downloadRecord(record: PianoRecord) {
        currentDownloadRecord = record

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上，使用MediaStore
            downloadToPublicStorage(record)
        } else {
            // Android 9及以下，需要存储权限
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED -> {
                    downloadToPublicStorage(record)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    showStoragePermissionRationaleDialog()
                }
                else -> {
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    /**
     * 下载文件到公共存储
     */
    private fun downloadToPublicStorage(record: PianoRecord) {
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        downloadUsingMediaStore(record)
                    } else {
                        downloadUsingLegacyStorage(record)
                    }
                }

                if (success) {
                    Toast.makeText(this@PianoHistoryActivity, "The file has been saved to the music folder", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@PianoHistoryActivity, "The download failed", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@PianoHistoryActivity, "The download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 使用MediaStore保存文件（Android 10+）
     */
    private suspend fun downloadUsingMediaStore(record: PianoRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${record.displayName}.wav")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Piano Records")
            }

            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(record.file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 使用传统方式保存文件（Android 9及以下）
     */
    private suspend fun downloadUsingLegacyStorage(record: PianoRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Piano Records")
            if (!musicDir.exists()) {
                musicDir.mkdirs()
            }

            val targetFile = File(musicDir, "${record.displayName}.wav")
            FileOutputStream(targetFile).use { outputStream ->
                FileInputStream(record.file).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // 通知媒体扫描器
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(targetFile)
            sendBroadcast(intent)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun showStoragePermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要存储权限")
            .setMessage("下载文件到手机需要存储权限，请授予权限以继续。")
            .setPositiveButton("授权") { _, _ ->
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showStoragePermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("权限被拒绝")
            .setMessage("存储权限被拒绝，无法下载文件。您可以在设置中手动开启权限。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // 每次返回页面时重新加载数据，确保显示最新的录制文件
        Log.d("PianoHistory", "页面恢复，重新加载录制历史")
        loadRecordHistory()
    }

    override fun onPause() {
        super.onPause()
        // 暂停播放
        if (pcmPlayer.isPlaying()) {
            pcmPlayer.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pcmPlayer.release()
    }
}