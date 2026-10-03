package com.fitifiti.tv.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.fitifiti.tv.App
import com.fitifiti.tv.BuildConfig
import com.fitifiti.tv.data.diag.Diag
import com.fitifiti.tv.data.remote.REMOTE_BASE
import com.fitifiti.tv.data.xtream.AppJson
import com.fitifiti.tv.data.xtream.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import java.io.File

/**
 * Uygulama içi güncelleme: ozul.com.tr/tv-version → yeni sürüm varsa "Güncelleme var" penceresi →
 * "Kur": APK ozul.com.tr/tv.apk'dan uygulamanın önbelleğine indirilir (boyut doğrulanır) → PackageInstaller oturumu →
 * Android'in kurulum onayı (UpdateReceiver açar). İlk seferde TV "bilinmeyen kaynak" izni isteyebilir.
 */
data class UpdateInfo(val code: Int, val name: String, val size: Long, val notes: String?, val url: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Failed(val info: UpdateInfo?, val message: String) : UpdateState
}

object Updater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    /** Açılışta gösterilecek pencere (Ayarlar'daki kontrol pencere açmaz, satırda yazar) */
    val prompt = MutableStateFlow<UpdateInfo?>(null)
    private var checkedAt = 0L
    private var dismissed = -1

    /** Açılışta: en fazla 30 dk'da bir. `manual` = Ayarlar'dan (her zaman sorar, pencere açmaz). */
    fun check(manual: Boolean = false) {
        if (!manual && System.currentTimeMillis() - checkedAt < 30 * 60_000) return
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        checkedAt = System.currentTimeMillis()
        if (manual) _state.value = UpdateState.Checking
        scope.launch {
            val info = runCatching { fetchInfo() }.onFailure { Diag.log("güncelleme kontrolü: ${it.message}") }.getOrNull()
            when {
                info == null -> if (manual) _state.value = UpdateState.Failed(null, "Sunucuya ulaşılamadı")
                info.code > BuildConfig.VERSION_CODE -> {
                    _state.value = UpdateState.Available(info)
                    if (!manual && dismissed != info.code) prompt.value = info
                }
                else -> _state.value = UpdateState.UpToDate
            }
        }
    }

    fun dismiss() { prompt.value?.let { dismissed = it.code }; prompt.value = null }

    private fun fetchInfo(): UpdateInfo? {
        val app = App.instance
        app.http.newCall(Request.Builder().url("$REMOTE_BASE/tv-version").build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val o = AppJson.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject ?: return null
            val code = o["versionCode"]?.jsonPrimitive?.intOrNull ?: return null
            return UpdateInfo(code, o.str("versionName") ?: "$code", o["size"]?.jsonPrimitive?.longOrNull ?: 0, o.str("notes"), o.str("url") ?: "$REMOTE_BASE/tv.apk")
        }
    }

    /** İndir + kur. Kurulum onayı Android'in penceresinde; onaylanınca uygulama kapanıp yenisi açılabilir. */
    fun install(ctx: Context, info: UpdateInfo) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        // Android 8+: bu uygulamaya "bilinmeyen uygulamaları yükle" izni verilmemişse önce ayar ekranı
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            val opened = runCatching {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) { _state.value = UpdateState.NeedsPermission(info); return }
            // bazı TV'lerde bu ayar ekranı yok: yine de dene, sistem kendi uyarısını gösterir
        }
        _state.value = UpdateState.Downloading(info, 0f)
        scope.launch {
            try {
                val file = File(ctx.cacheDir, "update.apk")
                download(info, file)
                _state.value = UpdateState.Installing(info)
                commit(ctx, file)
            } catch (e: Exception) {
                Diag.log("güncelleme hatası: ${e.message}")
                _state.value = UpdateState.Failed(info, e.message ?: "İndirilemedi")
            }
        }
    }

    private fun download(info: UpdateInfo, file: File) {
        val app = App.instance
        val client = app.http.newBuilder().readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url(info.url).build()).execute().use { r ->
            if (!r.isSuccessful) error("İndirme HTTP ${r.code}")
            val body = r.body ?: error("Boş yanıt")
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            var read = 0L
            var lastPct = -1
            file.outputStream().use { out ->
                body.byteStream().use { inp ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buf); if (n < 0) break
                        out.write(buf, 0, n); read += n
                        val pct = if (total > 0) (read * 100 / total).toInt() else 0
                        if (pct != lastPct) { lastPct = pct; _state.value = UpdateState.Downloading(info, pct / 100f) }
                    }
                }
            }
            if (info.size > 0 && read != info.size) error("Eksik indirme ($read / ${info.size})")
        }
    }

    private fun commit(ctx: Context, file: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(ctx.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("fitifiti.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; session.fsync(out) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateReceiver::class.java), flags)
            session.commit(pi.intentSender)
        }
    }

    internal fun onResult(ctx: Context, intent: Intent) {
        val info = (_state.value as? UpdateState.Installing)?.info
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { ctx.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.UpToDate
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Kurulum tamamlanmadı ($status)"
                Diag.log("güncelleme kurulumu: $msg")
                _state.value = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) (info?.let { UpdateState.Available(it) } ?: UpdateState.Idle) else UpdateState.Failed(info, msg)
            }
        }
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onResult(context, intent)
}
