package com.mobiled.android.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import com.mobiled.android.R
import com.mobiled.android.ui.controller.MusicIconDrawable
import org.json.JSONObject

object GeneralUtil {
    fun setEffectImage(command: JSONObject, view: ImageView) {
        if (command.getInt("GLights") == 0) {
            setColorBackground(command, view)
        } else {
            setEffectImage(command.getInt("GLights"), view)
        }
    }

    fun isSameEffectOrColor(command: JSONObject, command2: JSONObject): Boolean {
        try {
            if (command.getInt("GLights") != command2.getInt("GLights")) {
                return false;
            }

            if (command.getInt("GLights") == 0) {
                var color1 = findColor(command)
                var color2 = findColor(command2)

                if (color1 == color2) {
                    if (findAlpha(command) == findAlpha(command2)) return true
                }
            } else {
                return command.getInt("GLights") == command2.getInt("GLights")
            }
        } catch (e: Exception) {

        }
        return false;
    }

    private fun setColorBackground(command: JSONObject, view: ImageView) {
        var color = findColor(command)

        var radius = view.height
        if (radius <= 0) {
            radius = 200;
        }
        var alpha = findAlpha(command)
        view.setImageDrawable(buildColorBackground(color, radius, alpha))
    }

    private fun findAlpha(command: JSONObject): Float {
        return generateAlphaByWhiteBrightness(
            (command.getInt("white") / 255F) * 100F,
            command.getInt("Brightness") * 1F
        )
    }

    private fun findColor(command: JSONObject): Int {
        var R = command.getInt("red");
        var G = command.getInt("green");
        var B = command.getInt("blue");
        var color = Color.argb(255, R, G, B)
        if (R == 0 && G == 0 && B == 0) {
            R = 255;
        }

        var hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = command.getInt("Brightness") / 100F;
        color = Color.HSVToColor(hsv)
        return color
    }

