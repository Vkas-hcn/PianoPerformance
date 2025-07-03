package com.bdrink.poison.quench.pianoperformance

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

class PianoAudioManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var d5SoundId: Int = 0

    // D5在88键钢琴中的索引位置 (A0=0, A#0=1, ..., D5=53)
    private val d5KeyIndex = 53

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        loadBaseSound()
    }

    private fun loadBaseSound() {
        try {
            // 加载 d5.ogg 文件作为基准音
            val resourceId = context.resources.getIdentifier("d5", "raw", context.packageName)
            if (resourceId != 0) {
                d5SoundId = soundPool?.load(context, resourceId, 1) ?: 0
            } else {

                throw RuntimeException("The D5 audio file could not be found")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Failed to load the benchmark file: ${e.message}")
        }
    }

    suspend fun playNote(keyIndex: Int) = withContext(Dispatchers.IO) {
        try {
            if (d5SoundId != 0) {
                // 计算相对于D5的音调比率
                val pitch = calculatePitchForKey(keyIndex)

                // 播放音符，通过调整rate参数改变音调
                soundPool?.play(
                    d5SoundId,      // 声音ID
                    1.0f,           // 左声道音量
                    1.0f,           // 右声道音量
                    1,              // 优先级
                    0,              // 循环次数（0=不循环）
                    pitch           // 播放速度/音调
                )
            } else {
                throw RuntimeException("The benchmark file is not loaded")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 计算指定键相对于D5的音调比率
     * @param keyIndex 目标键的索引 (0-87)
     * @return 音调比率
     */
    private fun calculatePitchForKey(keyIndex: Int): Float {
        // 计算目标键与D5之间的半音数差异
        val semitonesDifference = keyIndex - d5KeyIndex

        // 每个半音的频率比率是 2^(1/12)
        val semitoneRatio = 2.0.pow(1.0 / 12.0)

        // 计算最终的音调比率
        return semitoneRatio.pow(semitonesDifference.toDouble()).toFloat()
    }


    private fun getNoteName(keyIndex: Int): String {
        val noteNames = arrayOf("A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#")

        // A0是第0个键，计算音符和八度
        val adjustedIndex = keyIndex + 9 // A0开始，所以要调整
        val octave = adjustedIndex / 12
        val noteIndex = adjustedIndex % 12

        return "${noteNames[noteIndex]}$octave"
    }


    suspend fun playNoteByName(noteName: String) = withContext(Dispatchers.IO) {
        val keyIndex = getKeyIndexByNoteName(noteName)
        if (keyIndex >= 0) {
            playNote(keyIndex)
        }
    }

    /**
     * 根据音符名称获取键索引
     */
    private fun getKeyIndexByNoteName(noteName: String): Int {
        val noteNames = arrayOf("A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#")

        try {
            val notePattern = Regex("([A-G]#?)([0-8])")
            val matchResult = notePattern.find(noteName.uppercase())

            if (matchResult != null) {
                val note = matchResult.groupValues[1]
                val octave = matchResult.groupValues[2].toInt()

                val noteIndex = noteNames.indexOf(note)
                if (noteIndex >= 0) {
                    val keyIndex = octave * 12 + noteIndex - 9 // 调整为A0开始
                    return if (keyIndex in 0..87) keyIndex else -1
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return -1
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        d5SoundId = 0
    }
}