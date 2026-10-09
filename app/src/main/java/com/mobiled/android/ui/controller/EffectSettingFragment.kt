package com.mobiled.android.ui.controller

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import com.mobiled.android.LogSystem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import codes.side.andcolorpicker.converter.toColorInt
import codes.side.andcolorpicker.hsl.HSLColorPickerSeekBar
import codes.side.andcolorpicker.model.IntegerHSLColor
import codes.side.andcolorpicker.view.picker.ColorSeekBar
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnChangeListener
import com.mobiled.android.adapters.GeneralUtil
import com.mobiled.android.databinding.FragmentEffectSettingBinding
import com.mobiled.android.model.Effect
import kotlin.math.roundToInt
import om.android.mobiled.comman.hide
import om.android.mobiled.comman.show


class EffectSettingFragment : Fragment() {

    private val TAG = EffectSettingFragment::class.java.simpleName

    lateinit var viewBinding: FragmentEffectSettingBinding

    var effect = Effect()

    var hsvColor = FloatArray(3).also {
        it[0] = 360F
    }

    private val tapHandler = Handler(Looper.getMainLooper())
    private val tapTimes = ArrayDeque<Long>()
    private var tapBeatRunnable: Runnable? = null
    private var tapLedOffRunnable: Runnable? = null
    private var tapLedView: View? = null
    private var tapBpmView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogSystem.e(TAG, "onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        viewBinding = FragmentEffectSettingBinding.inflate(layoutInflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogSystem.e(TAG, "onViewCreated")

        try {
            effect = requireArguments().getSerializable("Effect") as Effect
        } catch (e: java.lang.Exception) {

        }


        preLoadEffectSetting()


        bindEffectListeners()

        bindButtons()
        // Opening an effect editor must not transmit a command. The PC App sends
        // only after an effective user change; this also prevents Strobe Speed
        // from replaying a stale/default Brightness value on entry.
    }

