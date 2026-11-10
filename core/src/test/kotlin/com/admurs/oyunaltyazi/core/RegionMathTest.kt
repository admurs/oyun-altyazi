package com.admurs.oyunaltyazi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionMathTest {

    @Test
    fun `ust ve alt ayni yerdeyse bolge en az yuzde 5 yukseklige acilir`() {
        // Kullanıcının tabletinde görülen ayar: üst 75, alt 75, sol 73, sağ 75
        val fixed = Region(top = 75, bottom = 75, left = 73, right = 75).sanitized()
        assertEquals(Region(75, 80, 73, 83), fixed)
    }

    @Test
    fun `ters cevrilmis kenarlar duzeltilir`() {
        val fixed = Region(top = 90, bottom = 20, left = 80, right = 10).sanitized()
        assertTrue(fixed.bottom > fixed.top)
        assertTrue(fixed.right > fixed.left)
    }

    @Test
    fun `tabletteki hatali ayar bile ML Kit sinirini gecer`() {
        val rect = RegionMath.cropRect(1340, 800, Region(75, 75, 73, 75))
        assertTrue("genişlik ${rect.width}", rect.width >= 32)
        assertTrue("yükseklik ${rect.height}", rect.height >= 32)
        assertTrue(rect.right <= 1340 && rect.bottom <= 800 && rect.left >= 0 && rect.top >= 0)
    }

    @Test
    fun `alt kenara dayali kucuk bolge goruntunun icinde kalir`() {
        val rect = RegionMath.cropRect(1340, 800, Region(99, 100, 0, 100))
        assertEquals(800, rect.bottom)
        assertTrue(rect.height >= 32)
    }

    @Test
    fun `varsayilan bolge beklenen piksellere denk gelir`() {
        val rect = RegionMath.cropRect(1340, 800, Region.DEFAULT)
        assertEquals(PixelRect(left = 134, top = 576, width = 1072, height = 192), rect)
    }
}
