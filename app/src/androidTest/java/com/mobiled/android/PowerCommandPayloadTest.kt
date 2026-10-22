package com.mobiled.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobiled.android.base.model.PowerCommandPayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PowerCommandPayloadTest {
    private fun verify(effect: Int, fields: JSONObject) {
        for (command in 0..1) {
            val source = JSONObject(fields.toString())
                .put("GLights", effect).put("Command", 1 - command)
            val original = source.toString()
            val outgoing = JSONObject(PowerCommandPayload.build(source, command))
            assertEquals(command, outgoing.getInt("Command"))
            assertEquals(effect, outgoing.getInt("GLights"))
            assertEquals("Only Command may change", source.length(), outgoing.length())
            val keys = fields.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                assertEquals("Effect $effect: $key", fields.get(key).toString(), outgoing.get(key).toString())
            }
            assertEquals("Discovery must stay unchanged", original, source.toString())
            assertEquals("Explicit retries must produce the same packet",
                outgoing.toString(), PowerCommandPayload.build(source, command))
        }
    }

    @Test fun strobePreservesWhiteWhenRgbBrightnessIsZero() {
        verify(8, JSONObject("""{"Speed":90,"Brightness":0,"red":0,"green":0,"blue":0,"white":255,"GState":"X","GPort":"8889"}"""))
    }

    @Test fun allLegacyEffectsPreserveTheirConfiguration() {
        for (effect in 1..12) {
            val fields = JSONObject("""{"Speed":60,"Brightness":75,"GState":"S","GPort":"10001"}""")
            if (effect == 4 || effect == 12) fields.put("hue", 113).put("white", 180)
            if (effect == 8) fields.put("red", 12).put("green", 34).put("blue", 56).put("white", 255)
            verify(effect, fields)
        }
    }

    @Test fun colorPreservesHueAndRgbw() {
        verify(0, JSONObject("""{"Brightness":0,"Hue":45,"hue":113,"red":12,"green":34,"blue":56,"white":255}"""))
    }

    @Test fun musicAndAllMatrixEffectsPreserveRoutingAndParameters() {
        val routing = JSONObject("""{"GPort":"8890","GUniverse":32000,"PixelID":2,"PixelCount":3}""")
        verify(100, routing)
        for (effect in 101..112) {
            val fields = JSONObject(routing.toString())
                .put("Speed", 80).put("Brightness", 255).put("GState", "M")
                .put("ColorCount", 1).put("Random", 1)
                .put("Custom1", 10).put("Custom2", 20).put("Custom3", 30)
                .put("OnOff1", 1).put("OnOff2", 0)
                .put("Colors", org.json.JSONArray("""[{"r":10,"g":20,"b":30,"w":255}]"""))
            verify(effect, fields)
        }
    }

    @Test fun allEffectIdsPreserveAdditionalDiscoveryFields() {
        for (effect in 0..199) {
            verify(effect, JSONObject("""{"APName":"MobileD_test","Charge":80,"FutureParameter":{"value":7},"Optional":null}"""))
        }
    }

    @Test fun missingFieldsAreNotReplacedWithZeroDefaults() {
        val source = JSONObject("""{"GLights":8,"Command":0,"white":255,"APName":"MobileD_test","Charge":80}""")
        val outgoing = JSONObject(PowerCommandPayload.build(source, 1))
        assertEquals(255, outgoing.getInt("white"))
        assertFalse(outgoing.has("red"))
        assertFalse(outgoing.has("Brightness"))
        assertEquals("MobileD_test", outgoing.getString("APName"))
        assertEquals(80, outgoing.getInt("Charge"))
    }
}
