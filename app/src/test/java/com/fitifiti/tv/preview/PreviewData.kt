package com.fitifiti.tv.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.fitifiti.tv.App
import com.fitifiti.tv.data.catalog.Catalog
import com.fitifiti.tv.data.local.ProfileEntity
import com.fitifiti.tv.data.local.ProgressEntity
import com.fitifiti.tv.data.tmdb.Art
import com.fitifiti.tv.data.tmdb.CastMember
import com.fitifiti.tv.data.tmdb.Critics
import com.fitifiti.tv.data.tmdb.EpisodeArt
import com.fitifiti.tv.data.xtream.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

/**
 * Ekran önizlemeleri için örnek veri: ağa çıkılmaz. Görseller "fake:" adresleridir; Coil'e eklenen yakalayıcı her
 * adres için o anda renkli bir görsel üretir (sahne: geçişli manzara, afiş: dikey, yüz: yuvarlak portre, logo: yazı).
 */
object PreviewData {
    val genres = listOf("Dram", "Aksiyon, Macera", "Komedi", "Bilim-Kurgu", "Gerilim", "Animasyon", "Romantik", "Suç, Dram")
    val movieNames = listOf("Zaferin Rengi", "Kayıp Şehir", "Gece Yarısı Treni", "Son Vardiya", "Kuzey Rüzgarı", "Mavi Saat",
        "Sessiz Liman", "Yıldız Tozu", "Demir Kapı", "Uzun Yol", "Kırmızı Balon", "Gölgeler")
    val seriesNames = listOf("Slow Horses", "MobLand", "Şahsiyet", "Kuşlar da Uçar", "The Morning Show", "Bir Zamanlar", "Silo", "Kardeşler")

    val movies = movieNames.mapIndexed { i, n ->
        Movie(id = 1000 + i, name = n, icon = "fake:poster:m$i", categoryId = "1", ext = "mp4", rating = 6.0 + (i % 4) * 0.7, added = 1_700_000_000L + i * 1000,
            year = "${2018 + i % 8}", genre = genres[i % genres.size], plot = "Örnek özet: $n, bir ailenin geçmişiyle yüzleşmesini ve beklenmedik bir yolculuğa çıkmasını anlatıyor. İkinci cümle uzunluğu sınamak için.",
            tmdb = "${500 + i}", runtimeMin = 95 + i * 7)
    }
    val series = seriesNames.mapIndexed { i, n ->
        Series(id = 2000 + i, name = n, cover = "fake:poster:s$i", categoryId = "2", rating = 7.0 + (i % 3) * 0.6, added = 1_700_000_000L + i * 1000,
            year = "${2016 + i % 9}", genre = genres[(i + 2) % genres.size], plot = "Örnek dizi özeti: $n. Sezonlar boyunca süren bir sır ve onu çözmeye çalışan insanlar.", backdrop = "fake:backdrop:s$i")
    }
    val channels = (0 until 16).map { Channel(id = 3000 + it, name = listOf("TRT 1", "ATV", "Show TV", "Star", "NOW", "TV8", "Kanal D", "beIN Sports")[it % 8] + if (it >= 8) " HD" else "", icon = null, categoryId = "3", num = it + 1) }
    val catalog = Catalog(movies, series, channels, listOf(Category("1", "Yerli Filmler")), listOf(Category("2", "Diziler")), listOf(Category("3", "Ulusal")), System.currentTimeMillis())

    fun episodes(s: Series) = (1..2).associateWith { season ->
        (1..8).map { n -> Episode(id = "${s.id}$season$n", season = season, num = n, title = "${s.name} - S0${season}E0$n - Bölüm adı $n", ext = "mkv",
            plot = "Bölüm $n özeti: işler karışır.", image = "fake:backdrop:e${s.id}$season$n", durationSecs = 2700 + n * 60) }
    }

    val cast = listOf("Kubilay Aka" to "Galip Bey", "Timuçin Esen" to "Topkapılı Cahit", "Nejat İşler" to "Sabri Toprak", "Gülper Özdemir" to "Peyker",
        "Yılmaz Bayraktar" to "Captain John", "Gonca Vuslateri" to "Vera", "Haluk Bilginer" to "Kemal", "Ezgi Mola" to "Leyla")
        .mapIndexed { i, (n, r) -> CastMember(n, r, if (i % 4 == 3) null else "fake:face:$i") }

