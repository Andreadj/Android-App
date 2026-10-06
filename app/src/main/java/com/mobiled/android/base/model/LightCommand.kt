package com.mobiled.android.base.model

import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable

@Keep
class LightCommand : Serializable {
    var Command: Int = 0
    var GLights: Int = 0
    var Speed: Int = 0
    var Brightness: Int = 100
    var red: Int = 0
    var green: Int = 0
    var blue: Int = 0
    var white: Int = 0
    var hue: Int = -1
    var GPort: String = "8889"
    var GState: String = "X"
    var GUniverse: Int = 32000
    var PixelID: Int = 0
    var PixelCount: Int = 1
    var ColorCount: Int = 0
    var Random: Boolean = false
    var Custom1: Int = 0
    var Custom2: Int = 0
    var Custom3: Int = 0
    var OnOff1: Boolean = false
    var OnOff2: Boolean = false
    var Colors: JSONArray? = null

    constructor()

    fun copy(): LightCommand {
        val c = LightCommand()
        c.Command = Command; c.GLights = GLights; c.Speed = Speed; c.Brightness = Brightness
        c.red = red; c.green = green; c.blue = blue; c.white = white; c.hue = hue
        c.GPort = GPort; c.GState = GState; c.GUniverse = GUniverse; c.PixelID = PixelID
        c.PixelCount = PixelCount; c.ColorCount = ColorCount; c.Random = Random
        c.Custom1 = Custom1; c.Custom2 = Custom2; c.Custom3 = Custom3
        c.OnOff1 = OnOff1; c.OnOff2 = OnOff2
        c.Colors = Colors?.let { JSONArray(it.toString()) }
        return c
    }

    fun toJsonString(): String {
        val json = JSONObject()
        json.put("Command", Command)
        json.put("GLights", GLights)
        json.put("Speed", Speed)
        json.put("Brightness", Brightness)
        json.put("red", red); json.put("green", green); json.put("blue", blue); json.put("white", white)
        if (hue >= 0) json.put("hue", hue)
        json.put("GState", GState)
        json.put("GPort", GPort)
        if (GLights >= 100) {
            json.put("GUniverse", GUniverse)
            json.put("PixelID", PixelID)
            json.put("PixelCount", PixelCount)
        }
        if (GLights in 101..199) {
            json.put("ColorCount", ColorCount)
            json.put("Random", Random)
            json.put("Custom1", Custom1); json.put("Custom2", Custom2); json.put("Custom3", Custom3)
            json.put("OnOff1", OnOff1); json.put("OnOff2", OnOff2)
            if (Colors != null) json.put("Colors", Colors)
        }
        return json.toString()
    }

    fun fromJson(json: JSONObject) {
        Command = json.optInt("Command", Command)
        GLights = json.optInt("GLights", GLights)
        Speed = json.optInt("Speed", Speed)
        Brightness = json.optInt("Brightness", Brightness)
        red = json.optInt("red", red); green = json.optInt("green", green)
        blue = json.optInt("blue", blue); white = json.optInt("white", white)
        if (json.has("hue")) hue = json.optInt("hue", hue)
        GState = json.optString("GState", GState)
        GPort = json.optString("GPort", GPort)
        GUniverse = json.optInt("GUniverse", GUniverse)
        PixelID = json.optInt("PixelID", PixelID)
        PixelCount = json.optInt("PixelCount", PixelCount)
        ColorCount = json.optInt("ColorCount", ColorCount)
        Random = json.optBoolean("Random", Random)
        Custom1 = json.optInt("Custom1", Custom1); Custom2 = json.optInt("Custom2", Custom2); Custom3 = json.optInt("Custom3", Custom3)
        OnOff1 = json.optBoolean("OnOff1", OnOff1); OnOff2 = json.optBoolean("OnOff2", OnOff2)
        if (json.has("Colors") && !json.isNull("Colors")) Colors = json.optJSONArray("Colors")
    }

    companion object {
        @JvmStatic fun getActionCodeMessage(actionCode: Int = 0): String = when (actionCode) {
            1 -> "Command Change Request"; 2 -> "GLights Change Request"; 3 -> "Speed Change Request"
            4 -> "Brightness Change Request"; 5 -> "Color Red Change Request"; 6 -> "Color Green Change Request"
            7 -> "Color Blue Change Request"; 8 -> "Color White Change Request"; 9 -> "Color Change Request"
            else -> "Not found!"
        }
    }
}
