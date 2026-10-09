package com.mobiled.android.ui.controller

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable

/**
 * MobileD Music Home icon: three overlapping coloured musical notes.
 * Kept as a Drawable so Home, Controller and Music use the same artwork
 * without depending on a platform/emoji font.
 */
class MusicIconDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY() - 1f
        val step = b.width().coerceAtMost(b.height()).coerceAtLeast(1) * 0.16f
        val size = b.width().coerceAtMost(b.height()).coerceAtLeast(1) * 0.62f
        paint.textSize = size

        paint.color = Color.rgb(230, 45, 55)
        canvas.drawText("♪", cx - step, cy, paint)
        paint.color = Color.rgb(55, 205, 90)
        canvas.drawText("♪", cx, cy, paint)
        paint.color = Color.rgb(55, 125, 235)
        canvas.drawText("♪", cx + step, cy, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
}
