package com.example.fitquest.ui.widgets

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.annotation.IntRange
import kotlin.math.max
import kotlin.math.min

class SpriteSheetDrawable(
    private val sheet: Bitmap,
    private val rows: Int,
    private val cols: Int,
    fps: Int = 18,
    private val loop: Boolean = true,
    private val scaleMode: ScaleMode = ScaleMode.CENTER_CROP
) : Drawable(), Runnable {

    enum class ScaleMode { FIT_CENTER, CENTER_CROP, FILL_XY }

    private val frameW = sheet.width / cols
    private val frameH = sheet.height / rows
    private val totalFrames = rows * cols
    private var frameIndex = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dest = Rect()
    private val frameDurationMs = if (fps <= 0) 83L else 1000L / fps
    private var running = false

    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = frameIndex / cols
        val c = frameIndex % cols
        src.set(c * frameW, r * frameH, c * frameW + frameW, r * frameH + frameH)

        when (scaleMode) {
            ScaleMode.FILL_XY -> dest.set(b)
            ScaleMode.FIT_CENTER -> {
                val scale = min(b.width() / frameW.toFloat(), b.height() / frameH.toFloat())
                val w = (frameW * scale).toInt()
                val h = (frameH * scale).toInt()
                val cx = b.centerX(); val cy = b.centerY()
                dest.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            }
            ScaleMode.CENTER_CROP -> {
                val scale = max(b.width() / frameW.toFloat(), b.height() / frameH.toFloat())
                val w = (frameW * scale).toInt()
                val h = (frameH * scale).toInt()
                val cx = b.centerX(); val cy = b.centerY()
                dest.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            }
        }

        canvas.drawBitmap(sheet, src, dest, paint)
    }

    fun resetToStart() {
        frameIndex = 0
        invalidateSelf()
    }

    override fun run() {
        if (!running) return
        frameIndex++
        if (frameIndex >= totalFrames) {
            if (loop) {
                frameIndex = 0
            } else {
                frameIndex = totalFrames - 1
                running = false
                invalidateSelf()
                return
            }
        }
        invalidateSelf()
        scheduleSelf(this, SystemClock.uptimeMillis() + frameDurationMs)
    }


    fun start() {
        if (running) return
        running = true
        invalidateSelf()
        scheduleSelf(this, SystemClock.uptimeMillis() + frameDurationMs)
    }

    fun stop() {
        running = false
        unscheduleSelf(this)
    }

    fun isRunning(): Boolean = running

    override fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int) {
        paint.alpha = alpha; invalidateSelf()
    }
    override fun getAlpha(): Int = paint.alpha
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter; invalidateSelf()
    }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = frameW
    override fun getIntrinsicHeight(): Int = frameH
}
