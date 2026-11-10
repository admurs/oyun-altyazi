package com.admurs.oyunaltyazi.core

/** Altyazı bölgesi, ekranın yüzdesi olarak (0..100). */
data class Region(val top: Int, val bottom: Int, val left: Int, val right: Int) {

    /** Kaydırıcılar birbirinin üstüne geçse bile kullanılabilir bir bölge döndürür. */
    fun sanitized(): Region {
        val t = top.coerceIn(0, 95)
        val b = bottom.coerceIn(t + 5, 100)
        val l = left.coerceIn(0, 90)
        val r = right.coerceIn(l + 10, 100)
        return Region(t, b, l, r)
    }

    companion object {
        val DEFAULT = Region(top = 72, bottom = 96, left = 10, right = 90)
    }
}

data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right get() = left + width
    val bottom get() = top + height
}

object RegionMath {
    /**
     * Bölgeyi piksel dikdörtgenine çevirir. Sonuç her zaman görüntünün içinde kalır ve
     * (görüntü izin verdiği sürece) her iki kenarı da en az [minSize] pikseldir; ML Kit 32'den küçüğünü reddeder.
     */
    fun cropRect(imageWidth: Int, imageHeight: Int, region: Region, minSize: Int = 32): PixelRect {
        val r = region.sanitized()
        val (left, right) = fit(imageWidth * r.left / 100, imageWidth * r.right / 100, imageWidth, minSize)
        val (top, bottom) = fit(imageHeight * r.top / 100, imageHeight * r.bottom / 100, imageHeight, minSize)
        return PixelRect(left, top, right - left, bottom - top)
    }

    private fun fit(start: Int, end: Int, limit: Int, minSize: Int): Pair<Int, Int> {
        val a = start.coerceIn(0, limit)
        val b = end.coerceIn(a, limit)
        val need = minOf(minSize, limit)
        if (b - a >= need) return a to b
        val newStart = ((a + b) / 2 - need / 2).coerceIn(0, limit - need)
        return newStart to newStart + need
    }
}
