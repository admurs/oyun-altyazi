package com.admurs.oyunaltyazi.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.graphics.PixelFormat
import android.view.WindowManager
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import com.admurs.oyunaltyazi.core.Region
import org.json.JSONObject
import org.json.JSONTokener

/**
 * DENEME: Xbox'ın resmî tarayıcı Remote Play sayfasını TV'nin içinde açar ve
 * 1) oyun görüntüsünün sayfadan okunabildiğini,
 * 2) kullanıcı HDMI girişine geçince (uygulama arka plandayken) okumanın sürdüğünü ölçer.
 * TV kumandasıyla gezinmek için ekranda bir imleç vardır: yön tuşları imleci oynatır, Tamam tıklar.
 */
class XboxProbeActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var cursor: View
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var cx = 0f
    private var cy = 0f

    // Ölçüm durumu
    private var visible = true
    private var lastSignature: Long? = null
    private var videoInfo = "video yok"
    private var changedFrames = 0
    private var backgroundStart = 0L
    private var backgroundSamples = 0
    private var backgroundChanged = 0
    private var lastResult = ""
    private var windowChanged = 0

    // Telefonsuz altyazı hattı (OCR + çeviri) ve altyazı katmanı
    private lateinit var pipeline: SubtitlePipeline
    private var ownOverlay: SubtitleOverlay? = null
    private val region = Region.DEFAULT

    // Arka plandayken sayfanın taşındığı küçük köşe penceresi
    private var floating = false
    private val overlayManager by lazy { applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.userAgentString = DESKTOP_UA
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    request.grant(request.resources)
                }
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        root.addView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            textSize = 15f
            setPadding(18, 10, 18, 10)
        }
        root.addView(status, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START,
        ))

        val size = (resources.displayMetrics.density * 22).toInt()
        cursor = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(220, 255, 60, 60))
                setStroke(3, Color.WHITE)
            }
        }
        root.addView(cursor, FrameLayout.LayoutParams(size, size))
        setContentView(root)

        root.post {
            cx = root.width / 2f
            cy = root.height / 2f
            placeCursor()
        }
        DiagLog.add(this, "Xbox ekranı açıldı")
        pipeline = SubtitlePipeline(applicationContext).also { it.prepare() }
        ensureOverlay()
        web.loadUrl(START_URL)
        handler.post(probeLoop)
    }

    override fun onResume() {
        super.onResume()
        visible = true
        moveBackFromCorner()
        if (backgroundStart > 0) {
            val seconds = (SystemClock.elapsedRealtime() - backgroundStart) / 1000
            val ok = backgroundSamples >= 5 && backgroundChanged * 2 >= backgroundSamples
            lastResult = "ARKA PLAN TESTİ: ${if (ok) "BAŞARILI ✔" else "BAŞARISIZ ✘"} — $seconds sn arka planda, " +
                "$backgroundSamples ölçümün $backgroundChanged tanesinde görüntü değişti"
            DiagLog.add(this, lastResult)
            backgroundStart = 0
        }
    }

    override fun onPause() {
        // Bilerek web.onPause() çağrılmıyor: arka planda okumanın sürüp sürmediğini ölçüyoruz.
        super.onPause()
        visible = false
        backgroundStart = SystemClock.elapsedRealtime()
        backgroundSamples = 0
        backgroundChanged = 0
        DiagLog.add(this, "Xbox denemesi arka plana geçti ($videoInfo)")
        windowChanged = 0
        moveToCorner()
    }

    /** Uygulama arka plana düşünce sayfayı köşede küçük, hep görünen bir pencereye taşır. */
    private fun moveToCorner() {
        if (floating) return
        if (!Settings.canDrawOverlays(this)) {
            DiagLog.add(this, "Köşe penceresi açılamadı: 'üstte gösterme' izni yok (önce B modunu aç)")
            return
        }
        val width = (resources.displayMetrics.widthPixels * 0.12f).toInt()
        val params = WindowManager.LayoutParams(
            width, width * 9 / 16,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 24
        }
        root.removeView(web)
        try {
            overlayManager.addView(web, params)
            floating = true
            DiagLog.add(this, "Görüntü köşe penceresine taşındı")
        } catch (e: Exception) {
            DiagLog.add(this, "Köşe penceresi açılamadı: ${e.javaClass.simpleName}: ${e.message}")
            root.addView(web, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun moveBackFromCorner() {
        if (!floating) return
        try {
            overlayManager.removeView(web)
        } catch (_: Exception) {
        }
        floating = false
        root.addView(web, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Altyazı katmanı: B modu açıksa onunkini kullan, değilse kendi katmanını aç. */
    private fun ensureOverlay() {
        if (SubtitleOverlay.active != null) return
        if (!Settings.canDrawOverlays(this)) {
            DiagLog.add(this, "Altyazı katmanı açılamadı: 'üstte gösterme' izni yok (önce B modunu aç)")
            return
        }
        ownOverlay = SubtitleOverlay(applicationContext, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            .takeIf { it.attach() }
    }

    override fun onDestroy() {
        handler.removeCallbacks(probeLoop)
        pipeline.close()
        ownOverlay?.detach()
        ownOverlay = null
        if (floating) {
            try {
                overlayManager.removeView(web)
            } catch (_: Exception) {
            }
            floating = false
        }
        DiagLog.add(this, "Xbox ekranı kapandı")
        web.destroy()
        super.onDestroy()
    }

    // ---------- Ölçüm ----------

    private val probeLoop = object : Runnable {
        override fun run() {
            web.evaluateJavascript(captureScript(region)) { raw -> onProbe(raw) }
            handler.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    private fun onProbe(raw: String?) {
        val json = try {
            val inner = JSONTokener(raw ?: "null").nextValue() as? String ?: return
            JSONObject(inner)
        } catch (_: Exception) {
            return
        }
        var changed = false
        if (json.optBoolean("ok")) {
            videoInfo = "video ${json.optInt("w")}x${json.optInt("h")}" + if (json.optBoolean("paused")) " (duraklatılmış)" else ""
            val signature = json.optLong("sig")
            changed = lastSignature != null && signature != lastSignature
            lastSignature = signature
            if (changed) changedFrames++
            // Altyazı bölgesi değiştiyse yeni görüntüyü OCR'a ver, değişmediyse son okumayı tekrar kullan
            val image = json.optString("img")
            if (json.optBoolean("same") || image.isBlank()) pipeline.submitUnchanged() else pipeline.submit(image)
        } else {
            videoInfo = json.optString("err").ifBlank { "video yok (sayfadaki video öğesi: ${json.optInt("videos")})" }
                .let { if (it.startsWith("video yok")) it else "OKUMA HATASI: $it" }
        }
        if (!visible) {
            backgroundSamples++
            if (changed) {
                backgroundChanged++
                windowChanged++
            }
            if (backgroundSamples % SAMPLES_PER_LOG == 0) {
                val seconds = (SystemClock.elapsedRealtime() - backgroundStart) / 1000
                val verdict = if (windowChanged >= 5) "AKIYOR ✔" else "DONMUŞ ✘"
                DiagLog.add(this, "Arka planda $seconds sn — son 10 sn: $windowChanged değişim → $verdict")
                windowChanged = 0
            }
        }
        status.text = buildString {
            append(videoInfo).append(" | değişen kare: ").append(changedFrames).append('\n')
            append(if (changedFrames >= 5) "Oyun görüntüsü okunuyor ✔" else "Oyun görüntüsü bekleniyor…")
            append(" | ").append(pipeline.modelStatus).append(" | işlenen: ").append(pipeline.processed).append('\n')
            append("Okunan: ").append(pipeline.lastOcr.replace('\n', ' ').take(90).ifBlank { "-" }).append('\n')
            append("Çeviri: ").append(pipeline.lastTranslation.take(90).ifBlank { "-" }).append('\n')
            pipeline.lastError?.let { append("Hata: ").append(it).append('\n') }
            if (SubtitleOverlay.active == null) append("UYARI: altyazı katmanı kapalı — önce B modunu aç\n")
            if (lastResult.isNotEmpty()) append(lastResult).append('\n')
            append("Yön: imleç · Tamam: tıkla · Geri: geri · Hızlı ileri/geri sar: sayfayı kaydır")
        }
    }

    // ---------- Kumandayla gezinme ----------

    private fun placeCursor() {
        cursor.translationX = cx - cursor.width / 2f
        cursor.translationY = cy - cursor.height / 2f
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val handled = code in setOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
        )
        if (!handled) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true

        val density = resources.displayMetrics.density
        val step = density * 14 * (1 + minOf(event.repeatCount, 12) / 2f)
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> cx -= step
            KeyEvent.KEYCODE_DPAD_RIGHT -> cx += step
            KeyEvent.KEYCODE_DPAD_UP -> {
                cy -= step
                if (cy < 0) web.scrollBy(0, (-step * 2).toInt())
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                cy += step
                if (cy > root.height) web.scrollBy(0, (step * 2).toInt())
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_CHANNEL_DOWN -> web.scrollBy(0, root.height / 2)
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_CHANNEL_UP -> web.scrollBy(0, -root.height / 2)
            else -> tap()
        }
        cx = cx.coerceIn(0f, root.width.toFloat() - 1)
        cy = cy.coerceIn(0f, root.height.toFloat() - 1)
        placeCursor()
        return true
    }

    @Deprecated("Activity API")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun tap() {
        val time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, cx, cy, 0)
        val up = MotionEvent.obtain(time, time + 60, MotionEvent.ACTION_UP, cx, cy, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        root.dispatchTouchEvent(down)
        root.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    companion object {
        private const val START_URL = "https://www.xbox.com/remoteplay"
        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"

        private const val FRAME_INTERVAL_MS = 500L
        private const val SAMPLES_PER_LOG = 20

        /**
         * Sayfadaki en büyük videodan: tüm karenin küçük imzası (akış donmuş mu?) ve
         * altyazı bölgesinin JPEG'i (bölge önceki kareyle aynıysa gönderilmez).
         */
        private fun captureScript(r: Region): String {
            val g = r.sanitized()
            return """
(function(t,b,l,r){try{
var vs=[].slice.call(document.querySelectorAll('video'));var v=null;
vs.forEach(function(x){if(x.videoWidth>0&&(!v||x.videoWidth*x.videoHeight>v.videoWidth*v.videoHeight))v=x;});
if(!v)return JSON.stringify({ok:false,videos:vs.length});
var W=v.videoWidth,H=v.videoHeight;
var p=window.__oaProbe||(window.__oaProbe=document.createElement('canvas'));p.width=64;p.height=36;
var pg=p.getContext('2d');pg.drawImage(v,0,0,64,36);var pd=pg.getImageData(0,0,64,36).data;
var sig=0;for(var i=0;i<pd.length;i+=8){sig=(sig*31+pd[i]+pd[i+1]*7+pd[i+2]*13)|0;}
var sx=Math.round(W*l/100),sy=Math.round(H*t/100),sw=Math.max(32,Math.round(W*(r-l)/100)),sh=Math.max(32,Math.round(H*(b-t)/100));
var c=window.__oaCrop||(window.__oaCrop=document.createElement('canvas'));c.width=sw;c.height=sh;
var g=c.getContext('2d');g.drawImage(v,sx,sy,sw,sh,0,0,sw,sh);var d=g.getImageData(0,0,sw,sh).data;
var cs=0;for(var j=0;j<d.length;j+=64){cs=(cs*31+d[j]+d[j+1]*7+d[j+2]*13)|0;}
var same=(window.__oaCropSig===cs);window.__oaCropSig=cs;
return JSON.stringify({ok:true,w:W,h:H,paused:v.paused,sig:sig,same:same,img:same?'':c.toDataURL('image/jpeg',0.85).split(',')[1]});
}catch(e){return JSON.stringify({ok:false,err:String(e)});}})(${g.top},${g.bottom},${g.left},${g.right})
"""
        }
    }
}
