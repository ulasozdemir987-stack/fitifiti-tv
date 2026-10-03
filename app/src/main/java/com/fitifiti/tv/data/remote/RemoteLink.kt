package com.fitifiti.tv.data.remote

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import com.fitifiti.tv.data.xtream.AppJson
import com.fitifiti.tv.data.xtream.str
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Telefon sayfasının ve sinyal sunucusunun adresi */
const val REMOTE_BASE = "https://ozul.com.tr"

data class RemoteInput(val label: String, val value: String, val secret: Boolean)
data class RemotePlayer(val title: String, val line: String?, val image: String?, val timeSec: Long, val durationSec: Long, val playing: Boolean)
data class RemoteLogin(val server: String, val username: String, val password: String, val label: String)

/** Ekranların telefonla paylaştığı durum + telefondan gelen komutlar */
object RemoteBus {
    val screen = MutableStateFlow("app") // login | app | player
    val input = MutableStateFlow<RemoteInput?>(null)
    val player = MutableStateFlow<RemotePlayer?>(null)

    val keys = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val text = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val seek = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val login = MutableSharedFlow<RemoteLogin>(extraBufferCapacity = 2)
    val home = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
}

/**
 * TV ile telefon VPS'teki WebSocket odasında (`/ws/together?room=tv-<kod>`) buluşur; aynı Wi-Fi gerekmez.
 * Kod ve AES anahtarı cihazda bir kez üretilir; anahtar QR'daki adresin # kısmında durur (sunucuya gitmez),
 * telefon Xtream bilgilerini onunla şifreler.
 */
@OptIn(FlowPreview::class)
class RemoteLink(ctx: Context, http: OkHttpClient) {
    private val prefs = ctx.getSharedPreferences("remote", Context.MODE_PRIVATE)
    private val client = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(25, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ws: WebSocket? = null
    private var running = false
    private var retryJob: Job? = null
    private var tries = 0

    private val _peers = MutableStateFlow(0)
    val peers: StateFlow<Int> = _peers
    private val _code = MutableStateFlow(prefs.getString("code", null) ?: newCode())
    val code: StateFlow<String> = _code
    private var key: String = prefs.getString("key", null) ?: newKey()

    val url get() = "$REMOTE_BASE/tv?k=${_code.value}#$key"

    init {
        if (!prefs.contains("code") || !prefs.contains("key")) prefs.edit().putString("code", _code.value).putString("key", key).apply()
        scope.launch { combine(RemoteBus.screen, RemoteBus.input, RemoteBus.player) { _, _, _ -> }.debounce(120).collect { sendState() } }
    }

    private fun newCode(): String {
        val abc = "abcdefghjkmnpqrstuvwxyz23456789"
        val r = SecureRandom()
        return (1..10).map { abc[r.nextInt(abc.length)] }.joinToString("")
    }
    private fun newKey(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }

    /** Eski QR'ın fotoğrafını çeken biri bağlanamasın diye: yeni kod + anahtar */
    fun regenerate() {
        _code.value = newCode(); key = newKey()
        prefs.edit().putString("code", _code.value).putString("key", key).apply()
        if (running) { ws?.close(1000, null); ws = null; connect() }
    }

    fun start() { if (running) return; running = true; tries = 0; connect() }
    fun stop() { running = false; retryJob?.cancel(); ws?.close(1000, null); ws = null; _peers.value = 0 }

    private fun connect() {
        if (!running) return
        val wsUrl = REMOTE_BASE.replaceFirst("https://", "wss://") + "/ws/together?room=tv-${_code.value}"
        ws = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { tries = 0; webSocket.send("""{"type":"hello","name":"tv"}""") }
            override fun onMessage(webSocket: WebSocket, text: String) { if (webSocket === ws) handle(text) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (webSocket === ws) retry() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { if (webSocket === ws) retry() }
        })
    }

    private fun retry() {
        _peers.value = 0
        if (!running) return
        retryJob?.cancel()
        retryJob = scope.launch { delay(minOf(30_000L, 1_000L shl minOf(tries++, 5))); ws = null; connect() }
    }

    private fun handle(text: String) {
        val m = runCatching { AppJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
        when (m.str("type")) {
            "presence" -> setPeers(((m["count"]?.toString()?.toIntOrNull()) ?: 1) - 1)
            "joined" -> setPeers((m["peers"] as? kotlinx.serialization.json.JsonArray)?.size ?: 0)
            "tv-hello" -> sendState()
            "tv-key" -> m.str("key")?.let { k -> if (k == "home") RemoteBus.home.tryEmit(Unit) else RemoteBus.keys.tryEmit(k) }
            "tv-text" -> RemoteBus.text.tryEmit((m.str("value") ?: "").take(200))
            "tv-seek" -> m.str("value")?.toDoubleOrNull()?.let { RemoteBus.seek.tryEmit(it.toLong()) }
            "tv-login" -> decryptLogin(m.str("iv"), m.str("ct"))?.let { RemoteBus.login.tryEmit(it) } ?: loginResult(false, "Bilgiler çözülemedi. QR kodu yeniden okut.")
        }
    }

    private fun setPeers(n: Int) {
        val before = _peers.value
        _peers.value = n.coerceAtLeast(0)
        if (n > before) sendState()
    }

    private fun decryptLogin(iv: String?, ct: String?): RemoteLogin? = runCatching {
        val k = Base64.decode(key, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT)))
        val o = AppJson.parseToJsonElement(String(c.doFinal(Base64.decode(ct, Base64.DEFAULT)), Charsets.UTF_8)) as JsonObject
        RemoteLogin(o.str("server") ?: "", o.str("username") ?: "", o.str("password") ?: "", o.str("label") ?: "")
    }.getOrNull()

    fun loginResult(ok: Boolean, error: String? = null) {
        ws?.send(buildJsonObject { put("type", "tv-login-result"); put("ok", ok); if (error != null) put("error", error) }.toString())
    }

    private fun sendState() {
        val s = ws ?: return
        if (_peers.value <= 0) return
        val input = RemoteBus.input.value
        val p = RemoteBus.player.value
        s.send(buildJsonObject {
            put("type", "tv-state")
            putJsonObject("state") {
                put("screen", RemoteBus.screen.value)
                if (input != null) putJsonObject("input") { put("label", input.label); put("value", if (input.secret) "" else input.value); put("secret", input.secret) }
                if (p != null) putJsonObject("player") {
                    put("title", p.title); p.line?.let { put("line", it) }; p.image?.let { put("image", it) }
                    put("time", p.timeSec); put("duration", p.durationSec); put("playing", p.playing)
                }
            }
        }.toString())
    }
}
