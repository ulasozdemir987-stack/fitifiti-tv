# CLAUDE.md — fıtıfıtı Android TV

Fıtıfıtı Stream web sitesinin (ayrı repo: `streaming-platform-interfaceozul`) Android TV uygulaması. **Genel amaçlı:** kullanıcı kendi Xtream Codes bilgilerini girer, kendi yerel profillerini oluşturur. Sitedeki kişisel şeyler (belirli profiller, sabit sunucu/şifre, Birlikte İzle / Birlikte Gez) bu uygulamada YOK ve eklenmemeli.

## Yığın
* Kotlin 2.1, AGP 8.7, Gradle 8.11, compileSdk 35, minSdk 23 (Android TV 6+).
* Jetpack Compose + `androidx.tv:tv-material` (odakta büyüyen Surface/Button). Hilt yok: bağımlılıklar `App.kt`'te elle.
* Room (profiller, ilerleme, Listem, son kanallar/aramalar), EncryptedSharedPreferences (Xtream hesapları), SharedPreferences (ayarlar, sürüm tercihi).
* OkHttp (tarayıcı User-Agent'ı zorunlu — sağlayıcılar libav/okhttp UA'sını reddedebiliyor), kotlinx-serialization, Coil (+SVG).
* Media3 ExoPlayer + HLS + `org.jellyfin.media3:media3-ffmpeg-decoder` (AC3/E-AC3/DTS yazılımla çözülür).

## Derleme
```bash
./gradlew --no-daemon assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```
GitHub Actions (`.github/workflows/android.yml`) her push'ta debug APK'yı artifact olarak yükler. Release imzası: `FITIFITI_KEYSTORE`, `FITIFITI_KEYSTORE_PASSWORD`, `FITIFITI_KEY_ALIAS`, `FITIFITI_KEY_PASSWORD` ortam değişkenleri (yoksa debug imzası).

## Mimari
* `data/xtream/` — `XtreamClient` (player_api.php: kategoriler, film/dizi/kanal listeleri, `get_vod_info`, `get_series_info` (episodes nesne YA DA dizi gelebilir), `get_short_epg` (base64 başlıklar)), oynatma adresleri.
* `data/catalog/CatalogRepository` — açılışta diskteki önbellek (`catalog-<hesap>.json`) hemen, ağ arka planda; film/dizi tekilleştirme + sürümler (`domain/CatalogDedupe.kt`: "Orijinal dil / Türkçe dublaj / 4K"). `UserData` — etkin profilin akışları (devam et: dizi başına tek kart; süre 0 kayıt = "sıradaki bölüm" yer tutucusu).
* `data/tmdb/ArtRepository` — kullanıcının KENDİ TMDB anahtarıyla (Ayarlar) yazısız sahne görseli, logo, özet, puan, oyuncular, bölüm görselleri. Anahtar yoksa sağlayıcı görsellerine düşer.
* `domain/` — siteden taşınan mantık: `Format.kt` (başlık temizleme, `splitTitle`, süre), `CategoryStyle.kt` (platform adları/logolar, sıralama), `LiveFormat.kt` (maç başlıkları), `Search.kt` (Türkçe/aksan duyarsız, yazım hatası toleranslı), `Ranking.kt` (öne çıkan/yeni/en beğenilen; sağlayıcı puanında oy sayısı yok → 9.3+ şüpheli, puan ortalamaya çekilir).
* `ui/Nav.kt` + `ui/AppRoot.kt` — basit ekran yığını. Alttaki ekranlar bileşimde KALIR ama yerleştirilmez (çizilmez, odak almaz); geri dönünce kaydırma ve odak (`focusRestorer`) korunur.
* `ui/Actions.kt` — ortak eylemler (detay aç, oynat, devam et, kanal aç, seçili sürüm).
* `ui/screens/` — Login, Profiles (PIN'li), Main (üst sekmeler: Ana Sayfa · Filmler · Diziler · Canlı TV · Listem + Ara, Ayarlar), Home/Media (`HeroRowsLayout`: üstte odaktaki içeriğin vitrini, altında şeritler; şerit odağa gelince listenin tepesine hizalanır), Movie/Series detay, Category, Listem (durum süzgeci), Search, Settings, Live.
* `ui/player/` — `PlayerScreen` (VOD: sarma, ses/altyazı, bölümler, uyku zamanlayıcısı, girişi atla (+85 sn, ilk 4 dk), sonraki bölüm kartı, kaldığın yerden), `LivePlayerScreen` (↑/↓ kanal, rakamla kanal, OK = kanal listesi, bilgi şeridi; önce .m3u8, olmazsa .ts).

## Tasarım (sitedekiyle aynı dil)
* Renkler `ui/theme/Theme.kt` → `C`: zemin #050508, panel #12121c, fill1/2/3 = beyaz %4/7/10, çizgi %9, mor #8b5cf6, ilerleme mor → turkuaz #2dd4bf. Başlıklar Manrope ExtraBold (`Display`).
* Seçili = beyaz yazı + ince mor çizgi. TV odağı = hafif büyüme + beyaz çerçeve (her tıklanabilir öğede görünür olmak zorunda).
* Düğme metinleri: "Oynat", "Devam et", "Detaylar", "Listem", "Bölümler".

## Tuzaklar
* Henüz çizilmemiş bir öğeye `requestFocus()` sessizce başarısız olur → oynatıcıda `pendingFocus` + kısa gecikme kullanılıyor.
* ExoPlayer dinleyicisi ilk bileşimde kurulur; içinde değişen değerler `rememberUpdatedState` ile okunmalı.
* Hesaplar genelde tek bağlantılı: canlı yayında kanal değişince aynı oynatıcıda kaynak değiştirilir; aynı anda iki oynatıcı açma.
* `BasicTextField` TV'de odaklanınca klavye açmasın diye `showKeyboardOnFocus = false`; OK tuşu açar.
* Platform logoları `app/src/main/assets/brands/` (kaynaklar README'de); Marvel/TOD/Exxen/Gain dışındakiler beyaza boyanır.

## Telefon kumandası
* `data/remote/RemoteLink.kt`: uygulama ön plandayken (MainActivity onStart/onStop) `wss://ozul.com.tr/ws/together?room=tv-<kod>` odasına bağlanır. Kod (10 karakter) ve AES-256 anahtarı cihazda bir kez üretilir (`remote` prefs; "Yeni kod" ile yenilenir). QR adresi `https://ozul.com.tr/tv?k=<kod>#<anahtar>` — sitedeki `components/tv-remote.tsx` sayfası.
* `RemoteBus`: ekranlar durumu yayınlar (`screen` login|app|player, odaktaki `TvTextField` → `input`, oynatıcı → `player`), telefondan gelen `keys` MainActivity'de gerçek KeyEvent olarak gönderilir (kumandayla aynı yol), `text` odaktaki kutuyu doldurur, `seek` oynatıcıyı sarar, `login` (AES-GCM ile çözülmüş Xtream bilgisi) giriş ekranını doldurup bağlanır, `home` ana sayfaya döner.
* QR: zxing `core` (`components/RemotePair.kt`: `QrCode`, `RemoteQrCard`, `RemotePairDialog`). Giriş: Giriş ekranı, Profiller ("Telefon kumandası"), Ayarlar.
