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
./gradlew --no-daemon assembleRelease   # app/build/outputs/apk/release/app-release.apk (R8; dağıtılan sürüm)
./gradlew --no-daemon assembleDebug     # geliştirme (TV'de çok yavaş: debuggable, R8 yok)
```
GitHub Actions (`.github/workflows/android.yml`) her push'ta testleri çalıştırır, release APK'yı derleyip `latest` sürümüne yükler (ozul.com.tr/tv.apk). Debug ve release aynı ortak anahtarla imzalı (`app/debug.keystore`) → birbirinin üstüne kurulur. Release imzası: `FITIFITI_KEYSTORE`, `FITIFITI_KEYSTORE_PASSWORD`, `FITIFITI_KEY_ALIAS`, `FITIFITI_KEY_PASSWORD` ortam değişkenleri (yoksa debug imzası).

## Mimari
* `data/xtream/` — `XtreamClient` (player_api.php: kategoriler, film/dizi/kanal listeleri, `get_vod_info`, `get_series_info` (episodes nesne YA DA dizi gelebilir), `get_short_epg` (base64 başlıklar)), oynatma adresleri.
* `data/catalog/CatalogRepository` — açılışta diskteki önbellek (`catalog-<hesap>.json`) hemen, ağ arka planda; film/dizi tekilleştirme + sürümler (`domain/CatalogDedupe.kt`: "Orijinal dil / Türkçe dublaj / 4K"). `UserData` — etkin profilin akışları (devam et: dizi başına tek kart; süre 0 kayıt = "sıradaki bölüm" yer tutucusu).
* `data/tmdb/ArtRepository` — yazısız sahne görseli, logo, özet, puan, oyuncular, bölüm görselleri. Kullanıcı kendi TMDB anahtarını girdiyse (Ayarlar) doğrudan TMDB, girmediyse sitenin `ozul.com.tr/api/tv-tmdb` aracısı (izinli yollar; IP başına 120/dk — 429/5xx önbelleğe yazılmaz). `critics()` IMDb / Rotten Tomatoes / Metacritic / ödülleri her zaman sitenin `/api/reviews?kind=&tmdbId=` ucundan alır (OMDb anahtarı sunucuda); detay sayfalarında `CriticsRow`.
* Maskot: `assets/mascot.svg` (sitedeki `components/mascot-logo.tsx`'ten; AndroidSVG filtre desteklemediği için ışıma yok). `components/Mascot.kt`: `Mascot`, `MascotLoader` (zıplayan + gölge), `MascotOops` (boş/hata), `BrandLogo` (maskot + yazı: menü, giriş, profiller), `IndeterminateLine`, `KenBurns` (oynatıcı açılışı).
* `data/trailer/TrailerRepository` + `ui/components/Trailer.kt` (`TrailerVideo`; oynatıcı AYRI iş parçacığında — `release()` ana iş parçacığını kilitleyip geri tuşunu geciktiriyordu; görüntü bölgeyi kırparak kaplar; `TrailerMuteButton` başlık düğmelerinde) — detay hero'sunda fragman: sitenin `/api/trailer` (YouTube kimliği) + `/api/trailer-file` (VPS'teki 1080p MP4; hazır değilse sunucu indirir, 5 sn'de bir 90 sn sorulur). TextureView (saydamlık animasyonu için), sayfa aşağı kayınca durur, ekran pasifken (oynatıcı açık) serbest bırakılır, bitince görsele döner. Ayarlar: `trailerAutoplay`, `trailerSound`.
* `domain/` — siteden taşınan mantık: `Format.kt` (başlık temizleme, `splitTitle`, süre), `CategoryStyle.kt` (platform adları/logolar, sıralama), `LiveFormat.kt` (maç başlıkları), `Search.kt` (Türkçe/aksan duyarsız, yazım hatası toleranslı), `Ranking.kt` (öne çıkan/yeni/en beğenilen; sağlayıcı puanında oy sayısı yok → 9.3+ şüpheli, puan ortalamaya çekilir).
* `ui/Nav.kt` + `ui/AppRoot.kt` — basit ekran yığını. Alttaki ekranlar bileşimde KALIR ama yerleştirilmez (çizilmez, odak almaz); geri dönünce kaydırma ve odak (`focusRestorer`) korunur.
* `ui/Actions.kt` — ortak eylemler (detay aç, oynat, devam et, kanal aç, seçili sürüm).
* `ui/screens/` — Login, Profiles (PIN'li), Main (üst sekmeler: Ana Sayfa · Filmler · Diziler · Canlı TV · Listem + Ara, Ayarlar), Home/Media (`HeroRowsLayout`: üstte odaktaki içeriğin vitrini, altında şeritler; şerit odağa gelince listenin tepesine hizalanır), Movie/Series detay, Category, Listem (durum süzgeci), Search, Settings, Live.
* `ui/player/` — `PlayerScreen` (VOD: sarma, ses/altyazı, bölümler, uyku zamanlayıcısı, girişi atla (+85 sn, ilk 4 dk), sonraki bölüm kartı, kaldığın yerden), `LivePlayerScreen` (↑/↓ kanal, rakamla kanal, OK = kanal listesi, bilgi şeridi; önce .m3u8, olmazsa .ts).

## Tasarım (sitedekiyle aynı dil)
* **Kartlar Netflix / Prime Video TV gibi yatay (16:9):** `components/WideCard.kt` — TMDB sahne görseli + logo/ad (görsel kart ekrana gelince, 180 ms gecikmeyle istenir), yoksa bulanık afiş zemini + afiş. Sıralı şeritte (`ranked`) solda büyük sıra numarası. Vitrinli şeritler (`posterRow`), detaydaki benzerler (`plainPosterRow`), kategori ve Listem ızgaraları (`GridCells.Adaptive(196.dp)`) bunu kullanır. Ölçek (kullanıcı isteği, 2.6.1): kart 196 dp (sıralı 176), yazılar küçük (displayLarge 42, headlineSmall 18, bodyLarge 15 sp), vitrin ekranın %52si (düğmeler kesilmesin), üst çubuk ince — ekrana daha çok şey sığsın. `PosterCard` (2:3) artık kullanılmıyor.
* Renkler `ui/theme/Theme.kt` → `C`: zemin #050508, panel #12121c, fill1/2/3 = beyaz %4/7/10, çizgi %9, mor #8b5cf6, ilerleme mor → turkuaz #2dd4bf. Başlıklar Manrope ExtraBold (`Display`).
* Seçili = beyaz yazı + ince mor çizgi. TV odağı = hafif büyüme + beyaz çerçeve (her tıklanabilir öğede görünür olmak zorunda).
* Düğme metinleri: "Oynat", "Devam et", "Detaylar", "Listem", "Bölümler".
* **Kedili logo animasyonu (`components/AnimatedBrandLogo.kt`):** sitedeki `animated-brand-logo.tsx`'in birebir Canvas portu (aynı sahne birimleri, aynı `T` anları, CSS keyframe eğrileri küçük anahtar kare motoruyla: her karenin eğrisi sonraki parçaya uygulanır). Kedi yolları SVG'den `PathParser` ile; f/t Manrope 104 birim, "ı"lar çubuk. Kullanıldığı yerler: soğuk açılış (`BrandSplash`, MainActivity'de AppRoot'tan önce; ~4,9 sn ya da tuşa basınca geçer, başlatıcının OK bırakışı geçirmesin diye önce KeyDown görülmeli), oynatıcı açılışı (`PlayerLoading`), canlı TV yüklenirken. Kareleri gözle kontrol: `BrandLogoTest` → `app/build/brand-frames/frames.png`.
* **Oynatıcı (2.7.0, sitedeki Netflix/Prime tarzı):** `player/PlayerChrome.kt` — `rememberPlayerMeta` (Xtream + TMDB: logo, sahne, özet, puan, bölüm adı/özeti/görseli, oyuncular), `PlayerLoading`, `PauseScreen` (duraklatınca 0,6 sn sonra; oynayınca kapanır: "İzliyorsun", logo, bölüm, yıl · ★ · süre · tür, özet, **X-Ray** oyuncular), `ResumePrompt`. Kontroller: üstte küçük logo + saat · "Bitiş HH:mm"; altta geçişli ilerleme çubuğu (sararken tutamaç üstünde zaman baloncuğu) + kalan süre; solda oynat/±10, ortada başlık, sağda X-Ray · Bölümler · Ses ve altyazı · Uyku · Telefonla kumanda (QR) · Sonraki. Düğme adı yalnız odakta altında. Kumanda: ◀ ▶ sar (basılı tutunca hızlanır), OK duraklat/oynat, **▲ X-Ray**, ▼ kontroller, geri = önce açık katmanı kapatır.
* **Düğmeler (2.8.0, minimal):** `Btn` çerçevesiz — dinlenirken buzlu cam (beyaz %16–20), odakta düz beyaz + siyah yazı + hafif ışıma/büyüme (halka yok). Asıl eylem yazılı hap ("Oynat"/"Devam et"), ikincil eylemler `IconAction` yuvarlak ikon (Listem +/✓, İzlendi, Baştan, Detaylar, Ses): adı yalnız odakta altında; durum açıksa ikon mor tonlu. Oynatıcı `PillBtn`/`CtrlBtn` aynı dil.
* **Detay üst alanı:** logo ≤72 dp / genişlik %28, özet 4 satır %50 genişlik, puan satırı küçük; blok SOL ÜSTTEN başlar (top 56 dp, `Arrangement.Top`; alta yaslıyken logo ekranın ortasına iniyor, özet 2 satırda kesiliyordu — kullanıcı geri bildirimi). Film başlık alanı ekranın %76sı, dizi %62si.
* **Ödül defneleri (`components/AwardLaurels.kt`):** sitedeki `award-laurels.tsx`'in Canvas portu (aynı dal geometrisi). Oscar, Emmy, Altın Küre, BAFTA, Cannes, Venedik, Berlin, Altın Portakal; kazanılan altın, yalnız aday Oscar/Emmy gümüş, en fazla 4, sırayla belirir. Sayı `Critics.major` (site `/api/reviews` → OMDb özeti) ile `art.awards(imdbId)` (site `/api/awards`, Wikidata) değerinin büyüğü. Detayda puan satırının altında (fragmana binmez); puan satırındaki ödül metninden defnelerde görünen törenler atılır (`awardsRemainder`).
* **Yazı kutusu odak kaybı:** klavye Geri ile kapanınca BasicTextField odağı bırakıyor ama odak hiçbir yere geçmiyordu (tuşlar boşa gidiyordu) → `TvTextField` beklenmedik kayıpta odağı kutuya geri verir (`restoreBox`).
* **Uygulama simgesi + Android TV banner'ı:** `BrandAssetsTest` uygulamanın kendi logo çiziminden (`drawBrandLogo` / `drawBrandCat`, kedi son karede oturmuş) üretir → `app/build/brand-assets/`. Yerleri: `res/drawable-xhdpi/app_banner.png` (320×180 dp → 640×360; obsidian + hafif mor/turkuaz ışıma, ortada "fıtıfıtı" + kedi), uyarlanabilir simge `mipmap-anydpi-v26/ic_launcher.xml` → `mipmap-xxxhdpi/ic_launcher_foreground.png` (yalnız kedi, güvenli alan içinde) + `ic_launcher_background.png`, eski cihazlar için `mipmap-*/ic_launcher.png`. Logo değişirse testi çalıştırıp dosyaları yeniden kopyala.
* **Not (başka araçlarla çalışırken):** bu dosya UTF-8; Windows PowerShell'de `>>` ile ekleme UTF-16 yazar ve dosyayı bozar (2.9.6 notunda oldu, düzeltildi) — düzenleyiciyle ya da `Add-Content -Encoding utf8` ile ekle.
* **Uzun basış:** kumanda Android'in uzun basış işaretini göndermiyor (tv-material `onLongClick` gerçek kutuda hiç tetiklenmiyordu) → `components/Interaction.kt` `Modifier.okClicks(onClick, onLong)` OK'in basılı kalma süresini (≥500 ms ya da tekrar eden KeyDown) kendisi ölçer, seçenekleri BIRAKINCA açar (basılıyken açılsa bırakış penceredeki ilk seçeneğe tıklardı). MENU da açar. Test: `OkClicksTest`.
* **Arama:** üst çubuktaki Ara/Ayarlar simgeleri de sekmeler gibi üzerinde durunca sayfayı açar; Ara'ya OK = arama sayfası + kutuda klavye (`TvTextField(startEditing)`, istek bir kez tüketilir). Eskiden simgeye gelince hiçbir şey olmuyor, uygulama donmuş sanılıyordu.
* **"İzlemeye devam et" kartında OK'e basılı tut (ya da MENU):** `OptionsDialog` → Devam et / Detaylar / İzlendi olarak işaretle / Devam et'ten kaldır (`UserData.removeFromContinue`: dizide bitmemiş tüm bölüm kayıtları silinir, bitenler "izlendi" işareti olarak kalır).

## Tuzaklar
* **Geri tuşu Compose'a verilmez** (`MainActivity.dispatchKeyEvent` → doğrudan `onBackPressedDispatcher`). Compose TV'de Back'i önce `FocusDirection.Exit` (odağı üst gruba taşı) olarak tüketiyordu: iç içe her odak grubu için bir basış gerekiyordu. Gerçek kutuda adb ile bulundu (geri → sayfa kapanmıyor, yalnız odak kayboluyordu).
* Fragman oynatıcısı kendi iş parçacığında: `setVideoTextureView` KULLANMA (görünüm ana iş parçacığında kaldırılınca ExoPlayer dinleyicisi yanlış iş parçacığından çağrılıp çöküyordu, 2.5.1). `TrailerEngine.attach` yüzeyi kendisi yönetir, SurfaceTexture'ı oynatıcı bıraktıktan sonra serbest bırakır.
* **Gerçek cihaz testi:** kullanıcının kutusu (Nova, 192.168.1.108) ev tüneli üzerinden ADB ile erişilebilir: site reposunda `.vps/cmd.sh` (VPS'te `socat` 127.0.0.1:15555 → SOCKS 1080 → kutu:5555; `adb -s 127.0.0.1:15555`, stdin'i yutmasın diye `</dev/null`). Betiğin `/tmp/box-out`'a koyduğu ekran görüntüleri/loglar `claude/box-out` dalına gelir (`git fetch origin claude/box-out`). Ev bilgisayarı kapalıysa erişim yok.
* **`Modifier.focusRestorer()` KULLANMA.** İç içe iki tane (ekran katmanı + sekme içeriği) 2.5.0'da yön tuşlarında `IllegalStateException: Release should only be called once` (LazyLayoutPinnableItem) ile çöktürdü. Yerine `ui/FocusMemory.kt` (`rememberFocus()`, alt hafıza `FocusMemory(parent)`; bileşimden çıkan öğe unutulur). Regresyon testi: `NestedRestorerTest`.
* **Android TV'de LazyColumn/LazyRow varsayılan kaydırması (`PivotBringIntoViewSpec`) odaktaki öğeyi ekranın üst %30'una çeker** — öğe zaten görünse bile. Detay sayfası bu yüzden "Oynat"a odaklanınca ~300 px kayıyordu. Dikey listelerde `LocalBringIntoViewSpec provides rememberRowSpec(…)` (yalnız gerektiği kadar kaydırır) kullan; `DetailScaffold` bunu yapar.
* Yön araması yalnız hizalı öğeye gider: üst çubuğun sağındaki simgelerin altında öğe yoksa ↓ hiçbir şey yapmıyordu. Üst çubukta `focusProperties { down = contentMem.last ?: contentFocus }` (içeriğin son odaklanan öğesi, yoksa grubun ilk öğesi), içerik `focusRequester().focusGroup()`.
* tv-material'da `Surface` dışındaki `Text`'in varsayılan rengi SİYAH (`LocalContentColor` = Black). `FitifitiTheme` kökte beyaz verir; yine de koyu zemindeki metne renk vermeyi unutma.
* Detay sayfalarında başlık bloğu (ilk LazyColumn öğesi) ekrana SIĞMALI ve `Modifier.detailHead()` taşımalı (içinde odak olunca liste en üste kayar). Taşarsa sayfa kesik açılır, üstü kaydırılamaz ve fragman "aşağı kaydırıldı" sanılıp durur. Ek bilgileri ayrı öğeye koy.
* Henüz çizilmemiş bir öğeye `requestFocus()` sessizce başarısız olur → oynatıcıda `pendingFocus` + kısa gecikme kullanılıyor.
* ExoPlayer dinleyicisi ilk bileşimde kurulur; içinde değişen değerler `rememberUpdatedState` ile okunmalı.
* Hesaplar genelde tek bağlantılı: canlı yayında kanal değişince aynı oynatıcıda kaynak değiştirilir; aynı anda iki oynatıcı açma.
* Geri dönünce odak: her `ScreenLayer` kendi `FocusMemory`'sini verir; odaklanabilir öğelere `Modifier.rememberFocus()` ekle (kartlar, Btn, Chip, SettingRow… ekli). Eklenmezse geri dönüşte odak üst çubuğa düşebilir ve sekme "üzerinde durunca açılır" kuralıyla yanlış sekme açılır. Sekme değiştirip odak verirken önce sekmeyi değiştir, odağı yeniden bağlanınca (gecikmeyle) iste.
* `TvTextField` normalde düğmedir (Surface); OK'e basınca yazma moduna (BasicTextField + klavye) geçer. `showKeyboardOnFocus = false` Nova'da (Android 14) işe yaramıyordu: odak gelince klavye açılıp yön tuşlarını kapıyordu.
* Platform logoları `app/src/main/assets/brands/` (kaynaklar README'de); Marvel/TOD/Exxen/Gain dışındakiler beyaza boyanır.

## Uygulama içi güncelleme
* CI her derlemede `latest` sürümüne APK + `version.json` (`versionCode`, `versionName`, `notes` = son commit başlığı) yükler. Site (`lib/tv-apk.ts`) ikisini önbelleğe alır: `ozul.com.tr/tv.apk` dosyayı, `ozul.com.tr/tv-version` bilgiyi verir.
* `data/update/Updater.kt`: açılışta (en fazla 30 dk'da bir) `tv-version`'a bakar, `versionCode` büyükse `UpdateDialog` ("Güncelleme var · Kur / Sonra"). Kur → APK önbelleğe iner (boyut doğrulanır) → `PackageInstaller` oturumu → `UpdateReceiver` Android'in onay ekranını açar. Android 8+'da izin yoksa önce "Bilinmeyen uygulamaları yükle" ayarı açılır; dönünce (onStart) kendiliğinden devam eder. Ayarlar → Hakkında → "Güncellemeler" elle kontrol/kur. **Sürüm çıkarırken `versionCode`'u artırmayı unutma** (yoksa güncelleme görünmez).

## Hızlı geliştirme döngüsü (önce bunu kullan)
1. **Ekran önizlemeleri (kutusuz, ~45 sn):** `./gradlew testDebugUnitTest --tests '*ScreenPreviews*'` → `app/build/screens/` (960×540 PNG). Gerçek ekranlar örnek veriyle (`test/.../preview/PreviewData.kt`: katalog, oyuncular, devam kayıtları, eleştirmen puanları) 1920×1080 TV ekranında çizilir; görseller `fake:` adresleri (Coil yakalayıcısı renkli sahne/afiş/yüz/logo üretir), ağa çıkılmaz (`art.offline`, `trailers.offline`, `App.testClient`). Yeni ekran/durum için `ScreenPreviews`'e bir satır ekle; `keys` ile odak durumu (ör. `listOf(Key.DirectionRight)`). Tasarım değişikliğini kutuya kurmadan önce buradan gör.
2. **Kutuya doğrudan kurulum (~15 sn):** yerelde `./gradlew assembleRelease` → APK'yı kutu ajanına yükle (CI'ı ve /tv.apk'yı beklemeden). Resmi sürüm yine push → CI → uygulama içi güncelleme.
3. **Kutu ajanı (komut başına saniyeler):** VPS'te `ozul-box-agent` servisi (kaynak: site deposunda `deploy/box-agent/agent.mjs`), `https://ozul.com.tr/__box/` → yalnız sabit işlemler: `POST /apk` (gövde = APK, kurar), `POST /run` `{"steps":[…]}` (adımlar: `{"start":true}`, `{"key":"DPAD_DOWN"}` ya da `{"key":[…],"gap":0.5}`, `{"longpress":"DPAD_CENTER"}`, `{"text":"dune"}`, `{"wait":2}`, `{"shot":"ad"}`, `{"install":"site"}`, `{"logcat":"clear"|"crash"|"<etiket>"}`, `{"top":true}`), `GET /shot/<ad>.png`, `GET /health`. Kimlik `Authorization: Bearer <token>`; token VPS'te `/etc/ozul-box-token`, git'te YOK. Yeni Claude oturumunda token elde değilse: yeni token üret, VPS genel anahtarıyla (RSA-OAEP) şifreleyip site deposundaki `.vps/cmd.sh` ile `/etc/ozul-box-token`'a yaz + `systemctl restart ozul-box-agent`.
   * Not: kutunun `input keyevent --duration` desteği yok (tuş 4 ms'de bırakılıyor); basılı tutma testi için `longpress`.
4. **Kullanıcının "Sorun bildir"i:** uygulamada herhangi bir ekranda Geri'ye 1 sn basılı tutunca (MainActivity: tekrar eden KeyDown ya da bırakışta süre ≥1 sn → geri işlenmez) ya da Ayarlar → Sorun bildir: `data/diag/Feedback.kt` ekranı PixelCopy ile yakalar (video dahil), `FeedbackDialog` kategori + not (telefon kumandasından yazılabilir) → `ozul.com.tr/api/tv-feedback` (görsel + `Diag` son olaylar + `Diag.lastScreen`). Okuma: kutu ajanı `GET /__box/feedback` (son 20, JSON) ve `/__box/feedback/<id>.jpg`. Kullanıcı bir sorun anlattığında önce buraya bak.
5. Eski yol (yalnız ajan çalışmıyorsa): site deposunda `.vps/cmd.sh` + `claude/**` dalına push → `VPS Run` iş akışı (~3–4 dk ek bekleme).

## Testler (Robolectric)
* `app/src/test`: `DomainTest` (saf mantık) + kumanda gezinmesi testleri (`FocusProbeTest` detay sayfası kaymıyor / aşağıdan dönünce en üste, `SettingsProbeTest` üst çubuktan ↓, `BackFocusTest` tek geri basışında odak girilen karta ~100 ms'de döner). `@Config(qualifiers = "…-television")` + `FEATURE_LEANBACK` ile TV davranışı (pivot kaydırma) birebir çıkar; tuşlar `performKeyInput { pressKey(Key.DirectionDown) }`. TV'de görülen odak/kaydırma hatalarını önce burada tekrar üret.

## Telefon kumandası
* `data/remote/RemoteLink.kt`: uygulama ön plandayken (MainActivity onStart/onStop) `wss://ozul.com.tr/ws/together?room=tv-<kod>` odasına bağlanır. Kod (10 karakter) ve AES-256 anahtarı cihazda bir kez üretilir (`remote` prefs; "Yeni kod" ile yenilenir). QR adresi `https://ozul.com.tr/tv?k=<kod>#<anahtar>` — sitedeki `components/tv-remote.tsx` sayfası.
* `RemoteBus`: ekranlar durumu yayınlar (`screen` login|app|player, odaktaki `TvTextField` → `input`, oynatıcı → `player`), telefondan gelen `keys` MainActivity'de gerçek KeyEvent olarak gönderilir (kumandayla aynı yol), `text` odaktaki kutuyu doldurur, `seek` oynatıcıyı sarar, `login` (AES-GCM ile çözülmüş Xtream bilgisi) giriş ekranını doldurup bağlanır, `home` ana sayfaya döner.
* QR: zxing `core` (`components/RemotePair.kt`: `QrCode`, `RemoteQrCard`, `RemotePairDialog`). Giriş: Giriş ekranı, Profiller ("Telefon kumandası"), Ayarlar.

## Tanılama (çökme / donma / oynatıcı)
* `BackProbe`: geri tuşu basış → işlenme → ilk kare süresi; işlenmeyen basış ya da >700 ms gecikme `freeze` raporu olarak gelir (oturumda ≤3). Ekran geçişleri halkada (`ekran +MovieDetail` / `ekran −…`).
* `data/diag/Diag.kt`: son 80 olay halkada (oynatıcı çözücüsü, biçim, düşen kare, hata, bellek). Rapor `POST https://ozul.com.tr/api/tv-report` (sitede; son 80 rapor `DATA_DIR/tv-reports.json`, okuma `x-monitor-key` ya da VPS'te dosya). Adreslerdeki hesap bilgisi hem uygulamada hem sunucuda temizlenir.
* `FreezeWatchdog`: ana iş parçacığı 4 sn+ takılırsa o anki yığın + son olaylar hemen gönderilir (Android uygulamayı kapatsa bile rapor gitmiş olur). Çökmeler `last-crash.txt` → sonraki açılışta gönderilir + ekranda gösterilir.
* Oynatıcı tamponu 48 MB ile sınırlı (varsayılan ~140 MB Java belleği düşük bellekli TV'lerde 4K'da sorun çıkarıyordu).

## Güncelleme — 2026-10-04 (2.9.4): Netflix Tarzı Tam Sayfa Kaydırma & Optimize Ölçek
* **HeroRowsLayout:** Eskiden sabit üst alan olarak tutulan `HeroInfo`, artık `LazyColumn`'ın ilk öğesi olarak entegre edildi.
* **Tam Sayfa Sörf:** Kullanıcı aşağı kaydırdığında vitrin doğal biçimde yukarı kayıp ekrandan çıkar ve üst menü çubuğu gizlenir (`TopBarState.hidden`). Tüm ekran (100%) şeritlere açılır; ekranda aynı anda tam 3 şerit birden görünür, rahatça sörf yapılır.
* **Geri Dönüş:** En üste (Row 1'den yukarı) çıkıldığında odak `HeroButtons`'a ("Oynat") döner, vitrin ve üst çubuk pürüzsüzce geri gelir.
* **Kart ve Logo Ölçekleri:** Kart genişlikleri 220dp (sıralı 190dp) olarak ayarlandı; 1080p (540dp TV Compose) ölçeğinde tam 3 dikey satır ve yatayda 4 tam + 1 ucu görünen kart dizilimi sağlandı.

## Güncelleme — 2026-10-04 (2.9.5): Sinematik Zemin ve Hero Maskesi (Web Site Uyumu)
* **Cinematic Background:** Eskiden vitrin arkaplanı odaktaki filme göre tam ekran değişiyor ve aşağı kaydırıldığında şeritlerin arkasında kalıyordu. Artık uygulamanın ana arkaplanı sitedeki `.cinematic-bg` gibi çok hafif mor-turkuaz degrade ışımalı (glow) sabit koyu zemin oldu.
* **HeroBillboardBackdrop:** Vitrin (Hero) resmi artık ekrana yayılmak yerine, sadece kendi vitrin (billboard) kutusu içerisinde kalıyor ve alt kenardan yukarı doğru maskelenerek (`BlendMode.DstIn`) sinematik zeminle birleşiyor (sitedeki `-webkit-mask-image` tekniği Compose'da uygulandı). Aşağı kaydırıldığında resim ekrandan temiz bir şekilde çıkıyor.
* **Derleme Düzeltmeleri:** Compose'un `foundation`, `graphics`, `layout` ve `animation` modülleri eksiksiz içe aktarıldı, TV'de test edildi.

⌀⌀ 䜀ﰀ渀挀攀氀氀攀洀攀 ⴀ ㈀　㈀㘀ⴀ㄀　ⴀ　㐀 ⠀㈀⸀㤀⸀㘀⤀㨀 䄀渀椀洀愀猀礀漀渀氀甀 䌀愀渀氀㄀ 䴀攀渀ﰀ 䰀漀最漀猀甀ഀ਀⨀ ⨀⨀䄀渀椀洀愀琀攀搀䈀爀愀渀搀䰀漀最漀 ☀ 䴀愀椀渀匀挀爀攀攀渀㨀⨀⨀ �猀琀 洀攀渀ﰀ 甀戀甀ἀ甁渀搀愀欀椀 ⠀吀漀瀀䈀愀爀⤀ 猀琀愀琀椀欀 昀㄀琁㄀昁㄀琁㄀ 氀漀最漀猀甀 欀愀氀搀㄀爁㄀氁愀爀愀欀 礀攀爀椀渀攀 䄀渀椀洀愀琀攀搀䈀爀愀渀搀䰀漀最漀 攀欀氀攀渀搀椀⸀ 䰀漀最漀礀愀 挀漀洀瀀愀挀琀 ⠀欀㄀爁瀀㄀氁洀㄀弁 最爀ﰀ渀ﰀ洀⤀ 瘀攀 氀椀瘀攀 ⠀眀攀戀 猀椀琀攀猀椀渀搀攀欀椀 眀漀爀搀洀愀爀欀 最椀戀椀⤀ 洀漀搀氀愀爀㄀ 攀欀氀攀渀搀椀⸀ഀ਀⨀ ⨀⨀䌀愀渀氀㄀ 䴀漀搀㨀⨀⨀ 　氁欀 ﰀ ✀㄀✁ 栀愀爀昀椀 攀欀漀氀愀礀稀攀爀 最椀戀椀 甀稀愀礀㄀瀁 欀㄀猁愀氀㄀礁漀爀Ⰰ 猀漀渀 ✀㄀✁ ⠀欀攀搀椀渀椀渀 欀漀渀搀甀ἀ甁⤀ 猀愀戀椀琀⸀ 䰀漀最漀 爀攀渀欀氀攀爀椀 最ﰀ渀ﰀ渀 猀愀愀琀椀渀攀 最爀攀 瀀愀氀攀琀琀攀渀 ⠀猀愀戀愀栀Ⰰ ἀ氁攀渀Ⰰ 愀欀开愁洀Ⰰ 最攀挀攀⤀ 礀甀洀甀开愁欀 最攀椀开氁攀爀氀攀 ⠀愀渀椀洀愀琀攀䌀漀氀漀爀䄀猀匀琀愀琀攀⤀ 搀攀ἀ椁开椁礀漀爀⸀ഀ਀�
- v2.9.9: Diziler ve filmler icin afis detay bilgileri (ulke, yonetmen) eklendi, Hero alani asagiya yaslandi.

## Son Yapılanlar (Live TV Refactor)
- **Aşama 1**: EpgCache.kt XmlPullParser ile stream okuyacak şekilde uyarlandı, EpgEntity Room tablosuna eklendi.
- **Aşama 2**: LiveManager.kt oluşturuldu, profil bazlı kanal listeleri ve düzenleme özellikleri eklendi. ChannelEditScreen yazıldı.
- **Aşama 3**: LivePlayerScreen uydu alıcısı mantığıyla güncellendi (numara girişi, zap bilgi şeridi, yan panel ve EPG grid eklendi).
- **Aşama 4**: LiveFormat.kt taşındı, takvim ekranı (LiveCalendarScreen) ve hatırlatıcı altyapısı (ReminderManager) kuruldu.
- **Aşama 5**: LiveScreen keşfet ekranı baştan tasarlandı; anlık maçlar, filmler, diziler yatay şeritler halinde eklendi.


## Güncelleme — 2026-10-10 (Claude): OwnTV düzeni (dal `yeni-duzen`, 3.0.0)

* Düzen OwnTV'den (GPLv3) esinlenildi ama KOD KOPYALANMADI: tamamen bizim kodumuz, lisans yükümlülüğü yok. Renk/logo/kedi bizim.
* **Kabuk:** üst çubuk yerine sol dikey menü (`components/NavRail.kt`: kedi, Ara, Ana sayfa, Canlı TV, Yayın akışı, Filmler, Diziler, Listem, Ayarlar, profil). Odakta beyaz + sağa açılan ad etiketi, seçili = mor zemin + turkuaz nokta; üzerinde 320 ms durunca sekme açılır. Sağ üstte `CornerClock` (mor → turkuaz saat + tarih). İçerik `RailInset` (38dp) kadar sağdan başlar; vitrin görselleri `Modifier.bleedStart()` ile menünün arkasına taşar. İçerikte solda öğe kalmayınca ← = menü.
* **Ana sayfa vitrini:** görsel tüm ekranın arkasında (`FullBleedBackdrop`, HeroRows.kt), şeritlere inildikçe söner; vitrin noktaları düğmelerin altında çizgi.
* **Filmler/Diziler (`MediaScreen`):** sinematik düzen — odaktaki afişin sahne görseli arkada, üstte logo/meta/özet/oyuncular, araç çubuğu (Sırala, Kategori → `OptionsDialog`), 8 sütun afiş ızgarası (★ puan rozeti, basılı OK = seçenekler).
* **Canlı TV (`LiveScreen`):** solda numaralı kanal listesi (logo, şimdiki program, kalan dk, ilerleme), sağda canlı önizleme (`PreviewPlayer`, ~1,1 sn durunca; tek bağlantı yüzünden kanal açılırken / ekran arkadayken `stopNow()`), program + sıradaki. `NowNextMemo` satır EPG önbelleği (2 dk).
* **Yayın akışı (`GuideScreen`, yeni sekme):** üstte önizleme + program bilgisi, altta 3 saatlik kanal × saat çizelgesi, "şimdi" çizgisi; kenarda ←/→ saati 1 saat kaydırır; OK = yayındaysa izle, ileride ise hatırlatıcı (`eventId = kanal-başlangıç`).
* **Dizi detayı:** başlık artık tam ekran değil; sezon sekmeleri (`SeasonTab`) + 5 sütun bölüm ızgarası (numara rozeti). Film detayında da baş kısım kısaldı (oyuncular hemen altta).
* Kartların odak çerçevesi her yerde mor → turkuaz (`RingBrush` / `FocusRing`).
* Robolectric önizlemelerinde canlı önizleme oynatıcısı kurulmaz (FINGERPRINT kontrolü); PreviewData sahte EPG döndürür (`live`, `guide` önizlemeleri).


## Güncelleme — 2026-10-10 (Claude): akıcılık + arayüz turu (3.0.1, dal `yeni-duzen`)

* **`LiveManager` akışları paylaşılan `StateFlow`** (arka planda bir kez hesaplanır, App örneğine bağlı). Eskiden `getVisibleChannels()` her çağrıda yeni Flow kuruyordu; ekranlar bunu her çizimde çağırdığı için 15 bin kanal her yeniden çizimde baştan işleniyordu. Çağrı yerlerinde `collectAsStateWithLifecycle()` (başlangıç değeri vermeden).
* **`EpgCache.syncIfNeeded` artık beklemez:** XMLTV arka planda iner (12 saatte bir, hata olursa 30 dk sonra tekrar); son indirme zamanı `SharedPreferences("epg-sync")`'te kalıcı. Eskiden indirme sürerken tüm kanal satırları kilitte bekliyordu, ayrıca her açılışta dosya yeniden iniyordu.
* Odak değişimi yalnız ilgili katmanı yeniden çizer: `MediaScreen` → `FocusedArtEffect` + `BackdropLayer`; `LiveScreen` → `LiveSide`.
* Coil `respectCacheHeaders(false)`: "no-cache" diyen afiş sunucularında da disk önbelleği kullanılır.
* **Anında kanal açma:** önizlemede oynayan kanala OK → aynı ExoPlayer tam ekrana devredilir (`PreviewPlayer.handOff` → `LiveHandoff.take` in `LivePlayerScreen`); yeniden bağlanma yok, kutuda 0,6 sn'de görüntü. Canlı oynatıcıda tamponlama halkası + anlaşılır hata mesajları (bağlantı sınırı / ağ).
* **Sol menü genişler:** odak menüdeyken adlar ve profil adıyla 196 dp'ye açılır, içerik soldan kararır. İçerikten ◀ = her zaman SEÇİLİ sekme (`focusProperties { enter = { tabFocus } }` menünün focusGroup'unda; menünün içindeki ayrı bir `enter` çalışmıyordu → aynı hizadaki sekmeye gidip sayfayı değiştiriyordu).
* **Ortam rengi (`components/Ambient.kt`, `rememberAmbient`):** vitrin ışıması görselin baskın canlı renginden (48 px kopya, %18 sitenin moruna yaklaştırılmış).
* **Canlı TV'de rakamla kanal:** rakamları yaz → sağ üstte büyük numara + kanal adı, 1,6 sn sonra o kanala (yoksa sonraki numaraya) odaklanır.
* `displaySmall` = Manrope 28 sp (Ayarlar/Listem başlıkları); Ayarlar/Listem/Ara üst boşlukları üst çubuk kalktığı için küçüldü.
* **TUZAK:** video içeren ekranları (`PlayerView` = SurfaceView) `graphicsLayer { alpha }` içine KOYMA — kutuda görüntü siyah kalıyor (ses geliyor). Sekme geçişindeki solma efekti bu yüzden kaldırıldı.
* Kutuda test notu: yarıda kesilen bir testin açtığı yayın sağlayıcıda bir süre "açık bağlantı" sayılabiliyor (tek bağlantılı hesap) → sonraki deneme veri almaz; ~1 dk bekle.


## Güncelleme — 2026-10-10 (Claude): ana sayfa kaydırma performansı (3.0.2, dal `yeni-duzen`)

* **Ölçüm:** `data/diag/FrameStats.kt` 3 sn'de bir logcat `FitiFrames`: kare sayısı, gecikme p50/p90, aşama ortalamaları (girdi+anim / ölçü / çizim / senk / GL komutu / GPU) ve **kareler arası aralık** (asıl akıcılık: p50=16 iyi, "atlanan" = 20 ms'den uzun aralık). Kutuda: `{"logcat":"clear"}` → tuşlar → `{"logcat":"FitiFrames"}` (scratchpad'de `scroll-test.sh`).
* **Bulgu (SEI Nova kutusu):** önce kare başına GPU ~27 ms + GL ~18 ms, gecikme ~80 ms (≈12 fps). Neden: ana sayfanın arkasında üst üste ~9 tam ekran boyama (pencere zemini, AppRoot zemini, 3 radyal ışıma, görsel, 3 karartma, saydamlık katmanı) + kartlarda `blur()`.
* **Düzeltmeler:** `FullBleedBackdrop` görsel + TEK `drawWithCache` katmanı (karartmalar yalnız gereken bantlara, ışımalar yalnız kendi çevrelerine); aşağı kayınca saydamlık katmanı yerine üstüne zemin rengi, tamamen gizlenince görsel hiç çizilmez; vitrin görseli 1280×720 istenir; pencere zemini kaldırıldı (`window.setBackgroundDrawable(null)`, zemini AppRoot çiziyor); WideCard ve vitrin yedeğindeki `blur()` kaldırıldı; şeritler `graphicsLayer()` içinde; Canlı TV/Yayın akışı köşe ışıması `cornerGlow()` (sınırlı). Sonuç: GPU ~9 ms, gecikme ~39 ms, karelerin çoğu 16 ms aralıkla (atlanan ~%7-15).
* **Kumanda tepkisi:** odak kaydırma animasyonu varsayılan yay (~0,5 sn) yerine `SnappyScroll` (220 ms); AppRoot'ta tüm listeler için `LocalBringIntoViewSpec` (kenardan 24 dp). Ana sayfa vitrin/şerit konumlandırmaları kendi spec'lerinde aynı animasyonu kullanır.
* **KURAL:** zayıf TV'lerde tam ekran katmanları ve `blur()` en büyük maliyet; yeni tam ekran efekt eklerken boyanan alanı sınırla, `graphicsLayer { alpha }` ile tam ekran solma yapma (hem pahalı hem video yüzeyini kesiyor).


## Güncelleme — 2026-10-10 (Claude): arayüz ölçeği (3.1.0, dal `yeni-duzen`)

* **Neden sıkışık görünüyordu:** OwnTV ekran görüntüleriyle piksel karşılaştırması (1920×1080): bizde her öğe ~1,35-1,5 kat büyüktü (kanal satırı 124 px ↔ 82, gövde yazısı 30 px ↔ 22, kart 440 px ↔ 340, düğme 80 px ↔ 56). Aynı ekrana iri öğeler → kalabalık.
* **Çözüm:** `FitifitiTheme` `LocalDensity`'yi `uiScale` ile çarpar (tüm yazı/kart/boşluk tek oranla). Ayar `AppSettings.uiScale` (varsayılan 0,75; Ayarlar → Görünüm → Arayüz boyutu: Kompakt 0,7 · Ferah 0,75 · Orta 0,85 · Büyük 1). Diyaloglar da aynı yoğunluğu alır.
* **Dikkat:** `LocalConfiguration.screenHeightDp/screenWidthDp` ölçeği BİLMEZ → ekran boyutuna göre hesapta `ui.theme.screenHeightDp()` kullan. Video yüzeyi (PlayerView) piksel bazlı, etkilenmez.


## Güncelleme — 2026-10-10 (Claude): fragman düzeltmesi + "liquid glass" (3.2.0, dal `yeni-duzen`)

* **Fragman çıkmıyordu:** `TrailerRepository.find` sağlayıcının ham adını gönderiyordu ("Konferans - The Conference (2023)"); site `/api/trailer` bu adla `null` dönüyor, sade adla buluyor. Artık sırayla `splitTitle` Türkçe adı → özgün ad → ham ad denenir (ilk bulunan; ağ hatası önbelleğe yazılmaz). İlk açılışta MP4 sitede hazırlanırken ~25 sn sürebilir, sonra anında.
* **`Diag.log` artık logcat'e de yazar** (`FitiDiag`): kutu ajanıyla `{"logcat":"FitiDiag"}` (fragman, ekran geçişleri, oynatıcı olayları).
* **Cam görünümü (`components/Glass.kt`, `Modifier.glass(shape, strength, tint)`):** gerçek arka plan bulanıklaştırması YOK (zayıf GPU'da her karede pahalı); koyu yarı saydam renk + azalan beyaz dolgu + üst parlama + alt yansıma + sol üstten parlayan kenar. Çocukları kırpmaz (odak büyümesi/parıltı kesilmez). Kullananlar: `Btn` (Primary/Secondary), `IconAction`, seçili olmayan `Chip`, sol menü (açılınca koyu tonu artar), Filmler/Canlı TV araç çubuğu çipleri. Odakta hâlâ düz beyaz (TV'de odak net görünsün). Kaydırma ölçümü değişmedi.


## Güncelleme — 2026-10-10 (Claude): vitrin tipografisi ve düğmeler (3.2.1, dal `yeni-duzen`)

* **Font:** tüm uygulama Manrope (variable font; 450/500/600/700/800 ağırlıkları `Manrope` ailesinde). `Typography` stillerinin hepsi Manrope; `FitifitiTheme` stil verilmeyen `Text`'ler için `ProvideTextStyle(bodyMedium)`.
* **Ana sayfa vitrini (`HeroInfo` / `HeroText`):** sabit yuvalar — etiket 20dp, başlık 96dp (logo da yazı da alta hizalı, logo gelince çapraz geçiş), meta 24dp, özet her zaman 3 satır yer (`minLines = 3`). Vitrin dönerken yazı katmanı `AnimatedContent` ile solar; düğmeler ve noktalar yerinden oynamaz. Başlık/özet hafif gölgeli.
* **Puan:** "TMDB 7.1" düz yazısı yerine `StarRating` rozeti (★ 7.1); `MetaRow(..., rating = …)` en başa koyar. Vitrin, Filmler sinematik bilgi ve detay sayfaları bunu kullanır.
* **Düğmeler:** odakta altta beliren yazılı yuvarlak ikonlar kaldırıldı (kayıyor gibi görünüyordu). Vitrinde: Oynat/Devam et + "Detaylar"/"Bölümler" (cam) + yazısız +/✓. Film detayında "Baştan · Listem/Listemde · İzledim/İzlendi" yazılı cam düğmeler; dizide "Oynat · S1 B1" / "Devam et · S1 B3". `IconAction(showLabel = false)` ile alt yazı kapatılır (fragman ses düğmesi).