    private fun bindButtons() {


        //COLOR WORK
        viewBinding.viewColorPart.ivAdd.setOnClickListener {
            var color = viewBinding.viewColorPart.viewSlider.pickedColor
            color.floatH = color.floatH.inc()
            LogSystem.e(TAG, "Color Part Slider : $color")
            if (color.floatH > 360f) {
                return@setOnClickListener
            }
            viewBinding.viewColorPart.viewSlider.pickedColor = color
            ColorPartSlider.onColorPicking(viewBinding.viewColorPart.viewSlider, color, viewBinding.viewColorPart.viewSlider.progress, true)

        }
        viewBinding.viewColorPart.ivMinus.setOnClickListener {
            var color = viewBinding.viewColorPart.viewSlider.pickedColor
            color.floatH = color.floatH.dec()
            LogSystem.e(TAG, "Color Part : $color")
            if (color.floatH < 0f) {
                return@setOnClickListener
            }
            viewBinding.viewColorPart.viewSlider.pickedColor = color
            ColorPartSlider.onColorPicking(viewBinding.viewColorPart.viewSlider, color, viewBinding.viewColorPart.viewSlider.progress, true)
        }

        //BRIGHTNESS WORK
        viewBinding.viewColorBrightnessPart.ivAdd.setOnClickListener {
            var value = viewBinding.viewColorBrightnessPart.viewSlider.value.inc()
            if (value > viewBinding.viewColorBrightnessPart.viewSlider.valueTo) {
                return@setOnClickListener
            }
            LogSystem.e(TAG, "ivAdd Color Brightness Slider : $value")
            viewBinding.viewColorBrightnessPart.viewSlider.value = value
            ColorBrightnessPartChangeListener.onValueChange(viewBinding.viewColorBrightnessPart.viewSlider, value, true)
        }
        viewBinding.viewColorBrightnessPart.ivMinus.setOnClickListener {
            var value = viewBinding.viewColorBrightnessPart.viewSlider.value.dec()
            if (value < viewBinding.viewColorBrightnessPart.viewSlider.valueFrom) {
                return@setOnClickListener
            }
            LogSystem.e(TAG, "ivMinus Color Brightness Slider : $value")
            viewBinding.viewColorBrightnessPart.viewSlider.value = value
            ColorBrightnessPartChangeListener.onValueChange(viewBinding.viewColorBrightnessPart.viewSlider, value, true)
        }


        //ALPHA
        viewBinding.viewWhiteBrightnessPart.ivAdd.setOnClickListener {
            var final = viewBinding.viewWhiteBrightnessPart.viewSlider.value.inc()
            LogSystem.e(TAG, "White Brightness Slider : $final")
            if (final > viewBinding.viewWhiteBrightnessPart.viewSlider.valueTo) {
                return@setOnClickListener
            }
            viewBinding.viewWhiteBrightnessPart.viewSlider.value = final
            WhiteBrightnessSliderChangeListener.onValueChange(viewBinding.viewWhiteBrightnessPart.viewSlider, final, true)
        }
        viewBinding.viewWhiteBrightnessPart.ivMinus.setOnClickListener {
            var final = viewBinding.viewWhiteBrightnessPart.viewSlider.value.dec()
            LogSystem.e(TAG, "White Brightness Slider : $final")
            if (final < viewBinding.viewWhiteBrightnessPart.viewSlider.valueFrom) {
                return@setOnClickListener
            }
            viewBinding.viewWhiteBrightnessPart.viewSlider.value = final
            WhiteBrightnessSliderChangeListener.onValueChange(viewBinding.viewWhiteBrightnessPart.viewSlider, final, true)
        }


        //HUE
        viewBinding.viewHuePart.ivAdd.setOnClickListener {
            var color = viewBinding.viewHuePart.viewSlider.pickedColor
            color.floatH = color.floatH.inc()
            LogSystem.e(TAG, "Hue Slider : $color")
            if (color.floatH > 360f) {
                return@setOnClickListener
            }
            viewBinding.viewHuePart.viewSlider.pickedColor = color
            HuePartSliderColorChangeListener.onColorPicking(viewBinding.viewHuePart.viewSlider, color, viewBinding.viewHuePart.viewSlider.progress, true)
        }
        viewBinding.viewHuePart.ivMinus.setOnClickListener {
            var color = viewBinding.viewHuePart.viewSlider.pickedColor
            color.floatH = color.floatH.dec()
            LogSystem.e(TAG, "Hue Slider : $color")
            if (color.floatH < 0f) {
                return@setOnClickListener
            }
            viewBinding.viewHuePart.viewSlider.pickedColor = color
            HuePartSliderColorChangeListener.onColorPicking(viewBinding.viewHuePart.viewSlider, color, viewBinding.viewHuePart.viewSlider.progress, true)
        }


        //BRIGHTNESS WORK
        viewBinding.viewBrightnessPart.ivAdd.setOnClickListener {
            var value = viewBinding.viewBrightnessPart.viewSlider.value.inc()
            LogSystem.e(TAG, "Brightness Slider : $value")
            if (value > viewBinding.viewBrightnessPart.viewSlider.valueTo) {
                return@setOnClickListener
            }
            viewBinding.viewBrightnessPart.viewSlider.value = value
            BrightnessPartSliderListener.onValueChange(viewBinding.viewBrightnessPart.viewSlider, value, true)
        }

        viewBinding.viewBrightnessPart.ivMinus.setOnClickListener {
            var value = viewBinding.viewBrightnessPart.viewSlider.value.dec()
            LogSystem.e(TAG, "Brightness Slider : $value")
            if (value < viewBinding.viewBrightnessPart.viewSlider.valueFrom) {
                return@setOnClickListener
            }
            viewBinding.viewBrightnessPart.viewSlider.value = value
            BrightnessPartSliderListener.onValueChange(viewBinding.viewBrightnessPart.viewSlider, value, true)
        }

        //EXTRA PART WORK
        viewBinding.viewExtraPart.ivAdd.setOnClickListener {
            var value = viewBinding.viewExtraPart.viewSlider.value.inc()
            LogSystem.e(TAG, "Extra Slider : $value")
            if (value > viewBinding.viewExtraPart.viewSlider.valueTo) {
                return@setOnClickListener
            }
            viewBinding.viewExtraPart.viewSlider.value = value
            ExtraPartSliderListener?.onValueChange(viewBinding.viewExtraPart.viewSlider, value, true)
        }
        viewBinding.viewExtraPart.ivMinus.setOnClickListener {
            var value = viewBinding.viewExtraPart.viewSlider.value.dec()
            LogSystem.e(TAG, "Extra Slider : $value")
            if (value < viewBinding.viewExtraPart.viewSlider.valueFrom) {
                return@setOnClickListener
            }
            viewBinding.viewExtraPart.viewSlider.value = value
            ExtraPartSliderListener?.onValueChange(viewBinding.viewExtraPart.viewSlider, value, true)
        }
    }

