package com.admurs.oyunaltyazi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleStabilizerTest {

    private val line = "Arthur: We need to get out of here before dawn."

    @Test
    fun `tek karede gorulen metin gosterilmez, ikinci karede gosterilir`() {
        val s = SubtitleStabilizer()
        assertNull(s.push(line))
        assertEquals(SubtitleEvent.Show(line), s.push(line))
    }

    @Test
    fun `ayni altyazi tekrar gonderilmez`() {
        val s = SubtitleStabilizer()
        s.push(line)
        s.push(line)
        repeat(10) { assertNull(s.push(line)) }
    }

    @Test
    fun `OCR titremesi ayni altyazi sayilir`() {
        val s = SubtitleStabilizer()
        s.push(line)
        s.push(line)
        assertNull(s.push("Arthur: We need to get out of here bef0re dawn"))
        assertNull(s.push("Arthur: We need t0 get out of here before dawn."))
    }

    @Test
    fun `yeni altyazi iki karede kararlilasinca gosterilir`() {
        val s = SubtitleStabilizer()
        s.push(line)
        s.push(line)
        val next = "Dutch: I have a plan, son. Trust me."
        assertNull(s.push(next))
        assertEquals(SubtitleEvent.Show(next), s.push(next))
    }

    @Test
    fun `yazi kaybolunca dorduncu bos karede temizlenir`() {
        val s = SubtitleStabilizer()
        s.push(line)
        s.push(line)
        assertNull(s.push(""))
        assertNull(s.push(""))
        assertNull(s.push(""))
        assertEquals(SubtitleEvent.Clear, s.push(""))
        assertNull(s.push(""))
    }

    @Test
    fun `kisa bosluk altyaziyi silmez ve ayni metin yeniden gonderilmez`() {
        val s = SubtitleStabilizer()
        s.push(line)
        s.push(line)
        s.push("")
        s.push(line)
        assertNull(s.push(line))
    }

    @Test
    fun `temizlendikten sonra ayni cumle yeniden gelirse tekrar gosterilir`() {
        val s = SubtitleStabilizer()
        s.push(line)
        s.push(line)
        repeat(4) { s.push("") }
        s.push(line)
        assertEquals(SubtitleEvent.Show(line), s.push(line))
    }

    @Test
    fun `gurultu temizlenir, cok satir birlestirilir`() {
        assertEquals("", TextCleaner.clean("| . -"))
        assertEquals(
            "I don't know what you're talking about.",
            TextCleaner.clean("I don't know what\n  you're talking about.\n|"),
        )
    }

    @Test
    fun `benzerlik buyuk kucuk harf ve noktalamayi onemsemez`() {
        assertEquals(1.0, TextSimilarity.ratio("Hello, World!", "hello world"), 0.0001)
        assertTrue(TextSimilarity.ratio("completely different", "nothing alike here") < 0.5)
    }
}
