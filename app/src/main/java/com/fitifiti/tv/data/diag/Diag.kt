package com.fitifiti.tv.data.diag

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.fitifiti.tv.App
import com.fitifiti.tv.BuildConfig
import com.fitifiti.tv.data.remote.REMOTE_BASE
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Tanılama: son olaylar (oynatıcı çözücüleri, biçimler, hatalar, bellek) halkada tutulur; çökme / donma / oynatıcı
 * hatasında rapor ozul.com.tr/api/tv-report'a gider (TV'de logcat'e erişim zor). Adreslerdeki hesap bilgisi atılır.
 */
object Diag {
    private val ring = ArrayDeque<String>()
    /** en üstteki ekran (Sorun bildir için) */
    @Volatile var lastScreen = "?"
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(s: String) = synchronized(ring) {
        val r = redact(s)
        android.util.Log.i("FitiDiag", r) // kutu ajanıyla okunur: {"logcat":"FitiDiag"}
        ring.addLast("${clock.format(Date())} $r")
        while (ring.size > 80) ring.removeFirst()
    }
    fun snapshot(): String = synchronized(ring) { ring.joinToString("\n") }

    fun redact(s: String) = s.replace(Regex("/(movie|series|live)/[^/\\s]+/[^/\\s]+/"), "/$1/***/***/")
        .replace(Regex("(username|password)=[^&\\s]+"), "$1=***")

    fun device(ctx: Context): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · bellek ${am.memoryClass}/${am.largeMemoryClass} MB"
    }

    fun memory(): String {
        val r = Runtime.getRuntime()
        return "heap ${(r.totalMemory() - r.freeMemory()) / 1048576}/${r.maxMemory() / 1048576} MB"
    }

    /** Raporu arka planda gönder (başarısızsa sessiz) */
    fun send(kind: String, text: String) {
        val app = runCatching { App.instance }.getOrNull() ?: return
        thread(name = "diag-send", isDaemon = true) { sendNow(app, kind, text) }
    }

    fun sendNow(app: App, kind: String, text: String): Boolean = runCatching {
        val body = buildJsonObject {
            put("kind", kind); put("version", BuildConfig.VERSION_NAME); put("device", device(app)); put("text", redact(text).take(30_000))
        }.toString().toRequestBody("application/json".toMediaType())
        val client = app.http.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url("$REMOTE_BASE/api/tv-report").post(body).build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}

/**
 * Donma bekçisi: ana iş parçacığı 4 sn'den uzun yanıt vermezse o anki yığını (neyin takıldığını) kaydeder ve gönderir.
 * Android uygulamayı "yanıt vermiyor" diye kapatırsa da rapor önceden gitmiş olur.
 */
class FreezeWatchdog : Thread("freeze-watchdog") {
    @Volatile private var tick = 0L
    private val main = Handler(Looper.getMainLooper())

    init { isDaemon = true }

    override fun run() {
        var reported = false
        while (true) {
            val sent = System.nanoTime()
            main.post { tick = sent }
            try { sleep(4_000) } catch (_: InterruptedException) { return }
            if (tick == sent) { reported = false; continue }
            if (reported) continue
            reported = true
            val stack = Looper.getMainLooper().thread.stackTrace.take(40).joinToString("\n") { "  at $it" }
            Diag.log("DONMA: ana iş parçacığı 4 sn+ yanıt vermiyor · ${Diag.memory()}")
            Diag.send("freeze", "Ana iş parçacığı yığını:\n$stack\n\nSon olaylar:\n${Diag.snapshot()}")
        }
    }
}
