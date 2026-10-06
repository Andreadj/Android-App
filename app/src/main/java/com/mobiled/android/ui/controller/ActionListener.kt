package com.mobiled.android.ui.controller

import com.mobiled.android.model.Effect
import org.json.JSONObject

interface ActionListener {
    fun onColorChanged(a: Int, r: Int, g: Int, b: Int, whiteBrightness: Int, colorBrightness: Int)
    fun getFrame(): JSONObject

    fun onCommandChanged(
        GLights: Int = -1,
        Speed: Int = -1,
        Brightness: Int = -1,
        red: Int = -1,
        green: Int = -1,
        blue: Int = -1,
        white: Int = -1,
        hue: Int = -1
    )

    fun showMicEffectPage(effect: Effect)
    fun hasSameColor(): Boolean
}