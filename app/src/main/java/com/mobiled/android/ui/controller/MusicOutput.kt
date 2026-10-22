package com.mobiled.android.ui.controller

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Wire format and RGBW palette math shared by Music output and JVM checks. */
internal object MusicOutput {
    fun dmxUniverses(frame: List<IntArray>, baseUniverse: Int): Map<Int, ByteArray> {
        require(frame.size in 1..1024)
        val universes = LinkedHashMap<Int, ByteArray>()
        // Every virtual position belongs to DMX output, even when its MobileD
        // is temporarily offline. Each universe always carries all 512 slots.
        frame.forEachIndexed { pixel, rgbw ->
            val universe = baseUniverse + pixel / 128
            val slots = universes.getOrPut(universe) { ByteArray(512) }
            val firstChannel = (pixel % 128) * 4
            for (channel in 0..3) slots[firstChannel + channel] = rgbw.getOrElse(channel) { 0 }.toByte()
        }
        return universes
    }
    fun sacnPacket(dmx: ByteArray, universe: Int, sequence: Int, cid: ByteArray): ByteArray {
        require(dmx.size == 512)
        require(universe in 1..63999)
        require(cid.size == 16)
        val packet = ByteArray(638)
        fun u16(offset: Int, value: Int) {
            packet[offset] = (value ushr 8).toByte()
            packet[offset + 1] = value.toByte()
        }
        fun u32(offset: Int, value: Int) {
            packet[offset] = (value ushr 24).toByte()
            packet[offset + 1] = (value ushr 16).toByte()
            packet[offset + 2] = (value ushr 8).toByte()
            packet[offset + 3] = value.toByte()
        }
        u16(0, 0x0010)
        byteArrayOf(0x41, 0x53, 0x43, 0x2d, 0x45, 0x31, 0x2e, 0x31, 0x37, 0, 0, 0).copyInto(packet, 4)
        u16(16, 0x7000 or (packet.size - 16))
        u32(18, 4)
        cid.copyInto(packet, 22)
        u16(38, 0x7000 or (packet.size - 38))
        u32(40, 2)
        "MobileD Music".toByteArray(Charsets.US_ASCII).copyInto(packet, 44)
        packet[108] = 100
        packet[111] = sequence.toByte()
        // Options is one byte. The universe, DMP and slots begin at 113/115/126.
        u16(113, universe)
        u16(115, 0x7000 or (packet.size - 115))
        packet[117] = 2
        packet[118] = 0xa1.toByte()
        u16(121, 1)
        u16(123, 513)
        dmx.copyInto(packet, 126)
        return packet
    }

    fun palette(color: IntArray): FloatArray = FloatArray(4) { color.getOrElse(it) { 0 }.coerceIn(0, 255).toFloat() }

    fun scalePalette(color: FloatArray, level: Float, brightness: Int): IntArray {
        val scale = level.coerceIn(0f, 1f) * brightness.coerceIn(0, 100) / 100f
        // Saved RGB contains its color brightness; W is independent. Global White
        // belongs only to the procedural Rainbow/Random effects.
        return IntArray(4) { (color[it].coerceIn(0f, 255f) * scale).roundToInt().coerceIn(0, 255) }
    }

    fun blendPalette(from: IntArray, to: IntArray, progress: Float): FloatArray {
        val t = progress.coerceIn(0f, 1f)
        if (t == 0f) return palette(from)
        if (t == 1f) return palette(to)
        val a = hsv(from)
        val b = hsv(to)
        val delta = ((b[0] - a[0] + 540f) % 360f) - 180f
        val hue = ((a[0] + delta * t) % 360f + 360f) % 360f
        val saturation = a[1] + (b[1] - a[1]) * t
        val value = a[2] + (b[2] - a[2]) * t
        val x = 1f - abs((hue / 60f) % 2f - 1f)
        val rgb = when {
            hue < 60f -> floatArrayOf(1f, x, 0f)
            hue < 120f -> floatArrayOf(x, 1f, 0f)
            hue < 180f -> floatArrayOf(0f, 1f, x)
            hue < 240f -> floatArrayOf(0f, x, 1f)
            hue < 300f -> floatArrayOf(x, 0f, 1f)
            else -> floatArrayOf(1f, 0f, x)
        }
        val white = from.getOrElse(3) { 0 }.coerceIn(0, 255) * (1f - t) + to.getOrElse(3) { 0 }.coerceIn(0, 255) * t
        return floatArrayOf(
            (rgb[0] * saturation + 1f - saturation) * value * 255f,
            (rgb[1] * saturation + 1f - saturation) * value * 255f,
            (rgb[2] * saturation + 1f - saturation) * value * 255f,
            white
        )
    }

    fun bpmPixel(from: IntArray, to: IntArray, mode: String, elapsedMs: Long, beatPeriodMs: Long,
                 level: Float, smoothedLevel: Float, brightness: Int, colorCount: Int): IntArray {
        var color = palette(to)
        var outputLevel = smoothedLevel
        val p = max(0L, elapsedMs).toFloat() / max(40f, max(80L, beatPeriodMs).toFloat() / 3f)
        if (colorCount > 1 && mode == "fade") {
            color = blendPalette(from, to, p)
            outputLevel = level
        } else if (colorCount > 1 && mode == "crossfade") {
            val phase = p.coerceIn(0f, 3f)
            when {
                phase < 1f -> { color = palette(from); outputLevel = level * (1f - phase) }
                phase < 2f -> outputLevel = 0f
                else -> outputLevel = level * (phase - 2f)
            }
        }
        return scalePalette(color, outputLevel, brightness)
    }

    private fun hsv(color: IntArray): FloatArray {
        val r = color.getOrElse(0) { 0 }.coerceIn(0, 255) / 255f
        val g = color.getOrElse(1) { 0 }.coerceIn(0, 255) / 255f
        val b = color.getOrElse(2) { 0 }.coerceIn(0, 255) / 255f
        val hi = max(r, max(g, b))
        val delta = hi - min(r, min(g, b))
        val hue = when {
            delta == 0f -> 0f
            hi == r -> 60f * (((g - b) / delta) % 6f)
            hi == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        return floatArrayOf((hue + 360f) % 360f, if (hi == 0f) 0f else delta / hi, hi)
    }
}
