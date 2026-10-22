package com.mobiled.android.ui.controller

import org.junit.Assert.*
import org.junit.Test

class MusicOutputTest {
    private fun u16(packet: ByteArray, offset: Int) = ((packet[offset].toInt() and 255) shl 8) or (packet[offset + 1].toInt() and 255)

    @Test fun sacnContainsStandardHeadersAndAll512Slots() {
        val slots = ByteArray(512) { (it * 13 + 9).toByte() }
        val cid = ByteArray(16) { it.toByte() }
        val packet = MusicOutput.sacnPacket(slots, 32011, 257, cid)
        assertEquals(638, packet.size)
        assertEquals(0x726e, u16(packet, 16))
        assertEquals(0x7258, u16(packet, 38))
        assertEquals(0x720b, u16(packet, 115))
        assertEquals(0, packet[112].toInt())
        assertEquals(32011, u16(packet, 113))
        assertEquals(1, packet[111].toInt())
        assertEquals(2, packet[117].toInt())
        assertEquals(0xa1, packet[118].toInt() and 255)
        assertEquals(0, u16(packet, 119))
        assertEquals(1, u16(packet, 121))
        assertEquals(513, u16(packet, 123))
        assertEquals(0, packet[125].toInt())
        assertArrayEquals(cid, packet.copyOfRange(22, 38))
        assertArrayEquals(slots, packet.copyOfRange(126, 638))
        assertEquals(slots[511], packet[637])
    }

    @Test(expected = IllegalArgumentException::class) fun sacnRejectsShortUniverse() { MusicOutput.sacnPacket(ByteArray(511), 32000, 0, ByteArray(16)) }
    @Test(expected = IllegalArgumentException::class) fun sacnRejectsInvalidUniverse() { MusicOutput.sacnPacket(ByteArray(512), 64000, 0, ByteArray(16)) }

