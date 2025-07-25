package com.bdrink.poison.quench.pianoperformance

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

class PianoRecordManager(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingFile: File? = null
    private var recordingJob: Job? = null
    private var totalAudioLength = 0L

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val channels = 1 // 单声道
    private val bitsPerSample = 16

    private companion object {
        private const val TAG = "PianoRecordManager"
    }

    /**
     * 开始录制 - 立即返回，不阻塞UI
     */
    suspend fun startRecording(): String = withContext(Dispatchers.Main) {
        try {
            if (isRecording) {
                throw RuntimeException("Already in recording")
            }

            // 创建录制文件
            val recordDir = File(context.getExternalFilesDir(null), "piano_records")
            if (!recordDir.exists()) {
                recordDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            recordingFile = File(recordDir, "piano_record_$timestamp.wav")

            // 获取最小缓冲区大小
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
                throw RuntimeException("Unsupported recording parameter configuration")
            }

            // 使用推荐缓冲区大小的2倍，确保录音稳定
            val bufferSize = minBufferSize * 2


            // 初始化AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            // 检查初始化状态
            when (audioRecord?.state) {
                AudioRecord.STATE_INITIALIZED -> {
                    Log.d(TAG, "AudioRecord initialization successfully")
                }
                AudioRecord.STATE_UNINITIALIZED -> {
                    throw RuntimeException("AudioRecord initialization failed - state not initialized")
                }
                else -> {
                    throw RuntimeException("AudioRecord initialization failed - unknown status: ${audioRecord?.state}")
                }
            }

            // 开始录制
            audioRecord?.startRecording()

            // 检查录制状态
            val recordingState = audioRecord?.recordingState
            if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw RuntimeException("Unable to start recording - Recording status: $recordingState")
            }

            isRecording = true
            totalAudioLength = 0L
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                performRecording(bufferSize)
            }

            recordingFile?.absolutePath ?: ""

        } catch (e: SecurityException) {
            throw RuntimeException("Recording failed: There is no recording permission, please check the permission settings")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Recording failed: AudioRecord status is abnormal", e)
            throw RuntimeException("Recording failed: The recording device is abnormal, please try again")
        } catch (e: Exception) {
            cleanup()
            throw RuntimeException("Recording failed：${e.message}")
        }
    }

    /**
     * 录制循环 - 在后台线程执行
     */
    private suspend fun performRecording(bufferSize: Int) = withContext(Dispatchers.IO) {
        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(recordingFile)

            // 写入WAV文件头（临时的，录制完成后会更新）
            writeWavHeader(outputStream, 0)

            val buffer = ShortArray(bufferSize / 2) // Short是2字节，所以除以2

            while (isRecording && currentCoroutineContext().isActive) {
                val samplesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (samplesRead > 0) {
                    // 将short数组转换为byte数组并写入文件
                    val byteArray = ByteArray(samplesRead * 2)
                    for (i in 0 until samplesRead) {
                        val sample = buffer[i]
                        byteArray[i * 2] = (sample.toInt() and 0xFF).toByte()
                        byteArray[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                    }
                    outputStream.write(byteArray)
                    totalAudioLength += byteArray.size
                } else if (samplesRead < 0) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording loop abnormality", e)
        } finally {
            try {
                outputStream?.flush()
                outputStream?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Turn off output stream exception", e)
            }
        }
    }

    /**
     * 停止录制
     */
    suspend fun stopRecording(): String = withContext(Dispatchers.Main) {
        try {

            if (!isRecording) {
                throw RuntimeException("Not currently recording")
            }

            // 标记停止录制
            isRecording = false

            // 等待录制协程完成
            recordingJob?.join()
            recordingJob = null

            // 在IO线程中停止和释放AudioRecord
            withContext(Dispatchers.IO) {
                audioRecord?.let { record ->
                    try {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            record.stop()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "An exception occurred while stopping recording", e)
                    }

                    try {
                        record.release()
                    } catch (e: Exception) {
                        Log.w(TAG, "An exception occurred when releasing AudioRecord", e)
                    }
                }
                audioRecord = null

                // 更新WAV文件头的正确长度信息
                recordingFile?.let { file ->
                    if (file.exists()) {
                        updateWavHeader(file, totalAudioLength)
                    }
                }
            }

            val filePath = recordingFile?.absolutePath ?: ""

            filePath
        } catch (e: Exception) {
            cleanup()
            throw RuntimeException("Stop recording failed：${e.message}")
        }
    }

    /**
     * 写入WAV文件头
     */
    private fun writeWavHeader(outputStream: FileOutputStream, audioLength: Long) {
        val totalDataLen = audioLength + 36
        val longSampleRate = sampleRate.toLong()
        val byteRate = longSampleRate * channels * bitsPerSample / 8

        val header = ByteArray(44)

        // RIFF头
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // 文件大小
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        // WAVE标识
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt块
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // fmt块大小
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // 音频格式 (PCM)
        header[20] = 1
        header[21] = 0

        // 声道数
        header[22] = channels.toByte()
        header[23] = 0

        // 采样率
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()

        // 字节率
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        // 块对齐
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0

        // 位深度
        header[34] = bitsPerSample.toByte()
        header[35] = 0

        // data块
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // 音频数据大小
        header[40] = (audioLength and 0xff).toByte()
        header[41] = ((audioLength shr 8) and 0xff).toByte()
        header[42] = ((audioLength shr 16) and 0xff).toByte()
        header[43] = ((audioLength shr 24) and 0xff).toByte()

        outputStream.write(header, 0, 44)
    }

    /**
     * 更新WAV文件头的长度信息
     */
    private fun updateWavHeader(file: File, audioLength: Long) {
        try {
            val randomAccessFile = RandomAccessFile(file, "rw")

            // 更新文件大小（位置4-7）
            val totalDataLen = audioLength + 36
            randomAccessFile.seek(4)
            randomAccessFile.writeByte((totalDataLen and 0xff).toInt())
            randomAccessFile.writeByte(((totalDataLen shr 8) and 0xff).toInt())
            randomAccessFile.writeByte(((totalDataLen shr 16) and 0xff).toInt())
            randomAccessFile.writeByte(((totalDataLen shr 24) and 0xff).toInt())

            // 更新音频数据大小（位置40-43）
            randomAccessFile.seek(40)
            randomAccessFile.writeByte((audioLength and 0xff).toInt())
            randomAccessFile.writeByte(((audioLength shr 8) and 0xff).toInt())
            randomAccessFile.writeByte(((audioLength shr 16) and 0xff).toInt())
            randomAccessFile.writeByte(((audioLength shr 24) and 0xff).toInt())

            randomAccessFile.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update WAV file header", e)
        }
    }

    /**
     * 检查是否正在录制
     */
    fun isRecording(): Boolean = isRecording

    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            isRecording = false
            recordingJob?.cancel()
            recordingJob = null

            audioRecord?.let { record ->
                try {
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        record.stop()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Stop recording exceptions during cleaning", e)
                }

                try {
                    record.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Release AudioRecord exception during cleaning", e)
                }
            }
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean up resources", e)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        cleanup()
    }

    /**
     * 检查设备是否支持录音功能
     */
    fun isRecordingSupported(): Boolean {
        return try {
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            minBufferSize != AudioRecord.ERROR_BAD_VALUE && minBufferSize != AudioRecord.ERROR
        } catch (e: Exception) {
            false
        }
    }
}