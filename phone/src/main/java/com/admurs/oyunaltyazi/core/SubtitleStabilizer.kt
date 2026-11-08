package com.admurs.oyunaltyazi.core

/** Stabilizatörün TV'ye iletilmesini istediği olay. */
sealed interface SubtitleEvent {
    data class Show(val text: String) : SubtitleEvent
    data object Clear : SubtitleEvent
}

/** OCR çıktısını altyazı metnine çevirir: satırları birleştirir, gürültüyü atar. */
object TextCleaner {
    fun clean(raw: String): String {
        val lines = raw.lines()
            .map { it.trim() }
            .filter { line -> line.count { it.isLetter() } >= 2 }
        val joined = lines.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        return if (joined.count { it.isLetter() } >= 3) joined else ""
    }
}

/** İki metnin OCR titremesine dayanıklı benzerliği (0..1): yalnız harf ve rakamlara bakar. */
object TextSimilarity {
    fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    fun ratio(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() && y.isEmpty()) return 1.0
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val distance = levenshtein(x, y)
        return 1.0 - distance.toDouble() / maxOf(x.length, y.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}

/**
 * Kare kare gelen OCR metnini kararlı altyazı olaylarına çevirir.
 * - Aynı metin [stableFrames] kare boyunca görülmeden gösterilmez (geçiş karelerindeki yarım yazılar elenir).
 * - Ekrandaki altyazıyla aynı (benzer) metin tekrar gönderilmez.
 * - [clearAfterEmpty] kare boyunca yazı görülmezse altyazı kaldırılır.
 */
class SubtitleStabilizer(
    private val stableFrames: Int = 2,
    private val clearAfterEmpty: Int = 4,
    private val sameThreshold: Double = 0.85,
) {
    private var candidate = ""
    private var candidateCount = 0
    private var shown: String? = null
    private var emptyCount = 0

    fun push(rawOcr: String): SubtitleEvent? {
        val text = TextCleaner.clean(rawOcr)
        if (text.isEmpty()) {
            candidate = ""
            candidateCount = 0
            emptyCount++
            if (shown != null && emptyCount >= clearAfterEmpty) {
                shown = null
                return SubtitleEvent.Clear
            }
            return null
        }
        emptyCount = 0

        if (candidate.isNotEmpty() && TextSimilarity.ratio(text, candidate) >= sameThreshold) {
            candidateCount++
            candidate = text // en güncel okumayı tut; harf harf beliren yazıda en uzun hali gelir
        } else {
            candidate = text
            candidateCount = 1
        }

        if (candidateCount < stableFrames) return null
        val current = shown
        if (current != null && TextSimilarity.ratio(candidate, current) >= sameThreshold) return null
        shown = candidate
        return SubtitleEvent.Show(candidate)
    }

    fun reset() {
        candidate = ""
        candidateCount = 0
        shown = null
        emptyCount = 0
    }
}
