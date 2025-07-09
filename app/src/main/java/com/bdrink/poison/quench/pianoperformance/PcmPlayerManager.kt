package com.bdrink.poison.quench.pianoperformance

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream


class PcmPlayerManager {

    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private var isPlaying = false
    private var isPaused = false
    private var currentPosition = 0L

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // 播放状态监听器
    private var onPlayStateChangeListener: ((Boolean, Long, Long) -> Unit)? = null
    private var onPlayCompleteListener: (() -> Unit)? = null

    companion object {
        private const val TAG = "PcmPlayerManager"
        private const val WAV_HEADER_SIZE = 44 // WAV文件头大小
    }


    fun setOnPlayStateChangeListener(listener: (Boolean, Long, Long) -> Unit) {
        onPlayStateChangeListener = listener
    }

    fun setOnPlayCompleteListener(listener: () -> Unit) {
        onPlayCompleteListener = listener
    }

    suspend fun play(file: File) = withContext(Dispatchers.IO) {
        try {
            if (isPlaying) {
                stop()
            }

            if (!file.exists() || file.length() <= WAV_HEADER_SIZE) {
                throw RuntimeException("The file does not exist or is incorrect in format")
            }

            // 验证WAV文件格式
            if (!isValidWavFile(file)) {
                throw RuntimeException("Not a valid WAV file")
            }

            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (bufferSize == AudioTrack.ERROR_BAD_VALUE || bufferSize == AudioTrack.ERROR) {
                throw RuntimeException("Unsupported audio parameters")
            }

            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2,
                AudioTrack.MODE_STREAM
            )

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                throw RuntimeException("AudioTrack initialization failed")
            }

            audioTrack?.play()
            isPlaying = true
            isPaused = false
            currentPosition = 0L

            val totalDuration = estimateWavFileDuration(file)

            // 在主线程通知状态变化
            withContext(Dispatchers.Main) {
                onPlayStateChangeListener?.invoke(true, currentPosition, totalDuration)
            }

            // 启动播放协程
            playJob = CoroutineScope(Dispatchers.IO).launch {
                playWavFile(file, totalDuration)
            }

            Log.d(TAG, "Start playing WAV files: ${file.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Play failed", e)
            cleanup()
            throw RuntimeException("Play failed: ${e.message}")
        }
    }

    /**
     * 验证是否为有效的WAV文件
     */
    private fun isValidWavFile(file: File): Boolean {
        return try {
            val inputStream = FileInputStream(file)
            val header = ByteArray(12)
            val bytesRead = inputStream.read(header)
            inputStream.close()

            if (bytesRead >= 12) {
                // 检查RIFF标识
                val riffMagic = String(header, 0, 4)
                val waveMagic = String(header, 8, 4)
                riffMagic == "RIFF" && waveMagic == "WAVE"
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verification of WAV file failed", e)
            false
        }
    }

    fun pause() {
        try {
            if (isPlaying && !isPaused) {
                audioTrack?.pause()
                isPaused = true

                onPlayStateChangeListener?.invoke(false, currentPosition, 0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pause playback failed", e)
        }
    }


    fun resume() {
        try {
            if (isPlaying && isPaused) {
                audioTrack?.play()
                isPaused = false
                Log.d(TAG, "Playback has been restored")

                onPlayStateChangeListener?.invoke(true, currentPosition, 0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Resuming playback failed", e)
        }
    }


    fun stop() {
        try {
            isPlaying = false
            isPaused = false
            playJob?.cancel()
            playJob = null

            audioTrack?.let { track ->
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Exception when stopping playback", e)
                }
            }

            cleanup()
            currentPosition = 0L

            onPlayStateChangeListener?.invoke(false, currentPosition, 0L)

        } catch (e: Exception) {
            Log.e(TAG, "Stop playback failed", e)
        }
    }


    fun isPlaying(): Boolean = isPlaying && !isPaused


    fun getCurrentPosition(): Long = currentPosition


    private suspend fun playWavFile(file: File, totalDuration: Long) = withContext(Dispatchers.IO) {
        var inputStream: FileInputStream? = null
        try {
            inputStream = FileInputStream(file)

            // 跳过WAV文件头
            inputStream.skip(WAV_HEADER_SIZE.toLong())

            val buffer = ByteArray(4096)
            var totalBytesPlayed = 0L

            while (isPlaying) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) {
                    break // 文件读取完毕
                }

                if (!isPaused && bytesRead > 0) {
                    val bytesWritten = audioTrack?.write(buffer, 0, bytesRead) ?: 0
                    if (bytesWritten > 0) {
                        totalBytesPlayed += bytesWritten

                        // 计算播放进度
                        val bytesPerSecond = sampleRate * 2 // 16位单声道
                        currentPosition = (totalBytesPlayed * 1000) / bytesPerSecond

                        // 定期更新进度
                        if (totalBytesPlayed % (bytesPerSecond / 4) == 0L) { // 每250ms更新一次
                            withContext(Dispatchers.Main) {
                                onPlayStateChangeListener?.invoke(true, currentPosition, totalDuration)
                            }
                        }
                    }
                } else {
                    // 暂停状态，等待
                    delay(50)
                }
            }

            // 播放完成
            if (isPlaying) {
                withContext(Dispatchers.Main) {
                    onPlayCompleteListener?.invoke()
                }
                stop()
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                stop()
            }
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Exception when closing file stream", e)
            }
        }
    }


    private fun estimateWavFileDuration(file: File): Long {
        return try {
            // WAV文件总大小减去文件头大小
            val audioDataSize = file.length() - WAV_HEADER_SIZE

            // PCM 16位单声道，44100Hz
            val bytesPerSecond = sampleRate * 2
            (audioDataSize * 1000) / bytesPerSecond
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.w(TAG, "Exception when cleaning AudioTrack", e)
        }
    }

    /**
     * 释放所有资源
     */
    fun release() {
        stop()
        cleanup()
        onPlayStateChangeListener = null
        onPlayCompleteListener = null
    }
}