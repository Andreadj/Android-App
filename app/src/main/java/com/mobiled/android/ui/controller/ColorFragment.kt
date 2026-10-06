package com.mobiled.android.ui.controller

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.slider.Slider.OnChangeListener
import com.mobiled.android.LogSystem
import com.mobiled.android.adapters.GeneralUtil
import com.mobiled.android.base.BaseFragment
import com.mobiled.android.base.comman.KeyStorage
import com.mobiled.android.base.component.ColorPickerView.OnColorChangedListener
import com.mobiled.android.databinding.FragmentColorBinding
import om.android.mobiled.comman.hide
import om.android.mobiled.comman.show
import org.json.JSONObject


class ColorFragment : BaseFragment<FragmentColorBinding>() {

    private val TAG = ColorFragment::class.java.simpleName

    var argb: IntArray = IntArray(0)
    var hsl: IntArray = IntArray(0)
    var warmWhite255 = 255


    override fun getFragmentBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentColorBinding {
        return FragmentColorBinding.inflate(inflater, container, false)
    }

    override fun handleBackPress(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogSystem.e(TAG, "onCreate")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogSystem.e(TAG, "onViewCreated")

        viewBinding.viewColorBrightnessPart.viewSlider.valueFrom = 0F
        viewBinding.viewColorBrightnessPart.viewSlider.valueTo = 100F
        viewBinding.viewColorBrightnessPart.viewSlider.value = 100F
        viewBinding.viewWhiteBrightnessPart.viewSlider.valueFrom = 0F
        viewBinding.viewWhiteBrightnessPart.viewSlider.valueTo = 100F
        viewBinding.viewWhiteBrightnessPart.viewSlider.value = 0F

        viewBinding.viewCBPercent.setText("${Math.round(viewBinding.viewColorBrightnessPart.viewSlider.value)}%")
        viewBinding.viewWBPercent.setText("${Math.round(viewBinding.viewWhiteBrightnessPart.viewSlider.value)}%")


        viewBinding.viewColorBox1.viewRoot.setOnClickListener(toastMessage)
        viewBinding.viewColorBox1.viewRoot.setOnLongClickListener(saveColorListener)
        viewBinding.viewColorBox1.viewRoot.setTag("viewColor1")

        viewBinding.viewColorBox2.viewRoot.setOnClickListener(toastMessage)
        viewBinding.viewColorBox2.viewRoot.setOnLongClickListener(saveColorListener)
        viewBinding.viewColorBox2.viewRoot.setTag("viewColor2")

        viewBinding.viewColorBox3.viewRoot.setOnClickListener(toastMessage)
        viewBinding.viewColorBox3.viewRoot.setOnLongClickListener(saveColorListener)
        viewBinding.viewColorBox3.viewRoot.setTag("viewColor3")

        viewBinding.viewColorBox4.viewRoot.setOnClickListener(toastMessage)
        viewBinding.viewColorBox4.viewRoot.setOnLongClickListener(saveColorListener)
        viewBinding.viewColorBox4.viewRoot.setTag("viewColor4")

        viewBinding.viewColorBox5.viewRoot.setOnClickListener(toastMessage)
        viewBinding.viewColorBox5.viewRoot.setOnLongClickListener(saveColorListener)
        viewBinding.viewColorBox5.viewRoot.setTag("viewColor5")


        bindColorStorageUI()

        try {
            var Frame = (activity as ActionListener?)?.getFrame() ?: JSONObject()
            LogSystem.e(TAG, "Color Frame : $Frame")
            var R = Frame.getInt("red");
            var G = Frame.getInt("green");
            var B = Frame.getInt("blue");
            if (R == 0 && G == 0 && B == 0) {
                R = 255;
            }

            if ((activity as ActionListener?)?.hasSameColor() == false) {
                R = 255;
                G = 0;
                B = 0;
            }

            viewBinding.colorPickerView.setColor(255, R, G, B)
            viewBinding.viewColorBrightnessPart.viewSlider.value = Frame.getInt("Brightness") * 1F
            viewBinding.colorPickerView.setBrightness(Frame.getInt("Brightness") / 100F)
            viewBinding.viewWhiteBrightnessPart.viewSlider.value =
                (Frame.getInt("white") / 255F) * 100F
            viewBinding.colorPickerView.setColorAlpha(
                GeneralUtil.generateAlphaByWhiteBrightness(
                    viewBinding.viewWhiteBrightnessPart.viewSlider.value,
                    viewBinding.viewColorBrightnessPart.viewSlider.value
                )
            )
            viewBinding.viewCBPercent.setText("${Math.round(viewBinding.viewColorBrightnessPart.viewSlider.value)}%")
            viewBinding.viewWBPercent.setText("${Math.round(viewBinding.viewWhiteBrightnessPart.viewSlider.value)}%")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        //COLOR PICKER
        viewBinding.itemColorHuePlus.setOnClickListener {
            viewBinding.colorPickerView.increaseHue()
        }

        viewBinding.itemColorHueMinus.setOnClickListener {
            viewBinding.colorPickerView.decreaseHue()
        }


        viewBinding.colorPickerView.setColorChangedListener(object : OnColorChangedListener {
            override fun colorChanged(centerColor: Int, argb: IntArray, hsv: FloatArray) {
                LogSystem.d(
                    TAG,
                    "colorChanged() called with: centerColor = $centerColor, argb = ${
                        com.mobiled.android.base.comman.TextUtil.toString(
                            argb
                        )
                    }, hsv = ${com.mobiled.android.base.comman.TextUtil.toString(hsv)}"
                )
                this@ColorFragment.argb = argb

                bindPickerUI(hsv)

                var colorBrightness255 =
                    Math.round((viewBinding.viewColorBrightnessPart.viewSlider.value / 100F) * 255F)
                var whiteBrightness255 =
                    Math.round((viewBinding.viewWhiteBrightnessPart.viewSlider.value / 100F) * 255F)
                (activity as ActionListener?)?.onColorChanged(
                    argb[0],
                    argb[1],
                    argb[2],
                    argb[3],
                    whiteBrightness255,
                    viewBinding.viewColorBrightnessPart.viewSlider.value.toInt()
                )
            }
        })
        argb = viewBinding.colorPickerView.toArgb()
        bindPickerUI(viewBinding.colorPickerView.hsv)


        //BRIGHTNESS WORK
        viewBinding.viewColorBrightnessPart.ivAdd.setOnClickListener {
            var colorSlider = viewBinding.viewColorBrightnessPart.viewSlider.value.inc()
            if (colorSlider > viewBinding.viewColorBrightnessPart.viewSlider.valueTo) {
                return@setOnClickListener
            }
            LogSystem.e(TAG, "Color Brightness Slider : $colorSlider")
            viewBinding.viewColorBrightnessPart.viewSlider.value = colorSlider

            viewBinding.colorPickerView.setBrightness(colorSlider.toInt())
            if (isAlphaChangeNeeded(colorSlider)) {
                var alpha = GeneralUtil.generateAlphaByWhiteBrightness(
                    viewBinding.viewWhiteBrightnessPart.viewSlider.value,
                    colorSlider
                )
                viewBinding.colorPickerView.setColorAlpha(alpha)
                LogSystem.e(
                    TAG,
                    "Color Slider Change Alpha Range : ${GeneralUtil.alphaStartRange} Alpha $alpha"
                )
            }
        }
        viewBinding.viewColorBrightnessPart.ivMinus.setOnClickListener {
            var colorSlider = viewBinding.viewColorBrightnessPart.viewSlider.value.dec()
            if (colorSlider < viewBinding.viewColorBrightnessPart.viewSlider.valueFrom) {
                return@setOnClickListener
            }
            LogSystem.e(TAG, "Color Brightness Slider : $colorSlider")
            viewBinding.viewColorBrightnessPart.viewSlider.value = colorSlider

            viewBinding.colorPickerView.setBrightness(colorSlider.toInt())
            if (isAlphaChangeNeeded(colorSlider)) {
                var alpha = GeneralUtil.generateAlphaByWhiteBrightness(
                    viewBinding.viewWhiteBrightnessPart.viewSlider.value,
                    colorSlider
                )
                viewBinding.colorPickerView.setColorAlpha(alpha)
                LogSystem.e(
                    TAG,
                    "Color Slider Change Alpha Range : ${GeneralUtil.alphaStartRange} Alpha $alpha"
                )
            }
        }

        viewBinding.viewColorBrightnessPart.viewSlider.addOnChangeListener(OnChangeListener { slider, colorSlider, fromUser ->
            if (fromUser && argb.size > 3) {
                viewBinding.colorPickerView.setBrightness(colorSlider.toInt())
            }
            viewBinding.viewCBPercent.setText("${Math.round(colorSlider)}%")
            if (isAlphaChangeNeeded(colorSlider)) {
                var alpha = GeneralUtil.generateAlphaByWhiteBrightness(
                    viewBinding.viewWhiteBrightnessPart.viewSlider.value,
                    colorSlider
                )
                viewBinding.colorPickerView.setColorAlpha(alpha)
                LogSystem.e(
                    TAG,
                    "Color Slider Change Alpha Range : ${GeneralUtil.alphaStartRange} Alpha $alpha"
                )
            }
        })

        //ALPHA WORK
        viewBinding.viewWhiteBrightnessPart.ivAdd.setOnClickListener {
            var whiteSlider = viewBinding.viewWhiteBrightnessPart.viewSlider.value.inc()
            LogSystem.e(TAG, "White Brightness Slider : $whiteSlider")
            if (whiteSlider > viewBinding.viewWhiteBrightnessPart.viewSlider.valueTo) {
                return@setOnClickListener
            }
            viewBinding.viewWhiteBrightnessPart.viewSlider.value = whiteSlider

            viewBinding.colorPickerView.setColorAlpha(
                GeneralUtil.generateAlphaByWhiteBrightness(
                    whiteSlider,
                    viewBinding.viewColorBrightnessPart.viewSlider.value
                )
            )
        }
        viewBinding.viewWhiteBrightnessPart.ivMinus.setOnClickListener {
            var whiteSlider = viewBinding.viewWhiteBrightnessPart.viewSlider.value.dec()
            LogSystem.e(TAG, "White Brightness Slider : $whiteSlider")
            if (whiteSlider < viewBinding.viewWhiteBrightnessPart.viewSlider.valueFrom) {
                return@setOnClickListener
            }
            viewBinding.viewWhiteBrightnessPart.viewSlider.value = whiteSlider

            viewBinding.colorPickerView.setColorAlpha(
                GeneralUtil.generateAlphaByWhiteBrightness(
                    whiteSlider,
                    viewBinding.viewColorBrightnessPart.viewSlider.value
                )
            )
        }

        viewBinding.viewWhiteBrightnessPart.viewSlider.addOnChangeListener(OnChangeListener { slider, whiteSlider, fromUser ->
            if (fromUser) {
                LogSystem.e(TAG, "White Brightness Slider : $whiteSlider")
                viewBinding.colorPickerView.setColorAlpha(
                    GeneralUtil.generateAlphaByWhiteBrightness(
                        whiteSlider,
                        viewBinding.viewColorBrightnessPart.viewSlider.value
                    )
                )
            }
            viewBinding.viewWBPercent.setText("${Math.round(whiteSlider)}%")
        })



    }

