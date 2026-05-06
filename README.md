# AppRyous — Native Android Kotlin

> ŘΨØŬ Stream — Native Android port dari [ryoustream/ryoustream](https://github.com/ryoustream/ryoustream) branch `epsilon`

Pure Kotlin Android app. **Tidak ada WebView. Tidak ada web code.** Semua dibangun native dengan Android SDK + ExoPlayer Media3.

---

## Alur Kerja

```
File.mkv (SD Card Termux)
    │
    ▼
Backend Python (server.py, port 8080)
  └─ Serve raw bytes via HTTP Range 206
  └─ Convert ASS/SRT → VTT on-the-fly (external subtitle)
  └─ Parse XML Matroska chapters (/api/chapters)
    │
    ▼
Cloudflare Tunnel (cloudflared)
  └─ URL disimpan di GitHub Gist
    │
    ▼
App (AppRyous.apk)
  └─ SplashActivity → fetch Gist URL → resolve tunnel
  └─ BackendRepository → Retrofit + OkHttp (HTTP calls)
    │
    ▼
ExoPlayer / Media3 (MatroskaExtractor)
  └─ OkHttpDataSource → HTTP Range request ke backend
  └─ MatroskaExtractor → parse MKV container NATIVE
       ├─ Video tracks (H.264, H.265, AV1, VP9)
       ├─ Audio tracks (AAC, AC3, DTS, FLAC, Opus...)
       ├─ Embedded subtitle (SubRip, ASS, WebVTT, PGS)
       ├─ Embedded chapters (Matroska Editions)
       └─ External subtitle (.vtt dari backend)
    │
    ▼
PlayerActivity → render video
```

**Backend TIDAK transcode/remux apapun** — hanya serve bytes. ExoPlayer yang parse semua isi MKV natively di sisi app.

---

## Setup & Build

### Prerequisites

- Android Studio Hedgehog 2023.1.1+ atau Ladybug 2024.2.1+
- JDK 17
- Android SDK 35, min SDK 26 (Android 8.0)

### Clone & Build

```bash
git clone https://github.com/ryoustream/appryous.git
cd appryous
# Buka di Android Studio → Build → Make Project
```

### Konfigurasi Gist

Gist ID dan username sudah di-hardcode di `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "GIST_ID",      "\"b324efa90678d7fa4f605c8c425a6596\"")
buildConfigField("String", "GIST_USERNAME", "\"ryoustream\"")
```

Gist harus berisi JSON:
```json
{ "url": "https://xxx.trycloudflare.com", "updated": "2025-01-01" }
```

atau plain URL saja.

---

## Struktur Proyek

```
app/src/main/java/com/ryou/appryous/
├── data/
│   ├── model/       ← Data classes (AnimeEntry, Episode, Chapter, SubtitleTrack...)
│   ├── remote/      ← Retrofit API interfaces + MkvMediaItemBuilder + NetworkModule
│   ├── local/       ← SharedPreferences stores (Settings, History, Position, LibCache)
│   └── repository/  ← GistRepository, BackendRepository
├── ui/
│   ├── splash/      ← SplashActivity (resolve Gist URL)
│   ├── main/        ← MainActivity + BottomNavigation
│   ├── home/        ← HomeFragment (hero carousel, continue watching, rows)
│   ├── search/      ← SearchFragment (real-time search)
│   ├── archive/     ← ArchiveFragment (sort + filter + pagination)
│   ├── category/    ← CategoryFragment (15 genre chips + grid)
│   ├── detail/      ← DetailActivity (info + episode list)
│   ├── player/      ← PlayerActivity + MediaPlaybackService
│   ├── settings/    ← SettingsFragment
│   └── about/       ← AboutFragment
└── util/
    ├── BaseViewModel.kt
    ├── Extensions.kt
    └── ScanPoller.kt
```

---

## Tech Stack

| Kebutuhan | Library |
|---|---|
| Video player | **Media3 ExoPlayer** (MatroskaExtractor built-in) |
| MKV streaming | **OkHttpDataSource** + HTTP Range 206 |
| Networking | **Retrofit** + **OkHttp** |
| JSON parsing | **Moshi** + KSP codegen |
| Image loading | **Glide** |
| Navigation | **Navigation Component** |
| DI | Manual AppContainer (no Hilt) |
| Local store | **SharedPreferences** |

---

## Yang Berbeda dari Versi Web

| Web (Epsilon) | Android (AppRyous) |
|---|---|
| Vidstack player | Media3 ExoPlayer |
| ES Modules + router.js | Navigation Component |
| localStorage | SharedPreferences |
| fetch() API | Retrofit + OkHttp |
| GitHub Pages PWA | Native APK |
| HTML/CSS/JS | Kotlin + XML layouts |
| anime.js | MotionLayout / ValueAnimator |
| categories.json | Hardcoded + string resources |
| Service Worker | WorkManager |

---

## Catatan MKV

ExoPlayer Media3 membaca MKV **natively** via `MatroskaExtractor`:

- ✅ Embedded subtitle (SRT, ASS inline, WebVTT)
- ✅ Multi audio track (JP dub, CN dub, dll dalam 1 file)
- ✅ Embedded chapter markers (Matroska Editions)
- ✅ Video: H.264, H.265/HEVC, AV1, VP9, VP8
- ✅ Audio: AAC, AC3, E-AC3, DTS, FLAC, Opus, TrueHD, Vorbis, MP3
- ✅ External subtitle VTT (dari backend conversion)
- ✅ External chapter XML (via /api/chapters)
- ⚠️ PGS subtitle (image-based, dari Blu-ray rip) — render basic
- ⚠️ ASS complex styling — di-strip oleh server saat convert ke VTT

Backend cukup running di Termux dan tunnel aktif. Tidak perlu ffmpeg, tidak perlu transcode.
