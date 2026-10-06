package com.mobiled.android.model

import androidx.annotation.Keep
import com.mobiled.android.R
import java.io.Serializable


@Keep
data class Effect(
    val name: String = "None",
    val gLight: Int = 1,
    val effectResource: Int = R.drawable.eff_glight_none
) : Serializable {
    var isRGBW = false
    var isHue = false
    var isBrightness = false
    var isSpeed = false
    var isFrequency = false
    var isSensitivity = false


}