    /** App'i örnek veriyle doldurur (her testte bir kez) */
    fun install(ctx: Context) {
        val app = App.instance
        app.art.offline = true
        app.trailers.offline = true
        movies.forEachIndexed { i, m ->
            app.art.seed("movie", m.name, m.year, m.tmdb, Art(backdrop = "fake:backdrop:m$i", logo = if (i % 3 == 1) "fake:logo:${m.name}" else null, poster = m.icon,
                overview = m.plot, vote = 7.1 + i % 3 * 0.4, votes = 1200, tmdbId = 500 + i))
            app.art.seedCast("movie", 500 + i, cast)
            app.art.seedCritics("movie", 500 + i, Critics(imdb = 7.4, imdbVotes = 52000, rt = 88, mc = 69, awards = "2 ödül, 5 adaylık"))
        }
        series.forEachIndexed { i, s ->
            app.art.seed("series", s.name, s.year, null, Art(backdrop = "fake:backdrop:s$i", logo = if (i % 2 == 0) "fake:logo:${s.name}" else null, poster = s.cover,
                overview = s.plot, vote = 8.0, votes = 900, tmdbId = 700 + i))
            app.art.seedCast("series", 700 + i, cast)
            app.art.seedCritics("series", 700 + i, Critics(imdb = 8.1, imdbVotes = 120000, rt = 95))
            episodes(s).forEach { (season, eps) -> app.art.seedSeason(700 + i, season, eps.associate { it.num to EpisodeArt("Bölüm adı ${it.num}", it.plot, it.image, 48) }) }
        }
        app.testClient = object : XtreamClient(OkHttpClient(), Account("preview", "http://preview.invalid", "u", "p")) {
            override suspend fun vodInfo(id: Int) = movies.first { it.id == id }.let { VodInfo(plot = it.plot, director = "Örnek Yönetmen", genre = it.genre, durationSecs = (it.runtimeMin ?: 100) * 60, country = "Türkiye", age = "13") }
            override suspend fun seriesInfo(id: Int) = series.first { it.id == id }.let { SeriesInfo(episodes(it), plot = it.plot, genre = it.genre) }
            override suspend fun shortEpg(streamId: Int, limit: Int) = emptyList<EpgItem>()
        }
        app.catalog.setForPreview(catalog)
        runBlocking {
            val pid = app.db.profiles().upsert(ProfileEntity(accountId = "preview", name = "Ulaş", avatar = 0))
            app.user.profileId.value = pid
            val now = System.currentTimeMillis()
            app.db.progress().upsert(ProgressEntity(pid, "movie-1000", "movie", "1000", title = movies[0].name, image = movies[0].icon, ext = "mp4", positionMs = 6 * 60_000, durationMs = 157 * 60_000, updatedAt = now))
            val e = episodes(series[0])[1]!![0]
            app.db.progress().upsert(ProgressEntity(pid, "episode-${e.id}", "episode", e.id, series[0].id, 1, 1, series[0].name, "1. Sezon · 1. Bölüm", e.image, "mkv", 7 * 60_000, 60 * 60_000, now - 1000))
            val e2 = episodes(series[1])[1]!![0]
            app.db.progress().upsert(ProgressEntity(pid, "episode-${e2.id}", "episode", e2.id, series[1].id, 1, 1, series[1].name, "1. Sezon · 1. Bölüm", e2.image, "mkv", 1 * 60_000, 60 * 60_000, now - 2000))
        }
        Coil.setImageLoader(ImageLoader.Builder(ctx).components { add(FakeImages); add(coil.decode.SvgDecoder.Factory()) }.build())
    }
}

/** "fake:tür:anahtar" adresleri için anında üretilen görseller */
object FakeImages : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data.toString()
        if (!url.startsWith("fake:")) return chain.proceed(chain.request) // uygulamanın kendi dosyaları (maskot SVG) gerçek
        val parts = url.split(":", limit = 3)
        val kind = parts.getOrNull(1) ?: "backdrop"
        val key = parts.getOrNull(2) ?: url
        val bmp = when (kind) {
            "poster" -> scene(342, 513, key, vertical = true)
            "face" -> face(key)
            "logo" -> logo(key)
            else -> scene(640, 360, key, vertical = false)
        }
        return SuccessResult(BitmapDrawable(chain.request.context.resources, bmp), chain.request, DataSource.MEMORY)
    }

    private fun hue(key: String) = ((key.hashCode() and 0x7fffffff) % 360).toFloat()

    private fun scene(w: Int, h: Int, key: String, vertical: Boolean): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val hu = hue(key)
        val top = Color.HSVToColor(floatArrayOf(hu, 0.55f, 0.55f))
        val bottom = Color.HSVToColor(floatArrayOf((hu + 40) % 360, 0.7f, 0.18f))
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // güneş + tepeler + iki "kişi" silueti: sahne görseli gibi dursun, düz renk olmasın
        p.color = Color.HSVToColor(160, floatArrayOf((hu + 180) % 360, 0.3f, 1f)); c.drawCircle(w * 0.72f, h * 0.3f, h * 0.12f, p)
        p.color = Color.HSVToColor(220, floatArrayOf(hu, 0.6f, 0.12f))
        val path = Path().apply { moveTo(0f, h * 0.75f); quadTo(w * 0.3f, h * 0.55f, w * 0.6f, h * 0.72f); quadTo(w * 0.85f, h * 0.82f, w.toFloat(), h * 0.66f); lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close() }
        c.drawPath(path, p)
        p.color = Color.argb(200, 10, 10, 16)
        listOf(0.42f, 0.55f).forEach { x -> c.drawCircle(w * x, h * 0.5f, h * 0.06f, p); c.drawRoundRect(w * x - h * 0.07f, h * 0.57f, w * x + h * 0.07f, h * 0.85f, 12f, 12f, p) }
        if (vertical) { p.color = Color.WHITE; p.textSize = w * 0.11f; p.typeface = Typeface.DEFAULT_BOLD; c.drawText(key.uppercase(), w * 0.08f, h * 0.9f, p) }
        return b
    }

    private fun face(key: String): Bitmap {
        val b = Bitmap.createBitmap(185, 185, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.HSVToColor(floatArrayOf(hue(key), 0.25f, 0.35f)))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 182, 150) }
        c.drawCircle(92f, 80f, 42f, p)
        p.color = Color.rgb(60, 50, 70); c.drawRect(30f, 130f, 155f, 185f, p)
        return b
    }

    private fun logo(text: String): Bitmap {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 214, 120); textSize = 96f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        val w = (p.measureText(text.uppercase()) + 20).toInt()
        val b = Bitmap.createBitmap(w, 130, Bitmap.Config.ARGB_8888)
        Canvas(b).drawText(text.uppercase(), 10f, 100f, p)
        return b
    }
}
