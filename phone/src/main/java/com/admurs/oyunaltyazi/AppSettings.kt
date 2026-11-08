package com.admurs.oyunaltyazi

import android.content.Context
import android.content.SharedPreferences

/** Kalıcı ayarlar. Altyazı bölgesi ekranın yüzdesi olarak tutulur. */
object AppSettings {
    const val TV_PORT = 8788
    const val FRAME_INTERVAL_MS = 350L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun tvHost(context: Context): String? = prefs(context).getString("tvHost", null)
    fun setTvHost(context: Context, host: String) = prefs(context).edit().putString("tvHost", host).apply()

    data class Region(val top: Int, val bottom: Int, val left: Int, val right: Int)

    fun region(context: Context): Region = prefs(context).let {
        Region(
            top = it.getInt("top", 72),
            bottom = it.getInt("bottom", 96),
            left = it.getInt("left", 10),
            right = it.getInt("right", 90),
        )
    }

    fun setRegionValue(context: Context, key: String, value: Int) =
        prefs(context).edit().putInt(key, value).apply()

    fun fontSize(context: Context): Int = prefs(context).getInt("fontSize", 46)
    fun setFontSize(context: Context, value: Int) = prefs(context).edit().putInt("fontSize", value).apply()
}
