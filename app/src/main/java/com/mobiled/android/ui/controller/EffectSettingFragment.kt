package com.mobiled.android.ui.controller

import android.graphics.Color
import android.os.Bundle
import com.mobiled.android.LogSystem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import om.android.mobiled.comman.hide
import om.android.mobiled.comman.show


class EffectSettingFragment : Fragment() {

    private val TAG = EffectSettingFragment::class.java.simpleName

    lateinit var viewBinding: FragmentEffectSettingBinding

    var effect = Effect()

    var hsvColor = FloatArray(3).also {
        it[0] = 360F
    }

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

                bindHsvColor();
            }
        }

    }
    val WhiteBrightnessSliderChangeListener = OnChangeListener { slider, value, fromUser ->
        if (fromUser) {
            LogSystem.e(TAG, "White Brightness Slider OnChangeListener : $value")

            viewBinding.viewWBPercent.setText("${Math.round(value)}%")

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
                var hue = color.floatH / (360F / 255F)
                (activity as ActionListener?)?.onCommandChanged(
                    GLights = effect.gLight, hue = hue.toInt()
                )
                viewBinding.viewHuePreview.setText("(Hue = ${hue.toInt()})")
            }
        }

    }

    var ExtraPartSliderListener: OnChangeListener? = null

    val BrightnessPartSliderListener = object : OnChangeListener {
        override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
            if (fromUser) {
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

        viewBinding.viewBrightnessPart.viewSlider.valueFrom = 0F
        viewBinding.viewBrightnessPart.viewSlider.valueTo = 100.0F


        if (effect.isRGBW) {
            viewBinding.viewRgbSetting.show()

            viewBinding.viewColorPart.viewSlider.mode =
                HSLColorPickerSeekBar.Mode.MODE_HUE // Mode.MODE_SATURATION, Mode.MODE_LIGHTNESS
            viewBinding.viewColorPart.viewSlider.coloringMode =
                HSLColorPickerSeekBar.ColoringMode.OUTPUT_COLOR // ColoringMode.OUTPUT_COLOR


            viewBinding.viewColorPart.viewSlider.addListener(ColorPartSlider)

            viewBinding.viewColorBrightnessPart.viewSlider.addOnChangeListener(ColorBrightnessPartChangeListener)

            viewBinding.viewWhiteBrightnessPart.viewSlider.addOnChangeListener(WhiteBrightnessSliderChangeListener)
        }

        if (effect.isHue) {
            viewBinding.viewHueSetting.show()
            viewBinding.viewHuePart.viewSlider.mode =
                HSLColorPickerSeekBar.Mode.MODE_HUE // Mode.MODE_SATURATION, Mode.MODE_LIGHTNESS
            viewBinding.viewHuePart.viewSlider.coloringMode =
                HSLColorPickerSeekBar.ColoringMode.PURE_COLOR // ColoringMode.OUTPUT_COLOR

            viewBinding.viewHuePart.viewSlider.addListener(HuePartSliderColorChangeListener)

        }

        if (effect.isBrightness) {
            viewBinding.viewBrightnessSetting.show()
        }

        if (effect.isSpeed || effect.isFrequency || effect.isSensitivity) {
            viewBinding.viewExtraSettings.show()
            if (effect.isSpeed) {

                viewBinding.viewExtraSettingText.setText("Speed")
                ExtraPartSliderListener = object : OnChangeListener {
                    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
                        if (fromUser) {
                            (activity as ActionListener?)?.onCommandChanged(
                                GLights = effect.gLight, Speed = value.toInt()
                            )
                        }
                        viewBinding.viewExtraSliderPreview.text = "${value.toInt()}%"
                    }
                }
                viewBinding.viewExtraPart.viewSlider.addOnChangeListener(ExtraPartSliderListener!!)
            } else if (effect.isFrequency) {
                ExtraPartSliderListener = object : OnChangeListener {
                    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
                        if (fromUser) {
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

    private fun preLoadEffectSetting() {

        viewBinding.viewUnderDevelopmentPart.hide()
        (activity as ControllerActivity?)?.changeEffectPageBackground(effect.gLight)
        if (effect.gLight == 1) {

            //{"Command": 1, "GLights": 1, "Speed": 80, "Brightness": 100, "hue": 0,
            //"red": 0,"green": 0,"blue": 0,"white": 0}

            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                Speed = 80,
                Brightness = 100,
                red = 0,
                green = 0,
                blue = 0,
                white = 0,
                hue = -1,
            )

            viewBinding.viewBrightnessPart.viewSlider.valueFrom = 0F
            viewBinding.viewBrightnessPart.viewSlider.valueTo = 100.0F


            viewBinding.viewExtraPart.viewSlider.valueFrom = 0F
            viewBinding.viewExtraPart.viewSlider.valueTo = 100F

            viewBinding.viewBrightnessPart.viewSlider.value = 100F
            viewBinding.viewExtraPart.viewSlider.value = 80F

        } else if (effect.gLight == 2 || effect.gLight == 3) {

            //2
            //{"Command": 1, "GLights": 2, "Speed": 0, "Brightness": 100, "hue": 0,
            //“red": 0, "green": 0, "blue": 0, "white": 0}

            //3
            //{"Command": 1, "GLights": 3, "Speed": 0, "Brightness": 100, "hue": 0, "red":
            //0, "green": 0, "blue": 0, "white": 0}


            viewBinding.viewBrightnessPart.viewSlider.value = 100F
            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                Speed = 0,
                Brightness = 100,
                hue = 0,
                red = 0,
                green = 0,
                blue = 0,
                white = 0,
            )
        } else if (effect.gLight == 4) {
            //{"Command": 1, "GLights": 4, "Speed": 60, "Brightness": 0, "hue": 160,
            //"red": 0, "green": 0, "blue": 0, "white": 0}

            viewBinding.viewExtraPart.viewSlider.valueFrom = 0F
            viewBinding.viewExtraPart.viewSlider.valueTo = 100F
            viewBinding.viewExtraPart.viewSlider.value = 60F

            viewBinding.viewHuePart.viewSlider.post {
                viewBinding.viewHuePart.viewSlider.progress = Math.round((160F / 255F) * 360F)
            }
            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                Speed = 60,
                Brightness = 0,
                hue = 160,
                red = 0,
                green = 0,
                blue = 0,
                white = 0,
            )
        } else if (effect.gLight == 5) {

            //{"Command": 1, "GLights": 5, "Speed": 50, "Brightness": 50, "hue": 0,
            //"red": 0, "green": 0, "blue": 0, "white": 0}

            viewBinding.viewExtraPart.viewSlider.valueFrom = 0F
            viewBinding.viewExtraPart.viewSlider.valueTo = 100F
            viewBinding.viewExtraPart.viewSlider.value = 50F

            viewBinding.viewBrightnessPart.viewSlider.value = 50F

            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                Speed = 50,
                Brightness = 50,
                hue = 0,
                red = 0,
                green = 0,
                blue = 0,
                white = 0,
            )
        } else if (effect.gLight == 6) {

            //{"Command": 1, "GLights": 6, "Speed": 0, "Brightness": 100, "hue": 0,
            //"red": 0, "green": 0, "blue": 0, "white": 0}
            viewBinding.viewBrightnessPart.viewSlider.value = 100F

            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                Speed = 0,
                Brightness = 100,
                hue = 0,
                red = 0,
                green = 0,
                blue = 0,
                white = 0,
            )
        } else if (effect.gLight == 7) {
            //{"Command": 1, "GLights": 7, "Speed": 0, "Brightness": 100, "hue": 0, "red":
            //0, "green": 0, "blue": 0, "white": 0}
            viewBinding.viewBrightnessPart.viewSlider.value = 100F

            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                Speed = 0,
                Brightness = 100,
                hue = 0,
                red = 0,
                green = 0,
                blue = 0,
                white = 0,
            )
        } else if (effect.gLight == 8) {
            //{"Command": 1,"GLights": 8,"Speed": 90,"Brightness": 0,"hue": 0, "red":
            //0,"green": 0,"blue": 0,"white": 255}

            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                Speed = 90,
                Brightness = 0,
                hue = 0,
                red = 0,
                green = 0,
                blue = 0,
                white = 255,
            )

            viewBinding.viewExtraPart.viewSlider.valueFrom = 0F
            viewBinding.viewExtraPart.viewSlider.valueTo = 100F
            viewBinding.viewExtraPart.viewSlider.value = 90F

            viewBinding.viewColorPart.viewSlider.progress = 360


            viewBinding.viewColorBrightnessPart.viewSlider.value = 0F
            viewBinding.viewWhiteBrightnessPart.viewSlider.value = 100F


            hsvColor = FloatArray(3)
            hsvColor[0] = 360F
            hsvColor[1] = 1F
            hsvColor[2] = 1F

            viewBinding.viewExtraSliderPreview.text = "${90}%"
            bindHsvColor(false)
        } else if (effect.gLight == 9) {
            //{"Command": 1, "GLights": 9, "Speed": 0, "Brightness": 100, "red": 0,
            //"green": 0, "blue": 0, "white":0}

            viewBinding.viewBrightnessPart.viewSlider.value = 100F

            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                Speed = 0,
                Brightness = 100,
                hue = 0,
                red = 0,
                green = 0,
                blue = 0,
                white = 0,
            )
        } else if (effect.gLight == 10 || effect.gLight == 11) {
            //{"Command": 1, "GLights": 10, "Speed": 80, "Brightness": 0, "red": 0,
            //"green": 0, "blue": 0, "white": 0}

            viewBinding.viewExtraPart.viewSlider.valueFrom = 0F
            viewBinding.viewExtraPart.viewSlider.valueTo = 100F
            viewBinding.viewExtraPart.viewSlider.value = 100F



            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                Speed = 100,
                Brightness = 0,
                hue = 0,
                red = 0,
                green = 0,
                blue = 0,
                white = 0,
            )
        } else if (effect.gLight == 12) {
            //{"Command": 1, "GLights": 12, "Speed": 50, "Brightness": 0, "red": 255,
            //"green": 0, "blue": 0, "white": 0}


            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                Speed = 50,
                Brightness = 0,
                hue = 0,
                red = 0,
                green = 0,
                blue = 0,
                white = 0,
            )

            viewBinding.viewExtraPart.viewSlider.valueFrom = 0F
            viewBinding.viewExtraPart.viewSlider.valueTo = 100F
            viewBinding.viewExtraPart.viewSlider.value = 50F

            viewBinding.viewColorPart.viewSlider.progress = 360


            viewBinding.viewColorBrightnessPart.viewSlider.value = 0F
            viewBinding.viewWhiteBrightnessPart.viewSlider.value = 0F


            hsvColor = FloatArray(3)
            hsvColor[0] = 360F
            hsvColor[1] = 1F
            hsvColor[2] = 1F

            bindHsvColor(false)
        } else {
            viewBinding.viewUnderDevelopmentPart.show()
        }
    }

    private fun bindHsvColor(sendCommand: Boolean = true) {

        viewBinding.viewColorB.setText("${Math.round(viewBinding.viewColorBrightnessPart.viewSlider.value)}%")

        var colorBrightness = viewBinding.viewColorBrightnessPart.viewSlider.value / 100F
        hsvColor[2] = colorBrightness

        var normalColor = Color.HSVToColor(hsvColor)

        viewBinding.viewColorPreview.setBackgroundColor(normalColor)

        var alpha = GeneralUtil.generateAlphaByWhiteBrightness(viewBinding.viewWhiteBrightnessPart.viewSlider.value, viewBinding.viewColorBrightnessPart.viewSlider.value)

        viewBinding.viewColorPreview.alpha = alpha


        var whiteBrightness255 =
            Math.round((viewBinding.viewWhiteBrightnessPart.viewSlider.value / 100F) * 255F)


        var r = Color.red(normalColor)
        var g = Color.green(normalColor)
        var b = Color.blue(normalColor)

        viewBinding.viewRgbPreview.setText(
            String.format(
                "(R = %03d, G = %03d, B = %03d)", r, g, b
            )
        )


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
}