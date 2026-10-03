package com.fitifiti.tv.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val tmdbKey: String = "",
    val autoNext: Boolean = true,
    val skipIntro: Boolean = true,
    val subtitleLang: String = "tr",
    val subtitleScale: Float = 1f,
    val lastProfileId: Long = -1,
    val trailerAutoplay: Boolean = true,
    val trailerSound: Boolean = true,
)

/** Cihaza özel ayarlar (sitedeki lib/settings.ts karşılığı) */
class SettingsStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _s = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _s
    val value get() = _s.value

    private fun read() = AppSettings(
        tmdbKey = p.getString("tmdbKey", "") ?: "",
        autoNext = p.getBoolean("autoNext", true),
        skipIntro = p.getBoolean("skipIntro", true),
        subtitleLang = p.getString("subtitleLang", "tr") ?: "tr",
        subtitleScale = p.getFloat("subtitleScale", 1f),
        lastProfileId = p.getLong("lastProfileId", -1),
        trailerAutoplay = p.getBoolean("trailerAutoplay", true),
        trailerSound = p.getBoolean("trailerSound", true),
    )

    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_s.value)
        p.edit().putString("tmdbKey", n.tmdbKey).putBoolean("autoNext", n.autoNext).putBoolean("skipIntro", n.skipIntro)
            .putString("subtitleLang", n.subtitleLang).putFloat("subtitleScale", n.subtitleScale).putLong("lastProfileId", n.lastProfileId)
            .putBoolean("trailerAutoplay", n.trailerAutoplay).putBoolean("trailerSound", n.trailerSound).apply()
        _s.value = n
    }

    /** Sürüm seçimi: içerik başına + genel tercih (sitedeki lib/variant-pref.ts) */
    fun variantFor(key: String): String? = p.getString("variant:$key", null)
    fun variantPref(): String? = p.getString("variantPref", null)
    fun chooseVariant(key: String, label: String) { p.edit().putString("variant:$key", label).putString("variantPref", label).apply() }
}
