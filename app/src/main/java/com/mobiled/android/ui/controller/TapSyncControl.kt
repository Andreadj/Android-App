package com.mobiled.android.ui.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Standalone tap UI: reports tempo and never sends device commands itself. */
class TapSyncControl(
    context: Context,
    matrixEffectId: Int,
    onTempo: (TapTempo.Reading) -> Unit
) : LinearLayout(context) {
    private val tempo = TapTempo(matrixEffectId)
    private val led = TapLed(context)
    private val ledOff = Runnable { led.lit = false }

    init {
        orientation = VERTICAL
        val button = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(32, 32, 32))
                setStroke(dp(1), Color.rgb(85, 85, 85))
                cornerRadius = dp(5).toFloat()
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Tap Sync"
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Button::class.java.name
                }
            }
            addView(led, LayoutParams(dp(16), dp(16)))
            addView(TextView(context).apply {
                text = "Tap Sync"
                textSize = 11f
                setTextColor(Color.WHITE)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(4)
            })
        }
        val bpmLabel = TextView(context).apply {
            text = "BPM —"
            textSize = 10f
            setTextColor(Color.rgb(170, 170, 170))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addView(button, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        addView(bpmLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        button.setOnClickListener {
            removeCallbacks(ledOff)
            led.lit = true
            postDelayed(ledOff, 120L)
            tempo.tap(SystemClock.elapsedRealtime())?.let { reading ->
                bpmLabel.text = "BPM ${reading.bpm.roundToInt()}"
                onTempo(reading)
            }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ledOff)
        led.lit = false
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private class TapLed(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var lit: Boolean = false
            set(value) { field = value; invalidate() }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            paint.color = if (lit) Color.rgb(255, 174, 0) else Color.rgb(85, 85, 85)
            paint.alpha = if (lit) 255 else 166
            if (lit) paint.setShadowLayer(4f * density, 0f, 0f, Color.rgb(255, 174, 0))
            else paint.clearShadowLayer()
            canvas.drawCircle(width / 2f, height / 2f, 4f * density, paint)
        }
    }
}
