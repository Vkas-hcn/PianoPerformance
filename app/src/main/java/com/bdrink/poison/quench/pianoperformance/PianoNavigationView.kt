package com.bdrink.poison.quench.pianoperformance

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class PianoNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val whiteKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val blackKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000") // 半透明红色蒙版
        style = Paint.Style.FILL
    }

    private val maskStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val keys = mutableListOf<PianoKey>()
    private var maskPosition = 0f
    private var maskWidth = 0f

    // 钢琴键尺寸（导航版本，比较小）
    private val whiteKeyWidth = 8f
    private val whiteKeyHeight = 120f // 修改：高度改为原来的两倍 (60f -> 120f)
    private val blackKeyWidth = 5f
    private val blackKeyHeight = 72f // 修改：黑键高度也相应调整 (36f -> 72f)

    private var onPositionChangeListener: ((Float) -> Unit)? = null

    init {
        setupKeys()
    }

    private fun setupKeys() {
        keys.clear()

        // 88键钢琴：A0到C8
        val whiteKeyPattern = listOf(0, 2, 4, 5, 7, 9, 11) // C, D, E, F, G, A, B
        val blackKeyPattern = listOf(1, 3, 6, 8, 10) // C#, D#, F#, G#, A#

        var whiteKeyIndex = 0

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

    fun setOnPositionChangeListener(listener: (Float) -> Unit) {
        onPositionChangeListener = listener
    }

    fun updateMask(scrollX: Int, totalWidth: Int, viewWidth: Int) {
        val totalNavWidth = getTotalWidth()
        if (totalNavWidth > 0 && totalWidth > 0) {
            // 计算蒙版位置和宽度
            maskPosition = (scrollX.toFloat() * totalNavWidth / totalWidth)
            maskWidth = (viewWidth.toFloat() * totalNavWidth / totalWidth)

            // 确保蒙版不超出边界
            maskPosition = max(0f, min(maskPosition, totalNavWidth - maskWidth))
            maskWidth = min(maskWidth, totalNavWidth - maskPosition)

            invalidate()
        }
    }

    private fun getTotalWidth(): Float {
        val whiteKeyCount = keys.count { !it.isBlack }
        return whiteKeyCount * whiteKeyWidth
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 修改：宽度使用父视图提供的宽度（充满屏幕），高度使用新的高度值
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = whiteKeyHeight.toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 修改：根据实际宽度重新计算钢琴键的绘制比例
        val actualWidth = width.toFloat()
        val originalTotalWidth = getTotalWidth()
        val scaleX = if (originalTotalWidth > 0) actualWidth / originalTotalWidth else 1f

        // 先绘制白键
        keys.filter { !it.isBlack }.forEach { key ->
            val scaledRect = RectF(
                key.rect.left * scaleX,
                key.rect.top,
                key.rect.right * scaleX,
                key.rect.bottom
            )
            canvas.drawRect(scaledRect, whiteKeyPaint)
            canvas.drawRect(scaledRect, strokePaint)
        }

        // 再绘制黑键
        keys.filter { it.isBlack }.forEach { key ->
            val scaledRect = RectF(
                key.rect.left * scaleX,
                key.rect.top,
                key.rect.right * scaleX,
                key.rect.bottom
            )
            canvas.drawRect(scaledRect, blackKeyPaint)
        }

        // 绘制蒙版
        if (maskWidth > 0) {
            val maskRect = RectF(
                maskPosition * scaleX,
                0f,
                (maskPosition + maskWidth) * scaleX,
                whiteKeyHeight
            )
            canvas.drawRect(maskRect, maskPaint)
            canvas.drawRect(maskRect, maskStrokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val touchX = event.x
                val actualWidth = width.toFloat()

                if (actualWidth > 0) {
                    // 计算触摸位置对应的比例
                    val ratio = touchX / actualWidth
                    onPositionChangeListener?.invoke(ratio)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private data class PianoKey(
        val keyIndex: Int,
        val rect: RectF,
        val isBlack: Boolean
    )
}