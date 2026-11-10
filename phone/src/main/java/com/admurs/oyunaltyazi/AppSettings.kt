package com.admurs.oyunaltyazi

import android.content.Context
import android.content.SharedPreferences
import com.admurs.oyunaltyazi.core.Region

/** Kalıcı ayarlar. Altyazı bölgesi ekranın yüzdesi olarak tutulur. */
object AppSettings {
    const val TV_PORT = 8788
    const val FRAME_INTERVAL_MS = 350L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun tvHost(context: Context): String? = prefs(context).getString("tvHost", null)
    fun setTvHost(context: Context, host: String) = prefs(context).edit().putString("tvHost", host).apply()

    /** Kaydedilmiş ham değerler; kullanılırken [Region.sanitized] ile düzeltilir. */
    fun region(context: Context): Region = prefs(context).let {
        Region(
            top = it.getInt("top", Region.DEFAULT.top),
            bottom = it.getInt("bottom", Region.DEFAULT.bottom),
            left = it.getInt("left", Region.DEFAULT.left),
            right = it.getInt("right", Region.DEFAULT.right),
        )
    }

    fun setRegionValue(context: Context, key: String, value: Int) =
        prefs(context).edit().putInt(key, value).apply()

    fun resetRegion(context: Context) = prefs(context).edit()
        .putInt("top", Region.DEFAULT.top)
        .putInt("bottom", Region.DEFAULT.bottom)
        .putInt("left", Region.DEFAULT.left)
        .putInt("right", Region.DEFAULT.right)
        .apply()

    fun fontSize(context: Context): Int = prefs(context).getInt("fontSize", 46)
    fun setFontSize(context: Context, value: Int) = prefs(context).edit().putInt("fontSize", value).apply()
}
