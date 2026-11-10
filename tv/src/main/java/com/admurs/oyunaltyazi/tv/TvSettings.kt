package com.admurs.oyunaltyazi.tv

import android.content.Context
import com.admurs.oyunaltyazi.core.Region

/** TV'deki kalıcı ayarlar: oyuna göre ayarlanan altyazı bölgesi. */
object TvSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("tv_settings", Context.MODE_PRIVATE)

    fun region(context: Context): Region = prefs(context).let {
        Region(
            top = it.getInt("top", Region.DEFAULT.top),
            bottom = it.getInt("bottom", Region.DEFAULT.bottom),
            left = it.getInt("left", Region.DEFAULT.left),
            right = it.getInt("right", Region.DEFAULT.right),
        ).sanitized()
    }

    fun setRegion(context: Context, region: Region) {
        val r = region.sanitized()
        prefs(context).edit()
            .putInt("top", r.top).putInt("bottom", r.bottom)
            .putInt("left", r.left).putInt("right", r.right)
            .apply()
    }
}
