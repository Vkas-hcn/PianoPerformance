package com.bdrink.poison.quench.pianoperformance

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import kotlin.math.max
import kotlin.math.min

class PianoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private var keyClickListener: ((Int) -> Unit)? = null
    private var currentScale = 1.0f
    private val minScale = 0.5f
    private val maxScale = 2.0f

    private val pianoCanvas = PianoCanvas(context)

    init {
        addView(pianoCanvas)
        isHorizontalScrollBarEnabled = false
    }

    fun setOnKeyClickListener(listener: (Int) -> Unit) {
        keyClickListener = listener
        pianoCanvas.setOnKeyClickListener(listener)
    }

    fun zoomIn() {
        if (currentScale < maxScale) {
            currentScale = min(maxScale, currentScale + 0.2f)
            updateScale()
        }
    }

    fun zoomOut() {
        if (currentScale > minScale) {
            currentScale = max(minScale, currentScale - 0.2f)
            updateScale()
        }
    }

    private fun updateScale() {
        pianoCanvas.scaleX = currentScale
        pianoCanvas.scaleY = currentScale
    }

    private inner class PianoCanvas(context: Context) : View(context) {

        private val whiteKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        private val blackKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        private val pressedWhiteKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.FILL
        }

        private val pressedBlackKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            style = Paint.Style.FILL
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        private val whiteKeyWidth = 80*1.3f
        private val whiteKeyHeight = 300*1.3f
        private val blackKeyWidth = 50*1.3f
        private val blackKeyHeight = 180*1.3f

        private val keys = mutableListOf<PianoKey>()
        private val pressedKeys = mutableSetOf<Int>()

        private var keyClickListener: ((Int) -> Unit)? = null

        init {
            setupKeys()
        }

        private fun setupKeys() {
            keys.clear()

            // 88键钢琴：A0到C8
            val whiteKeyPattern = listOf(0, 2, 4, 5, 7, 9, 11) // C, D, E, F, G, A, B
            val blackKeyPattern = listOf(1, 3, 6, 8, 10) // C#, D#, F#, G#, A#

            var whiteKeyIndex = 0
            var currentX = 0f

            // 从A0开始 (keyIndex = 0)
            for (keyIndex in 0 until 88) {
                val noteInOctave = (keyIndex + 9) % 12 // A0开始，所以+9
                val isBlackKey = noteInOctave in blackKeyPattern

                if (isBlackKey) {
                    // 黑键
                    val whiteKeyX = (whiteKeyIndex - 1) * whiteKeyWidth
                    val blackKeyX = whiteKeyX + whiteKeyWidth - blackKeyWidth / 2
                    keys.add(
                        PianoKey(
                            keyIndex,
                            RectF(blackKeyX, 0f, blackKeyX + blackKeyWidth, blackKeyHeight),
                            true
                        )
                    )
                } else {
                    // 白键
                    val whiteKeyX = whiteKeyIndex * whiteKeyWidth
                    keys.add(
                        PianoKey(
                            keyIndex,
                            RectF(whiteKeyX, 0f, whiteKeyX + whiteKeyWidth, whiteKeyHeight),
                            false
                        )
                    )
                    whiteKeyIndex++
                }
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val whiteKeyCount = keys.count { !it.isBlack }
            val totalWidth = (whiteKeyCount * whiteKeyWidth).toInt()
            setMeasuredDimension(totalWidth, whiteKeyHeight.toInt())
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // 先绘制白键
            keys.filter { !it.isBlack }.forEach { key ->
                val paint = if (key.keyIndex in pressedKeys) pressedWhiteKeyPaint else whiteKeyPaint
                canvas.drawRect(key.rect, paint)
                canvas.drawRect(key.rect, strokePaint)
            }

            // 再绘制黑键
            keys.filter { it.isBlack }.forEach { key ->
                val paint = if (key.keyIndex in pressedKeys) pressedBlackKeyPaint else blackKeyPaint
                canvas.drawRect(key.rect, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val touchedKey = findKeyAtPosition(event.x, event.y)
                    touchedKey?.let { key ->
                        if (key.keyIndex !in pressedKeys) {
                            pressedKeys.add(key.keyIndex)
                            keyClickListener?.invoke(key.keyIndex)
                            invalidate()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    pressedKeys.clear()
                    invalidate()
                    return true
                }
            }
            return false
        }

        private fun findKeyAtPosition(x: Float, y: Float): PianoKey? {
            // 先检查黑键（优先级更高）
            keys.filter { it.isBlack }.forEach { key ->
                if (key.rect.contains(x, y)) {
                    return key
                }
            }

            // 再检查白键
            keys.filter { !it.isBlack }.forEach { key ->
                if (key.rect.contains(x, y)) {
                    return key
                }
            }

            return null
        }

        fun setOnKeyClickListener(listener: (Int) -> Unit) {
            keyClickListener = listener
        }
    }

    private data class PianoKey(
        val keyIndex: Int,
        val rect: RectF,
        val isBlack: Boolean
    )
}