    private fun isAlphaChangeNeeded(colorSlider: Float): Boolean {
        if ((colorSlider < 20.0F && GeneralUtil.alphaStartRange != 0.0F))
            return true;
        if ((colorSlider >= 20.0F && GeneralUtil.alphaStartRange != GeneralUtil.MIN_RANG))
            return true;
        return false
    }


    val toastMessage: View.OnClickListener =
        View.OnClickListener { showToast("Long press to save current color!"); }
    val saveColorListener: View.OnLongClickListener = View.OnLongClickListener {
        KeyStorage.getKeyStorage(context = requireContext()).setStringValue(
            it.tag.toString(),
            "${com.mobiled.android.base.comman.TextUtil.toString(argb)},${viewBinding.viewColorBrightnessPart.viewSlider.value.toInt()},${viewBinding.viewWhiteBrightnessPart.viewSlider.value.toInt()}"
        )
        bindColorStorageUI()
        return@OnLongClickListener true
    }

    private fun bindColorStorageUI() {
        var keyStorage = KeyStorage.getKeyStorage(context = requireContext())
        var data1 = if (!keyStorage.getStringValue("viewColor1").isNullOrEmpty()) {
            keyStorage.getStringValue("viewColor1").split(",").stream().mapToInt { num ->
                try {
                    num.toInt()
                } catch (ignore: Exception) {
                    255
                }
            }.toArray()
        } else IntArray(0)
        var data2 = if (!keyStorage.getStringValue("viewColor2").isNullOrEmpty()) {
            keyStorage.getStringValue("viewColor2").split(",").stream().mapToInt { num ->
                try {
                    num.toInt()
                } catch (ignore: Exception) {
                    255
                }
            }.toArray()
        } else IntArray(0)
        var data3 = if (!keyStorage.getStringValue("viewColor3").isNullOrEmpty()) {
            keyStorage.getStringValue("viewColor3").split(",").stream().mapToInt { num ->
                try {
                    num.toInt()
                } catch (ignore: Exception) {
                    255
                }
            }.toArray()
        } else IntArray(0)
        var data4 = if (!keyStorage.getStringValue("viewColor4").isNullOrEmpty()) {
            keyStorage.getStringValue("viewColor4").split(",").stream().mapToInt { num ->
                try {
                    num.toInt()
                } catch (ignore: Exception) {
                    100
                }
            }.toArray()
        } else IntArray(0)
        var data5 = if (!keyStorage.getStringValue("viewColor5").isNullOrEmpty()) {
            keyStorage.getStringValue("viewColor5").split(",").stream().mapToInt { num ->
                try {
                    num.toInt()
                } catch (ignore: Exception) {
                    100
                }
            }.toArray()
        } else IntArray(0)
        viewBinding.viewColorBox1.viewColor.background =
            GeneralUtil.buildColorStoreBackground(data1, 15)
        viewBinding.viewColorBox2.viewColor.background =
            GeneralUtil.buildColorStoreBackground(data2, 15)
        viewBinding.viewColorBox3.viewColor.background =
            GeneralUtil.buildColorStoreBackground(data3, 15)
        viewBinding.viewColorBox4.viewColor.background =
            GeneralUtil.buildColorStoreBackground(data4, 15)
        viewBinding.viewColorBox5.viewColor.background =
            GeneralUtil.buildColorStoreBackground(data5, 15)
        (viewBinding.viewColorBox1.viewColorBrightness).text = ""
        (viewBinding.viewColorBox2.viewColorBrightness).text = ""
        (viewBinding.viewColorBox3.viewColorBrightness).text = ""
        (viewBinding.viewColorBox4.viewColorBrightness).text = ""
        (viewBinding.viewColorBox5.viewColorBrightness).text = ""

        viewBinding.viewColorBox1.viewPlus.show()
        viewBinding.viewColorBox2.viewPlus.show()
        viewBinding.viewColorBox3.viewPlus.show()
        viewBinding.viewColorBox4.viewPlus.show()
        viewBinding.viewColorBox5.viewPlus.show()

        if (data1.size > 5) {
            viewBinding.viewColorBox1.viewPlus.hide()
            (viewBinding.viewColorBox1.viewColorBrightness).text = "${data1[4]}%"

            viewBinding.viewColorBox1.viewRoot.setOnClickListener {
                keyStorage.getStringValue("${it.tag}").split(",").stream().mapToInt { num ->
                    try {
                        num.toInt()
                    } catch (ignore: Exception) {
                        100
                    }
                }.toArray().let {
                    extractStorageColor(it)
                }
            }
        }

        if (data2.size > 5) {
            viewBinding.viewColorBox2.viewPlus.hide()
            (viewBinding.viewColorBox2.viewColorBrightness).text = "${data2[4]}%"

            viewBinding.viewColorBox2.viewRoot.setOnClickListener {
                keyStorage.getStringValue("${it.tag}").split(",").stream().mapToInt { num ->
                    try {
                        num.toInt()
                    } catch (ignore: Exception) {
                        100
                    }
                }.toArray().let {
                    extractStorageColor(it)
                }
            }
        }
        if (data3.size > 5) {
            viewBinding.viewColorBox3.viewPlus.hide()
            (viewBinding.viewColorBox3.viewColorBrightness).text = "${data3[4]}%"
            viewBinding.viewColorBox3.viewRoot.setOnClickListener {
                keyStorage.getStringValue("${it.tag}").split(",").stream().mapToInt { num ->
                    try {
                        num.toInt()
                    } catch (ignore: Exception) {
                        100
                    }
                }.toArray().let {
                    extractStorageColor(it)
                }
            }
        }
        if (data4.size > 5) {
            viewBinding.viewColorBox4.viewPlus.hide()
            (viewBinding.viewColorBox4.viewColorBrightness).text = "${data4[4]}%"

            viewBinding.viewColorBox4.viewRoot.setOnClickListener {
                keyStorage.getStringValue("${it.tag}").split(",").stream().mapToInt { num ->
                    try {
                        num.toInt()
                    } catch (ignore: Exception) {
                        100
                    }
                }.toArray().let {
                    extractStorageColor(it)
                }
            }
        }
        if (data5.size > 5) {
            viewBinding.viewColorBox5.viewPlus.hide()
            (viewBinding.viewColorBox5.viewColorBrightness).text = "${data5[4]}%"

            viewBinding.viewColorBox5.viewRoot.setOnClickListener {
                keyStorage.getStringValue("${it.tag}").split(",").stream().mapToInt { num ->
                    try {
                        num.toInt()
                    } catch (ignore: Exception) {
                        100
                    }
                }.toArray().let {
                    extractStorageColor(it)
                }
            }
        }
    }

