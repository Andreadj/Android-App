package com.mobiled.android.ui.controller

import kotlin.math.roundToInt

/** Tap estimation shared by the Matrix control; no transport or Android APIs. */
class TapTempo(private val effectId: Int) {
    data class Reading(val bpm: Double, val speed: Int)

    private val taps = ArrayList<Long>(5)

    init {
        require(effectId == 101 || effectId == 105)
    }

    fun tap(nowMs: Long): Reading? {
        if (taps.isNotEmpty() && nowMs - taps.last() > 2000L) taps.clear()
        taps.add(nowMs)
        if (taps.size > 5) taps.removeAt(0)
        if (taps.size < 2) return null

        val averageInterval = taps.zipWithNext { first, second -> second - first }.average()
        if (averageInterval <= 0.0) return null
        val bpm = (60000.0 / averageInterval).coerceIn(1.0, 200.0)
        val beatMs = 60000.0 / bpm
        val speed = when (effectId) {
            101 -> ((900.0 - beatMs) / 7.0).roundToInt()
            else -> ((650.0 - beatMs) / 6.1).roundToInt()
        }.coerceIn(0, 100)
        return Reading(bpm, speed)
    }
}