    fun findColorString(command: JSONObject): String {
        var R = command.getInt("red");
        var G = command.getInt("green");
        var B = command.getInt("blue");
        var color = Color.argb(255, R, G, B)
        if (R == 0 && G == 0 && B == 0) {
            R = 255;
        }

        var hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = command.getInt("Brightness") / 100F;

        return "${hsv[0]},${hsv[1]},${hsv[2]},${
            generateAlphaByWhiteBrightness(
                (command.getInt("white") / 255F) * 100F,
                command.getInt("Brightness") * 1F
            )
        }"
    }

    fun setEffectImage(gLight: Int, view: ImageView) {
        var imageRes = R.drawable.eff_glight_none
        when (gLight) {
            0 -> {
                imageRes = -11
            }

            1 -> {
                imageRes = R.drawable.eff_rainbow
            }

            2 -> {
                imageRes = R.drawable.eff_lava
            }

            3 -> {
                imageRes = R.drawable.eff_ocean
            }

            4 -> {
                imageRes = R.drawable.eff_breath
            }

            5 -> {
                imageRes = R.drawable.eff_lightning
            }

            6 -> {
                imageRes = R.drawable.eff_police1
            }

            7 -> {
                imageRes = R.drawable.eff_police2
            }

            8 -> {
                imageRes = R.drawable.eff_strobe
            }

            9 -> {
                imageRes = R.drawable.eff_candle
            }

            10 -> {
                imageRes = R.drawable.eff_mic
            }

            11 -> {
                imageRes = R.drawable.eff_mic_rainbow
            }

            12 -> {
                imageRes = R.drawable.eff_heartbeat
            }

            100 -> {
                view.setImageDrawable(MusicIconDrawable())
                return
            }

            101 -> {
                imageRes = R.drawable.matrix_101
            }

            102 -> {
                imageRes = R.drawable.matrix_102
            }

            103 -> {
                imageRes = R.drawable.matrix_103
            }

            104 -> {
                imageRes = R.drawable.matrix_104
            }

            105 -> {
                imageRes = R.drawable.matrix_105
            }

            106 -> {
                imageRes = R.drawable.matrix_106
            }

            107 -> {
                imageRes = R.drawable.matrix_107
            }

            108 -> {
                imageRes = R.drawable.matrix_108
            }

            109 -> {
                imageRes = R.drawable.matrix_109
            }

            110 -> {
                imageRes = R.drawable.matrix_110
            }

            111 -> {
                imageRes = R.drawable.matrix_111
            }

            112 -> {
                imageRes = R.drawable.matrix_112
            }
        }
        view.setImageResource(imageRes)
    }

    fun buildColorBackground(color: Int, radius: Int, alpha: Float): LayerDrawable {
        val gradientDrawable = GradientDrawable()
        gradientDrawable.cornerRadius = radius.toFloat()
        gradientDrawable.setColor(color)
        gradientDrawable.alpha = Math.round(alpha * 255F)
        gradientDrawable.shape = GradientDrawable.RECTANGLE

        val whiteGradient = GradientDrawable()
        whiteGradient.cornerRadius = radius.toFloat()
        whiteGradient.setColor(Color.WHITE)
        whiteGradient.shape = GradientDrawable.RECTANGLE

        return LayerDrawable(arrayOf<Drawable>(whiteGradient, gradientDrawable))
    }


    fun buildColorBackgroundByStroke(color: Int, radius: Int): GradientDrawable {
        val gradientDrawable = GradientDrawable()
        gradientDrawable.setStroke(5, Color.WHITE)
        gradientDrawable.cornerRadius = radius.toFloat()
        gradientDrawable.setColor(color)
        gradientDrawable.shape = GradientDrawable.RECTANGLE
        return gradientDrawable
    }

    fun buildColorBackgroundByStroke(array: IntArray, radius: Int): GradientDrawable {
        var color = Color.TRANSPARENT
        if (array.size > 2) {
            color = Color.argb(array[0], array[1], array[2], array[3])
        }
        val gradientDrawable = GradientDrawable()
        gradientDrawable.setStroke(5, Color.WHITE)
        gradientDrawable.cornerRadius = radius.toFloat()
        gradientDrawable.setColor(color)
        gradientDrawable.shape = GradientDrawable.RECTANGLE
        return gradientDrawable
    }

    fun buildColorStoreBackground(array: IntArray, radius: Int): GradientDrawable {
        var color = Color.TRANSPARENT
        if (array.size == 6) {
            var a = Math.round(
                generateAlphaByWhiteBrightness(
                    array[5].toFloat(),
                    array[4].toFloat()
                ) * 255F
            );
            if (a > 255) {
                a = 255;
            }
            color = Color.argb(a, array[1], array[2], array[3])
        }
        val gradientDrawable = GradientDrawable()
        gradientDrawable.cornerRadius = radius.toFloat()
        gradientDrawable.setColor(color)
        gradientDrawable.shape = GradientDrawable.RECTANGLE
        return gradientDrawable
    }

    var alphaStartRange = 0F   // Declare a mutable variable to hold the start of the alpha range, initialized to 0
    val MIN_RANG = 0.2F        // Declare a immutable variable to hold the minimum alpha range, initialized to 0.2

    /**
     * Calculate Alpha Using While Slide & Color Slider
     *
     * @param whiteSlider is Alpha Slider 1 - 100
     * @param colorSlider is Brightness Slider 1 - 100
     */
    @Synchronized   // Add a synchronized block to ensure that only one thread can execute this function at a time
    fun generateAlphaByWhiteBrightness(whiteSlider: Float, colorSlider: Float): Float {
        val alphaEndRange = 1F   // Set the end of the alpha range to 1
        alphaStartRange = 0F   // Set the start of the alpha range to 0
        if (colorSlider > 20.0F) {   // If the color slider value is greater than 20
            alphaStartRange = MIN_RANG   // Set the start of the alpha range to the minimum alpha range
        }

        val range = alphaEndRange - alphaStartRange   // Calculate the range of the alpha values
        val valueInRange = alphaEndRange - whiteSlider / 100.0 * range   // Calculate the alpha value in the range based on the white slider value
        return valueInRange.toFloat()   // Return the calculated alpha value as a float
    }

    fun bindSwitchImage(command: Int, imageLayerView: ImageView, backgroundSwitchLayerView: View) {
        ImageViewCompat.setImageTintMode(imageLayerView, PorterDuff.Mode.SRC_ATOP);
        if (command == 1) {
            imageLayerView.setImageResource(R.drawable.ic_device_switch_on)
            ImageViewCompat.setImageTintList(imageLayerView, ColorStateList.valueOf(Color.GREEN))
            //backgroundSwitchLayerView.setBackgroundResource(R.drawable.ic_circle_border_green)
        } else {
            ImageViewCompat.setImageTintList(imageLayerView, null)
            imageLayerView.setImageResource(R.drawable.ic_device_switch_off)
            //backgroundSwitchLayerView.setBackgroundResource(R.drawable.ic_circle_border)
        }
    }


}