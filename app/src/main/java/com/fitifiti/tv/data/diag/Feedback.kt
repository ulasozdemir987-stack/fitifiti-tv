package com.fitifiti.tv.data.diag

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import com.fitifiti.tv.App
import com.fitifiti.tv.BuildConfig
import com.fitifiti.tv.data.remote.REMOTE_BASE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * "Sorun bildir": Geri tuşuna uzun basınca (ya da Ayarlar'dan) o anki ekranın görüntüsü alınır, kullanıcı kategori/not
 * seçip gönderir → ozul.com.tr/api/tv-feedback (görsel + son olaylar). Geliştirici VPS'teki kutu ajanından okur.
 * Görüntü PixelCopy ile (oynayan video/fragman da çıkar); Android 8 öncesinde yalnız arayüz çizilir.
 */
object Feedback {
    class Capture(val jpeg: ByteArray?, val preview: Bitmap?, val events: String, val screen: String)
    val pending = MutableStateFlow<Capture?>(null)

    fun capture(activity: Activity, screen: String) {
        val events = Diag.snapshot() + "\n" + Diag.memory()
        val view = activity.window.decorView
        val w = view.width; val h = view.height
        if (w <= 0 || h <= 0) { pending.value = Capture(null, null, events, screen); return }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        fun done(ok: Boolean) {
            if (!ok) runCatching { view.draw(android.graphics.Canvas(bmp)) }
            val small = Bitmap.createScaledBitmap(bmp, 960, (960f * h / w).toInt(), true)
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 78, out)
            pending.value = Capture(out.toByteArray(), small, events, screen)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching { PixelCopy.request(activity.window, bmp, { r -> done(r == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper())) }
                .onFailure { done(false) }
        } else done(false)
    }

    suspend fun send(c: Capture, category: String, note: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val app = App.instance
            val body = buildJsonObject {
                put("version", BuildConfig.VERSION_NAME); put("device", Diag.device(app)); put("category", category)
                put("note", note.take(500)); put("events", Diag.redact("ekran: ${c.screen}\n" + c.events).take(20_000))
                c.jpeg?.let { put("image", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)) }
            }.toString().toRequestBody("application/json".toMediaType())
            val client = app.http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url("$REMOTE_BASE/api/tv-feedback").post(body).build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