    private fun extractStorageColor(argb: IntArray) {
        if (argb[1] == 0 && argb[2] == 0 && argb[3] == 0) {
            argb[1] = 255;
        }
        this.argb = intArrayOf(argb[0], argb[1], argb[2], argb[3])

        viewBinding.colorPickerView.setColor(argb[0], argb[1], argb[2], argb[3])
        viewBinding.colorPickerView.setBrightness(argb[4].toFloat() / 100F)
        viewBinding.viewColorBrightnessPart.viewSlider.value = argb[4].toFloat()
        viewBinding.viewWhiteBrightnessPart.viewSlider.value = argb[5].toFloat()
        viewBinding.colorPickerView.setColorAlpha(
            GeneralUtil.generateAlphaByWhiteBrightness(
                argb[5].toFloat(),
                viewBinding.viewColorBrightnessPart.viewSlider.value
            )
        )
    }

    private fun bindPickerUI(hsv: FloatArray) {
        //REF : https://redketchup.io/color-picker


        var HsvRGBColor = Color.HSVToColor(hsv)
        argb[1] = Color.red(HsvRGBColor)
        argb[2] = Color.green(HsvRGBColor)
        argb[3] = Color.blue(HsvRGBColor)

        viewBinding.viewArgb.viewA.setText(String.format("%.2f", argb[0] / 255F))
        viewBinding.viewArgb.viewA.alpha = argb[0] / 255F
        viewBinding.viewArgb.viewR.setText("${argb[1]}")
        viewBinding.viewArgb.viewG.setText("${argb[2]}")
        viewBinding.viewArgb.viewB.setText("${argb[3]}")

        viewBinding.viewHsv.viewH.setText(String.format("%d", hsv[0].toInt()))
        viewBinding.viewHsv.viewS.setText(String.format("%.2f", hsv[1]))
        viewBinding.viewHsv.viewV.setText(String.format("%.2f", hsv[2]))

        viewBinding.CommandValuePreview.setText(
            "\"Brightness\": ${Math.round(hsv[2] * 100)}, \"red\": ${argb[1]}, \"green\": ${argb[2]}, \"blue\":${argb[3]}, \"white\": ${warmWhite255} "
        )
    }

    companion object {

        @JvmStatic
        fun newInstance() = ColorFragment().apply {}
    }

    override fun onResume() {
        super.onResume()
        LogSystem.e(TAG, "onResume: ")
    }
}