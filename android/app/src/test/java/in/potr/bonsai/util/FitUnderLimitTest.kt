package `in`.potr.bonsai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitUnderLimitTest {
    /** Fake encoder: size grows with side² and quality, roughly like a real codec. */
    private fun fake(perPixelAtQ100: Double): (Int, Int) -> ByteArray = { side, q ->
        ByteArray((side.toDouble() * side * 0.75 * perPixelAtQ100 * q / 100).toInt())
    }

    @Test
    fun picksHighestQualityThatFitsAtFullSize() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val out = fitUnderLimit(PHOTO_SIDES) { s, q -> calls += s to q; fake(0.15)(s, q) }
        assertTrue(out.size <= MAX_PHOTO_BYTES)
        assertTrue("stays at 1200 px", calls.all { it.first == 1200 })
        // One quality step higher would not fit.
        val bestQ = calls.filter { fake(0.15)(it.first, it.second).size <= MAX_PHOTO_BYTES }.maxOf { it.second }
        assertTrue(fake(0.15)(1200, bestQ + 1).size > MAX_PHOTO_BYTES || bestQ == 85)
        assertTrue("binary search, not a linear scan", calls.size <= 6)
    }

    @Test
    fun dropsToSmallerSizeForDetailedPhotos() {
        val sides = mutableSetOf<Int>()
        val out = fitUnderLimit(PHOTO_SIDES) { s, q -> sides += s; fake(0.6)(s, q) }
        assertTrue(out.size <= MAX_PHOTO_BYTES)
        assertTrue(sides.contains(1000) || sides.contains(800))
    }

    @Test
    fun simplePhotoKeepsMaxQuality() {
        val out = fitUnderLimit(PHOTO_SIDES) { s, q -> fake(0.01)(s, q) }
        assertEquals(fake(0.01)(1200, 85).size, out.size)
    }

    @Test(expected = IllegalStateException::class)
    fun failsClearlyWhenNothingFits() {
        fitUnderLimit(PHOTO_SIDES) { _, _ -> ByteArray(MAX_PHOTO_BYTES + 1) }
    }
}
