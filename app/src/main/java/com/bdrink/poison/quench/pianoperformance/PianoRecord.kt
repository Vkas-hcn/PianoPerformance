package com.bdrink.poison.quench.pianoperformance

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 钢琴录制文件数据模型
 */
data class PianoRecord(
    val file: File,
    val displayName: String,
    val recordTime: String,
    val duration: Long = 0L, // 录制时长（毫秒）
    val fileSize: Long = 0L  // 文件大小（字节）
) {

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private val fileNameDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

        /**
         * 从文件创建PianoRecord对象
         */
        fun fromFile(file: File): PianoRecord {
            try {
                Log.d("PianoRecord", "处理文件: ${file.name}, 路径: ${file.absolutePath}")

                // 解析文件名中的时间戳
                val fileName = file.nameWithoutExtension
                Log.d("PianoRecord", "文件名（无扩展名）: $fileName")

                val recordTime = parseRecordTimeFromFileName(fileName)
                Log.d("PianoRecord", "解析的录制时间: $recordTime")

                // 生成显示名称（去掉前缀和时间戳）
                val displayName = generateDisplayName(fileName)
                Log.d("PianoRecord", "生成的显示名称: $displayName")

                val duration = estimateDuration(file)
                Log.d("PianoRecord", "估算时长: ${duration}ms")

                return PianoRecord(
                    file = file,
                    displayName = displayName,
                    recordTime = recordTime,
                    duration = duration,
                    fileSize = file.length()
                )
            } catch (e: Exception) {
                Log.e("PianoRecord", "创建PianoRecord失败", e)
                // 返回一个基本的记录，确保不会因为解析错误而丢失文件
                return PianoRecord(
                    file = file,
                    displayName = file.nameWithoutExtension.ifEmpty { "钢琴演奏" },
                    recordTime = dateFormat.format(Date(file.lastModified())),
                    duration = estimateDuration(file),
                    fileSize = file.length()
                )
            }
        }

        /**
         * 从文件名解析录制时间
         */
        private fun parseRecordTimeFromFileName(fileName: String): String {
            return try {
                // 文件名格式: piano_record_20241203_142530
                val timestampPattern = Regex("(\\d{8})_(\\d{6})")
                val matchResult = timestampPattern.find(fileName)

                if (matchResult != null) {
                    val dateStr = matchResult.groupValues[1]
                    val timeStr = matchResult.groupValues[2]
                    val fullTimestamp = "${dateStr}_$timeStr"

                    val date = fileNameDateFormat.parse(fullTimestamp)
                    dateFormat.format(date ?: Date())
                } else {
                    // 如果无法解析，使用文件修改时间
                    dateFormat.format(Date())
                }
            } catch (e: Exception) {
                dateFormat.format(Date())
            }
        }

        /**
         * 生成友好的显示名称
         */
        private fun generateDisplayName(fileName: String): String {
            return try {
                // 移除 "piano_record_" 前缀
                if (fileName.startsWith("piano_record_")) {
                    val withoutPrefix = fileName.substring("piano_record_".length)
                    // 尝试解析时间并生成友好名称
                    val timestampPattern = Regex("(\\d{8})_(\\d{6})")
                    val matchResult = timestampPattern.find(withoutPrefix)

                    if (matchResult != null) {
                        val dateStr = matchResult.groupValues[1]
                        val timeStr = matchResult.groupValues[2]
                        "钢琴演奏 ${dateStr.substring(0, 4)}-${dateStr.substring(4, 6)}-${dateStr.substring(6, 8)} ${timeStr.substring(0, 2)}:${timeStr.substring(2, 4)}"
                    } else {
                        "钢琴演奏"
                    }
                } else {
                    fileName
                }
            } catch (e: Exception) {
                "钢琴演奏"
            }
        }

        /**
         * 估算录制时长（基于WAV文件大小）
         */
        private fun estimateDuration(file: File): Long {
            return try {
                // WAV文件头大小为44字节，需要从总大小中减去
                val audioDataSize = if (file.extension.lowercase() == "wav") {
                    file.length() - 44 // 减去WAV文件头
                } else {
                    file.length() // PCM文件没有文件头
                }

                // PCM 16位单声道，44100Hz
                // 每秒字节数 = 44100 * 2 = 88200
                val bytesPerSecond = 44100 * 2
                val durationSeconds = audioDataSize / bytesPerSecond
                durationSeconds * 1000 // 转换为毫秒
            } catch (e: Exception) {
                0L
            }
        }
    }

    /**
     * 获取格式化的文件大小
     */
    fun getFormattedFileSize(): String {
        return when {
            fileSize < 1024 -> "${fileSize}B"
            fileSize < 1024 * 1024 -> "${String.format("%.1f", fileSize / 1024.0)}KB"
            else -> "${String.format("%.1f", fileSize / (1024.0 * 1024.0))}MB"
        }
    }

    /**
     * 获取格式化的时长
     */
    fun getFormattedDuration(): String {
        val seconds = duration / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
}