    @Test fun savedBlackStaysBlack() { assertArrayEquals(intArrayOf(0, 0, 0, 0), MusicOutput.scalePalette(MusicOutput.palette(intArrayOf(0, 0, 0, 0)), 1f, 100)) }
    @Test fun savedWhiteDoesNotBecomeSaturatedRed() { assertArrayEquals(intArrayOf(255, 255, 255, 96), MusicOutput.scalePalette(MusicOutput.palette(intArrayOf(255, 255, 255, 96)), 1f, 100)) }
    @Test fun savedWhiteChannelCanWorkWithBlackRgb() { assertArrayEquals(intArrayOf(0, 0, 0, 128), MusicOutput.scalePalette(MusicOutput.palette(intArrayOf(0, 0, 0, 128)), 1f, 100)) }
    @Test fun audioAndMasterBrightnessScaleSavedRgbwOnce() { assertArrayEquals(intArrayOf(16, 8, 4, 12), MusicOutput.scalePalette(MusicOutput.palette(intArrayOf(64, 32, 16, 48)), .5f, 50)) }
    @Test fun zeroMasterBrightnessBlacksOutEveryChannel() { assertArrayEquals(intArrayOf(0, 0, 0, 0), MusicOutput.scalePalette(MusicOutput.palette(intArrayOf(255, 255, 255, 255)), 1f, 0)) }
    @Test fun fadePreservesExactRgbwEndpoints() {
        val from = intArrayOf(18, 36, 54, 72)
        val to = intArrayOf(0, 0, 0, 255)
        assertArrayEquals(MusicOutput.palette(from), MusicOutput.blendPalette(from, to, 0f), .001f)
        assertArrayEquals(MusicOutput.palette(to), MusicOutput.blendPalette(from, to, 1f), .001f)
    }
    @Test fun fadeInterpolatesValueSaturationAndWhite() {
        val blended = MusicOutput.blendPalette(intArrayOf(0, 0, 0, 0), intArrayOf(0, 0, 0, 200), .5f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 100f), blended, .001f)
        val whiteToDimWhite = MusicOutput.blendPalette(intArrayOf(255, 255, 255, 0), intArrayOf(51, 51, 51, 100), .5f)
        assertArrayEquals(floatArrayOf(153f, 153f, 153f, 50f), whiteToDimWhite, .001f)
    }
    @Test fun crossfadeFinishesFadeInAtOneBeatAndStaysLit() {
        val from = intArrayOf(40, 0, 0, 0)
        val to = intArrayOf(0, 80, 0, 64)
        assertArrayEquals(intArrayOf(20, 0, 0, 0), MusicOutput.bpmPixel(from, to, "crossfade", 100, 600, 1f, 1f, 100, 2))
        assertArrayEquals(intArrayOf(0, 0, 0, 0), MusicOutput.bpmPixel(from, to, "crossfade", 300, 600, 1f, 1f, 100, 2))
        assertArrayEquals(intArrayOf(0, 40, 0, 32), MusicOutput.bpmPixel(from, to, "crossfade", 500, 600, 1f, 1f, 100, 2))
        assertArrayEquals(to, MusicOutput.bpmPixel(from, to, "crossfade", 5000, 600, 1f, 1f, 100, 2))
    }
    @Test fun oneColorBpmUsesSmoothedLevelWithoutCyclingToBlack() {
        val color = intArrayOf(80, 40, 20, 60)
        assertArrayEquals(intArrayOf(40, 20, 10, 30), MusicOutput.bpmPixel(color, color, "crossfade", 300, 600, 1f, .5f, 100, 1))
    }
    @Test fun fullStripKeepsOfflineMiddlePositionAndPadsRemainingChannels() {
        // Position 1 still drives its external RGBW fixture when that MobileD
        // goes offline; reachability is intentionally absent from the mapping.
        val frame = listOf(intArrayOf(255, 0, 0, 10), intArrayOf(0, 120, 0, 80), intArrayOf(0, 0, 60, 40))
        val universes = MusicOutput.dmxUniverses(frame, 32010)
        assertEquals(setOf(32010), universes.keys)
        val slots = universes.getValue(32010)
        assertEquals(512, slots.size)
        assertArrayEquals(byteArrayOf(0, 120, 0, 80), slots.copyOfRange(4, 8))
        assertArrayEquals(byteArrayOf(0, 0, 60, 40), slots.copyOfRange(8, 12))
        assertTrue(slots.copyOfRange(12, 512).all { it == 0.toByte() })
    }

    @Test fun virtualPixel127EndsFirstUniverseAnd128StartsNextUniverse() {
        val frame = MutableList(129) { intArrayOf(0, 0, 0, 0) }
        frame[127] = intArrayOf(11, 22, 33, 252)
        frame[128] = intArrayOf(44, 55, 66, 77)
        val universes = MusicOutput.dmxUniverses(frame, 32000)
        assertEquals(listOf(32000, 32001), universes.keys.toList())
        assertArrayEquals(byteArrayOf(11, 22, 33, 252.toByte()), universes.getValue(32000).copyOfRange(508, 512))
        assertArrayEquals(byteArrayOf(44, 55, 66, 77), universes.getValue(32001).copyOfRange(0, 4))
        assertTrue(universes.getValue(32001).copyOfRange(4, 512).all { it == 0.toByte() })
    }

    @Test fun maximumStripKeepsRepeatedColorsAndPixel1023ThroughLastSlot() {
        val repeatedColor = intArrayOf(20, 40, 60, 80)
        val frame = MutableList(1024) { repeatedColor }
        frame[1023] = intArrayOf(101, 151, 201, 255)
        val universes = MusicOutput.dmxUniverses(frame, 32010)
        assertEquals((32010..32017).toList(), universes.keys.toList())
        assertEquals(4096, universes.values.sumOf { it.size })
        universes.values.forEach { assertEquals(512, it.size) }
        assertArrayEquals(byteArrayOf(20, 40, 60, 80), universes.getValue(32010).copyOfRange(0, 4))
        assertArrayEquals(byteArrayOf(20, 40, 60, 80), universes.getValue(32010).copyOfRange(4, 8))
        assertArrayEquals(byteArrayOf(101, 151.toByte(), 201.toByte(), 255.toByte()), universes.getValue(32017).copyOfRange(508, 512))
    }
}