    val ColorBrightnessPartChangeListener = OnChangeListener { slider, value, fromUser ->
        if (fromUser) {
            LogSystem.e(TAG, "ViewColorBrightnessPart value = $value")
            saveEffectParam("colorBrightness", value.toInt())
            bindHsvColor()
        }
    }
    val ColorPartSlider = object :
        ColorSeekBar.OnColorPickListener<ColorSeekBar<IntegerHSLColor>, IntegerHSLColor> {
        override fun onColorChanged(
            picker: ColorSeekBar<IntegerHSLColor>, color: IntegerHSLColor, value: Int
        ) {

        }

        override fun onColorPicked(
            picker: ColorSeekBar<IntegerHSLColor>,
            color: IntegerHSLColor,
            value: Int,
            fromUser: Boolean
        ) {

        }

        override fun onColorPicking(
            picker: ColorSeekBar<IntegerHSLColor>,
            color: IntegerHSLColor,
            value: Int,
            fromUser: Boolean
        ) {

            if (fromUser) {
                Color.colorToHSV(color.toColorInt(), hsvColor)
                saveEffectParam("hue", (hsvColor[0] / (360F / 255F)).roundToInt().coerceIn(0, 255))
                bindHsvColor()
            }
        }

    }
    val WhiteBrightnessSliderChangeListener = OnChangeListener { slider, value, fromUser ->
        if (fromUser) {
            LogSystem.e(TAG, "White Brightness Slider OnChangeListener : $value")

            viewBinding.viewWBPercent.setText("${Math.round(value)}%")
            saveEffectParam("whiteBrightness", value.toInt())
            bindHsvColor()
        }
    }

    val HuePartSliderColorChangeListener = object :
        ColorSeekBar.OnColorPickListener<ColorSeekBar<IntegerHSLColor>, IntegerHSLColor> {
        override fun onColorChanged(
            picker: ColorSeekBar<IntegerHSLColor>, color: IntegerHSLColor, value: Int
        ) {

        }

        override fun onColorPicked(
            picker: ColorSeekBar<IntegerHSLColor>,
            color: IntegerHSLColor,
            value: Int,
            fromUser: Boolean
        ) {

        }

        override fun onColorPicking(
            picker: ColorSeekBar<IntegerHSLColor>,
            color: IntegerHSLColor,
            value: Int,
            fromUser: Boolean
        ) {
            LogSystem.e(TAG, "HuePartSliderColorChangeListener onColorPicking() called with: picker = $picker, color = $color, value = $value, fromUser = $fromUser")
            if (fromUser) {
                hsvColor[0] = color.floatH.coerceIn(0f, 360f)
                val hue = (hsvColor[0] / (360F / 255F)).roundToInt().coerceIn(0, 255)
                saveEffectParam("hue", hue)
                (activity as ActionListener?)?.onCommandChanged(
                    GLights = effect.gLight, hue = hue
                )
                viewBinding.viewHuePreview.setText("(Hue = $hue)")
            }
        }

    }

    var ExtraPartSliderListener: OnChangeListener? = null

    private fun pcDefaultLegacySpeed(effectId: Int): Int = when (effectId) {
        1 -> 80
        4 -> 60
        5 -> 50
        8 -> 90
        12 -> 50
        else -> 0
    }

    // Values mirror effectDefaults(id) in the PC App renderer.js.
    // Hue is stored in the protocol's 0..255 range (PC UI degrees converted to protocol).
    private fun pcDefaultLegacyParam(effectId: Int, key: String): Int = when (key) {
        "brightness" -> when (effectId) {
            1, 2, 3, 6, 7, 9 -> 100
            5 -> 50
            else -> 0
        }
        "colorBrightness" -> when (effectId) {
            4, 8, 12 -> 0
            else -> 100
        }
        "whiteBrightness" -> when (effectId) {
            4, 12 -> 0
            8 -> 100
            else -> 100
        }
        "hue" -> if (effectId == 4) 113 else 0
        else -> 0
    }

