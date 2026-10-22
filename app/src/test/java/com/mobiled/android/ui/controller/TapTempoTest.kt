package com.mobiled.android.ui.controller

import org.junit.Assert.*
import org.junit.Test

class TapTempoTest {
    @Test fun maps120BpmToTheSameSpeedsAsThePc() {
        val colorChange = TapTempo(101)
        val theater = TapTempo(105)
        assertNull(colorChange.tap(1000L))
        assertNull(theater.tap(1000L))
        val colorReading = colorChange.tap(1500L)!!
        val theaterReading = theater.tap(1500L)!!
        assertEquals(120.0, colorReading.bpm, 0.0001)
        assertEquals(120.0, theaterReading.bpm, 0.0001)
        assertEquals(57, colorReading.speed)
        assertEquals(25, theaterReading.speed)
    }

    @Test fun keepsOnlyTheLatestFourIntervals() {
        val tempo = TapTempo(101)
        listOf(0L, 1000L, 1500L, 2000L, 2500L).forEach { tempo.tap(it) }
        val reading = tempo.tap(3000L)!!
        assertEquals(120.0, reading.bpm, 0.0001)
        assertEquals(57, reading.speed)
    }

    @Test fun startsANewSeriesAfterMoreThanTwoSeconds() {
        val tempo = TapTempo(105)
        assertNull(tempo.tap(0L))
        assertEquals(120.0, tempo.tap(500L)!!.bpm, 0.0001)
        assertNull(tempo.tap(2501L))
        val reading = tempo.tap(3001L)!!
        assertEquals(120.0, reading.bpm, 0.0001)
        assertEquals(25, reading.speed)
    }

    @Test fun clampsFastBpmAndSlowSpeedWithoutLosingBoundaryTaps() {
        for ((effectId, fastSpeed) in listOf(101 to 86, 105 to 57)) {
            val fast = TapTempo(effectId)
            fast.tap(0L)
            val reading = fast.tap(100L)!!
            assertEquals(200.0, reading.bpm, 0.0001)
            assertEquals(fastSpeed, reading.speed)

            val slow = TapTempo(effectId)
            slow.tap(0L)
            val slowReading = slow.tap(2000L)!!
            assertEquals(30.0, slowReading.bpm, 0.0001)
            assertEquals(0, slowReading.speed)
        }
    }
}
