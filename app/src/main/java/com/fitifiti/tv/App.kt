package com.fitifiti.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.fitifiti.tv.data.catalog.CatalogRepository
import com.fitifiti.tv.data.catalog.UserData
import com.fitifiti.tv.data.local.AccountStore
import com.fitifiti.tv.data.local.AppDb
import com.fitifiti.tv.data.local.SettingsStore
import com.fitifiti.tv.data.tmdb.ArtRepository
import com.fitifiti.tv.data.xtream.Account
import com.fitifiti.tv.data.xtream.BROWSER_UA
import com.fitifiti.tv.data.xtream.XtreamClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Basit bağımlılık kabı (Hilt yerine — daha az derleme yükü) */
class App : Application(), ImageLoaderFactory {
    lateinit var http: OkHttpClient
    lateinit var accounts: AccountStore
    lateinit var settings: SettingsStore
    lateinit var db: AppDb
    lateinit var catalog: CatalogRepository
    lateinit var user: UserData
    lateinit var art: ArtRepository
    lateinit var remote: com.fitifiti.tv.data.remote.RemoteLink

    fun client(a: Account = accounts.active!!) = XtreamClient(http, a)
    fun crashFile() = java.io.File(filesDir, "last-crash.txt")

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Çökme raporu: bir sonraki açılışta ekranda gösterilir (TV'de logcat'e erişim zor)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { crashFile().writeText("sürüm ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n" + e.stackTraceToString().take(8000)) }
            prev?.uncaughtException(t, e)
        }
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true).followSslRedirects(true)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", BROWSER_UA).build()) }
            .build()
        accounts = AccountStore(this)
        settings = SettingsStore(this)
        db = AppDb.create(this)
        catalog = CatalogRepository(this) { XtreamClient(http, it) }
        user = UserData(db)
        art = ArtRepository(http) { settings.value.tmdbKey }
        remote = com.fitifiti.tv.data.remote.RemoteLink(this, http)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(http)
        .components { add(SvgDecoder.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(256L * 1024 * 1024).build() }
        .crossfade(true)
        .build()

    companion object { lateinit var instance: App }
}