    private fun legacySpeedPrefs() = requireContext().getSharedPreferences("mobiled_legacy_effect_settings", 0)

    private fun loadLegacySpeed(effectId: Int): Int {
        val defaults = pcDefaultLegacySpeed(effectId)
        val prefs = legacySpeedPrefs()
        val effectPrefs = requireContext().getSharedPreferences("mobiled_legacy_effect_params_$effectId", 0)
        val value = when {
            prefs.contains("speed_$effectId") -> prefs.getInt("speed_$effectId", defaults)
            effectPrefs.contains("speed") -> effectPrefs.getInt("speed", defaults)
            else -> defaults
        }
        return value.coerceIn(if (effectId == 4 || effectId == 8 || effectId == 12) 1 else 0, 100)
    }

    private fun saveLegacySpeed(value: Int) {
        legacySpeedPrefs().edit().putInt("speed_${effect.gLight}", value.coerceIn(if (effect.gLight == 4 || effect.gLight == 8 || effect.gLight == 12) 1 else 0, 100)).apply()
    }

    private fun effectPrefs() = requireContext().getSharedPreferences("mobiled_legacy_effect_params_${effect.gLight}", 0)

    private fun saveEffectParam(key: String, value: Int) {
        effectPrefs().edit().putInt(key, value).apply()
    }

    private fun savedEffectParam(key: String, fallback: Int): Int =
        effectPrefs().getInt(key, fallback).coerceIn(0, 255)

    val BrightnessPartSliderListener = object : OnChangeListener {
        override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
            if (fromUser) {
                saveEffectParam(if (effect.isRGBW || effect.isColorBrightness) "colorBrightness" else "brightness", value.toInt())
                (activity as ActionListener?)?.onCommandChanged(
                    GLights = effect.gLight, Brightness = value.toInt()
                )
            }
            viewBinding.viewBrightnessPreview.text = "${value.toInt()}%"
        }
    }

    private fun bindEffectListeners() {
        viewBinding.viewEffectName.text = "${effect.name} Effect"

        viewBinding.viewRgbSetting.hide()
        viewBinding.viewHueSetting.hide()
        viewBinding.viewBrightnessSetting.hide()
        viewBinding.viewExtraSettings.hide()
        viewBinding.viewColorBrightnessPart.root.hide()
        viewBinding.viewWhiteBrightnessPart.root.hide()

        viewBinding.viewBrightnessPart.viewSlider.valueFrom = 0F
        viewBinding.viewBrightnessPart.viewSlider.valueTo = 100.0F


        if (effect.isRGBW) {
            viewBinding.viewRgbSetting.show()
            // PC Strobe presents a color preview and Hue / Color Brightness /
            // White Brightness controls, not an RGB numeric readout.
            viewBinding.viewColorPart.root.show()
            viewBinding.viewColorBrightnessPart.root.show()
            viewBinding.viewWhiteBrightnessPart.root.show()
            (viewBinding.viewRgbPreview.parent as? ViewGroup)?.let { row ->
                (row.getChildAt(0) as? TextView)?.text = "Hue"
            }

            viewBinding.viewColorPart.viewSlider.mode =
                HSLColorPickerSeekBar.Mode.MODE_HUE
            viewBinding.viewColorPart.viewSlider.coloringMode =
                HSLColorPickerSeekBar.ColoringMode.OUTPUT_COLOR

            viewBinding.viewColorPart.viewSlider.addListener(ColorPartSlider)
            viewBinding.viewColorBrightnessPart.viewSlider.addOnChangeListener(ColorBrightnessPartChangeListener)
            viewBinding.viewWhiteBrightnessPart.viewSlider.addOnChangeListener(WhiteBrightnessSliderChangeListener)
        }

        if (effect.isHue) {
            viewBinding.viewHueSetting.show()
            viewBinding.viewHuePart.root.show()
            viewBinding.viewHuePart.viewSlider.mode =
                HSLColorPickerSeekBar.Mode.MODE_HUE
            viewBinding.viewHuePart.viewSlider.coloringMode =
                HSLColorPickerSeekBar.ColoringMode.PURE_COLOR
            viewBinding.viewHuePart.viewSlider.addListener(HuePartSliderColorChangeListener)

            if (!effect.isRGBW && (effect.gLight == 4 || effect.gLight == 12)) {
                // Breath and Heartbeat use exactly the same Color Brightness /
                // White Brightness controls as Strobe. The existing RGBW block
                // is reused for the controls and color preview, but its RGB hue
                // picker/readout is hidden because these effects use the Hue control above.
                viewBinding.viewRgbSetting.show()
                viewBinding.viewColorPart.root.hide()
                (viewBinding.viewRgbPreview.parent as? ViewGroup)?.hide()
                viewBinding.viewColorBrightnessPart.root.show()
                viewBinding.viewWhiteBrightnessPart.root.show()
                viewBinding.viewColorBrightnessPart.viewSlider.addOnChangeListener(ColorBrightnessPartChangeListener)
                viewBinding.viewWhiteBrightnessPart.viewSlider.addOnChangeListener(WhiteBrightnessSliderChangeListener)
                moveHueColorSettingsBelowHue()
                bindHsvColor(false)
            } else {
                bindHsvColor(false)
            }
        }

        if (effect.isBrightness) {
            viewBinding.viewBrightnessSetting.show()
        }

        if (effect.isSpeed || effect.isFrequency || effect.isSensitivity) {
            viewBinding.viewExtraSettings.show()
            viewBinding.viewExtraPart.root.show()
            if (effect.isSpeed) {

                viewBinding.viewExtraSettingText.setText("Speed")
                ExtraPartSliderListener = object : OnChangeListener {
                    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
                        if (fromUser) {
                            saveLegacySpeed(value.toInt())
                            saveEffectParam("speed", value.toInt())
                            (activity as ActionListener?)?.onCommandChanged(
                                GLights = effect.gLight, Speed = value.toInt()
                            )
                        }
                        viewBinding.viewExtraSliderPreview.text = "${value.toInt()}%"
                    }
                }
                viewBinding.viewExtraPart.viewSlider.addOnChangeListener(ExtraPartSliderListener!!)
                if (effect.gLight == 4 || effect.gLight == 8 || effect.gLight == 12) {
                    viewBinding.viewExtraPart.viewSlider.valueFrom = 1F
                    viewBinding.viewExtraPart.viewSlider.valueTo = 100F
                    addTapSyncControls()
                }
            } else if (effect.isFrequency) {
                ExtraPartSliderListener = object : OnChangeListener {
                    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
                        if (fromUser) {
                            saveLegacySpeed(value.toInt())
                            saveEffectParam("speed", value.toInt())
                            (activity as ActionListener?)?.onCommandChanged(
                                GLights = effect.gLight, Speed = value.toInt()
                            )
                        }
                        viewBinding.viewExtraSliderPreview.text = "${value.toInt()}%"
                    }
                }
                viewBinding.viewExtraSettingText.setText("Frequency")
                viewBinding.viewExtraPart.viewSlider.addOnChangeListener(ExtraPartSliderListener!!)
            } else if (effect.isSensitivity) {
                ExtraPartSliderListener = object : OnChangeListener {
                    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
                        if (fromUser) {
                            (activity as ActionListener?)?.onCommandChanged(
                                GLights = effect.gLight, Speed = 100 - value.toInt()
                            )
                        }
                        viewBinding.viewExtraSliderPreview.text = "${value.toInt()}%"
                    }
                }
                viewBinding.viewExtraSettingText.setText("Sensitivity")
                viewBinding.viewExtraPart.viewSlider.addOnChangeListener(ExtraPartSliderListener!!)
            }
        }

        viewBinding.viewBrightnessPart.viewSlider.addOnChangeListener(BrightnessPartSliderListener)
    }

    private fun sendInitialEffectCommand() {
        val action = activity as? ActionListener ?: return
        var speed = -1
        var brightness = -1
        var red = -1
        var green = -1
        var blue = -1
        var white = -1
        var hue = -1

        if (effect.isSpeed || effect.isFrequency) {
            speed = viewBinding.viewExtraPart.viewSlider.value.toInt()
        }
        if (effect.isBrightness || effect.isColorBrightness) {
            brightness = if (effect.isColorBrightness) {
                viewBinding.viewColorBrightnessPart.viewSlider.value.toInt()
            } else {
                viewBinding.viewBrightnessPart.viewSlider.value.toInt()
            }
        }
        if (effect.isHue) {
            hue = ((hsvColor[0] + 360F) % 360F / 360F * 255F).roundToInt().coerceIn(0, 255)
        }
        if (effect.isWhite) {
            white = (viewBinding.viewWhiteBrightnessPart.viewSlider.value / 100F * 255F).roundToInt().coerceIn(0, 255)
        }
        if (effect.isRGBW) {
            val cb = (viewBinding.viewColorBrightnessPart.viewSlider.value / 100F).coerceIn(0F, 1F)
            val rgb = Color.HSVToColor(floatArrayOf((hsvColor[0] + 360F) % 360F, 1F, cb))
            red = Color.red(rgb)
            green = Color.green(rgb)
            blue = Color.blue(rgb)
            white = (viewBinding.viewWhiteBrightnessPart.viewSlider.value / 100F * 255F).roundToInt().coerceIn(0, 255)
        }
        action.onCommandChanged(
            GLights = effect.gLight,
            Speed = speed,
            Brightness = brightness,
            red = red,
            green = green,
            blue = blue,
            white = white,
            hue = hue
        )
    }

    private fun moveHueColorSettingsBelowHue() {
        val parent = viewBinding.viewHueSetting.parent as? ViewGroup ?: return
        val rgb = viewBinding.viewRgbSetting
        if (rgb.parent !== parent) return
        parent.removeView(rgb)
        val hueIndex = parent.indexOfChild(viewBinding.viewHueSetting)
        parent.addView(rgb, (hueIndex + 1).coerceAtMost(parent.childCount))
    }

    private fun addTapSyncControls() {
        val parent = viewBinding.viewExtraSettings as? ViewGroup ?: return
        if (parent.findViewWithTag<View>("mobiled_tap_sync") != null) return

        val column = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            tag = "mobiled_tap_sync"
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpTap(8), 0, 0)
        }

        val button = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dpTap(34)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpTap(5).toFloat()
                setColor(Color.rgb(32, 32, 32))
                setStroke(dpTap(1), Color.rgb(85, 85, 85))
            }
            setPadding(dpTap(14), 0, dpTap(14), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { registerTap() }
        }

        tapLedView = View(requireContext()).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(85, 85, 85))
            }
        }
        button.addView(tapLedView, LinearLayout.LayoutParams(dpTap(8), dpTap(8)).apply { rightMargin = dpTap(8) })
        button.addView(TextView(requireContext()).apply {
            text = "Tap Sync"
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        tapBpmView = TextView(requireContext()).apply {
            text = "BPM —"
            textSize = 10f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dpTap(5), 0, 0)
        }

        column.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpTap(34)))
        column.addView(tapBpmView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        parent.addView(column, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun dpTap(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun setTapLed(on: Boolean) {
        val view = tapLedView ?: return
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (on) Color.rgb(255, 174, 0) else Color.rgb(85, 85, 85))
        }
    }

    private fun registerTap() {
        val now = System.currentTimeMillis()
        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2000L) {
            tapTimes.clear()
            stopTapBeat()
        }
        tapTimes.addLast(now)
        while (tapTimes.size > 5) tapTimes.removeFirst()
        flashTapLed()
        if (tapTimes.size < 2) {
            tapBpmView?.text = "BPM —"
            return
        }
        val values = tapTimes.toList()
        val intervals = values.zipWithNext { a, b -> (b - a).toDouble() }
        val recent = intervals.takeLast(4)
        val avg = recent.average().coerceAtLeast(1.0)
        val bpm = (60000.0 / avg).coerceIn(1.0, 200.0)
        val speed = (1.0 + (bpm - 1.0) * 79.0 / 199.0).roundToInt().coerceIn(1, 80)
        tapBpmView?.text = "BPM ${bpm.roundToInt()}"
        startTapBeat(bpm)
        viewBinding.viewExtraPart.viewSlider.value = speed.toFloat()
        viewBinding.viewExtraSliderPreview.text = "${speed}%"
        saveLegacySpeed(speed)
        saveEffectParam("speed", speed)
        (activity as ActionListener?)?.onCommandChanged(GLights = effect.gLight, Speed = speed)
    }

    private fun flashTapLed() {
        setTapLed(true)
        tapLedOffRunnable?.let(tapHandler::removeCallbacks)
        tapLedOffRunnable = Runnable { setTapLed(false) }
        tapHandler.postDelayed(tapLedOffRunnable!!, 120L)
    }

    private fun startTapBeat(bpm: Double) {
        stopTapBeat()
        val period = (60000.0 / bpm).roundToInt().coerceAtLeast(1)
        val r = object : Runnable {
            override fun run() {
                flashTapLed()
                tapHandler.postDelayed(this, period.toLong())
            }
        }
        tapBeatRunnable = r
        tapHandler.post(r)
    }

    private fun stopTapBeat() {
        tapBeatRunnable?.let { tapHandler.removeCallbacks(it) }
        tapBeatRunnable = null
        tapLedOffRunnable?.let(tapHandler::removeCallbacks)
        tapLedOffRunnable = null
        setTapLed(false)
    }

    private fun preLoadEffectSetting() {
        viewBinding.viewUnderDevelopmentPart.hide()
        (activity as ControllerActivity?)?.changeEffectPageBackground(effect.gLight)

        // PC stores each legacy effect's Speed independently (legacyPreset(id)).
        // Keep that behavior on Android so an old Discovery Speed from another
        // effect, or a stale zero for Heartbeat, cannot make the effect crawl.
        val speedValue = loadLegacySpeed(effect.gLight)
        val brightnessKey = if (effect.isRGBW || effect.isColorBrightness) "colorBrightness" else "brightness"
        val brightnessValue = savedEffectParam(brightnessKey, pcDefaultLegacyParam(effect.gLight, brightnessKey)).coerceIn(0,100)
        viewBinding.viewBrightnessPart.viewSlider.valueFrom = 0F
        viewBinding.viewBrightnessPart.viewSlider.valueTo = 100F
        viewBinding.viewBrightnessPart.viewSlider.value = brightnessValue.toFloat()
        viewBinding.viewBrightnessPreview.text = "$brightnessValue%"

        if (effect.isSpeed || effect.isFrequency || effect.isSensitivity) {
            val tapSyncEffect = effect.gLight == 4 || effect.gLight == 8 || effect.gLight == 12
            viewBinding.viewExtraPart.viewSlider.valueFrom = if (tapSyncEffect) 1F else 0F
            viewBinding.viewExtraPart.viewSlider.valueTo = 100F
            val uiValue = if (effect.isSensitivity) 100 - speedValue else speedValue
            viewBinding.viewExtraPart.viewSlider.value = uiValue.coerceIn(0, 100).toFloat()
            viewBinding.viewExtraSliderPreview.text = "${uiValue}%"
        }

        if (effect.isHue) {
            val hueProtocol = savedEffectParam("hue", pcDefaultLegacyParam(effect.gLight, "hue")).coerceIn(0, 255)
            val hueDegrees = hueProtocol * 360F / 255F
            hsvColor[0] = hueDegrees
            hsvColor[1] = 1F
            viewBinding.viewHuePart.viewSlider.post {
                viewBinding.viewHuePart.viewSlider.progress =
                    Math.round(hueDegrees).coerceIn(0, 360)
            }
            viewBinding.viewHuePreview.text = "(Hue = $hueProtocol)"
        }

        if (effect.isHue && !effect.isRGBW) {
            val colorBrightness = savedEffectParam("colorBrightness", pcDefaultLegacyParam(effect.gLight, "colorBrightness")).coerceIn(0, 100)
            val whiteBrightness = savedEffectParam("whiteBrightness", pcDefaultLegacyParam(effect.gLight, "whiteBrightness")).coerceIn(0,100) * 255 / 100
            if (effect.isColorBrightness) {
                viewBinding.viewColorBrightnessPart.viewSlider.value = colorBrightness.toFloat()
                viewBinding.viewColorB.text = "$colorBrightness%"
            }
            if (effect.isWhite) {
                val wb = whiteBrightness * 100F / 255F
                viewBinding.viewWhiteBrightnessPart.viewSlider.value = wb
                viewBinding.viewWBPercent.text = "${Math.round(wb)}%"
            }
        }

        if (effect.isRGBW) {
            val hueProtocol = savedEffectParam("hue", pcDefaultLegacyParam(effect.gLight, "hue")).coerceIn(0, 255)
            val colorBrightness = savedEffectParam("colorBrightness", pcDefaultLegacyParam(effect.gLight, "colorBrightness")).coerceIn(0, 100).toFloat()
            val whiteBrightness = savedEffectParam("whiteBrightness", pcDefaultLegacyParam(effect.gLight, "whiteBrightness")).coerceIn(0, 100).toFloat()

            hsvColor[0] = hueProtocol * 360F / 255F
            hsvColor[1] = 1F
            hsvColor[2] = colorBrightness / 100F
            viewBinding.viewColorPart.viewSlider.post {
                viewBinding.viewColorPart.viewSlider.progress = (hueProtocol * 360F / 255F).roundToInt().coerceIn(0, 360)
            }
            viewBinding.viewColorBrightnessPart.viewSlider.value = colorBrightness
            viewBinding.viewWhiteBrightnessPart.viewSlider.value = whiteBrightness
            bindHsvColor(false)
        }
    }

    private fun bindHsvColor(sendCommand: Boolean = true) {

        val colorBrightness = viewBinding.viewColorBrightnessPart.viewSlider.value.coerceIn(0F, 100F)
        val whiteBrightness = viewBinding.viewWhiteBrightnessPart.viewSlider.value.coerceIn(0F, 100F)
        viewBinding.viewColorB.setText("${Math.round(colorBrightness)}%")
        viewBinding.viewWBPercent.setText("${Math.round(whiteBrightness)}%")

        if (effect.isHue && !effect.isRGBW) {
            val previewColor = Color.HSVToColor(floatArrayOf((hsvColor[0] + 360F) % 360F, 1F, (colorBrightness / 100F).coerceIn(0F, 1F)))
            viewBinding.viewColorPreview.setBackgroundColor(previewColor)
            viewBinding.viewColorPreview.alpha = GeneralUtil.generateAlphaByWhiteBrightness(whiteBrightness, colorBrightness)
            viewBinding.viewExtraSliderPreview.text = "${viewBinding.viewExtraPart.viewSlider.value.toInt()}%"
            if (sendCommand) {
                (activity as ActionListener?)?.onCommandChanged(
                    GLights = effect.gLight,
                    Brightness = colorBrightness.toInt(),
                    white = Math.round((whiteBrightness / 100F) * 255F),
                    hue = Math.round(((hsvColor[0] + 360F) % 360F) / 360F * 255F)
                )
            }
            return
        }

        hsvColor[2] = (colorBrightness / 100F).coerceIn(0F, 1F)
        val normalColor = Color.HSVToColor(hsvColor)
        viewBinding.viewColorPreview.setBackgroundColor(normalColor)
        viewBinding.viewColorPreview.alpha = GeneralUtil.generateAlphaByWhiteBrightness(whiteBrightness, colorBrightness)

        val whiteBrightness255 = Math.round((whiteBrightness / 100F) * 255F)
        val r = Color.red(normalColor)
        val g = Color.green(normalColor)
        val b = Color.blue(normalColor)
        viewBinding.viewRgbPreview.text = if (effect.isRGBW) {
            "(Hue = ${hsvColor[0].roundToInt().coerceIn(0, 360)})"
        } else {
            String.format("(R = %03d, G = %03d, B = %03d)", r, g, b)
        }

        if (sendCommand) {
            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                red = r,
                green = g,
                blue = b,
                white = whiteBrightness255
            )
        }
        viewBinding.viewExtraSliderPreview.text = "${viewBinding.viewExtraPart.viewSlider.value.toInt()}%"
    }

    companion object {

        @JvmStatic
        fun newInstance(effect: Effect): EffectSettingFragment {
            var bundle = Bundle()
            bundle.putSerializable("Effect", effect)
            return EffectSettingFragment().apply {
                arguments = bundle
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LogSystem.e(TAG, "onResume: ")
    }

    override fun onDestroyView() {
        stopTapBeat()
        super.onDestroyView()
    }
}