package com.ryou.appryous.player.mpv

import android.content.Context
import com.ryou.appryous.data.model.AppSettings
import com.ryou.appryous.data.model.Episode
import com.ryou.appryous.data.model.SubtitleTrack

/**
 * High-level helper untuk configure mpv sebelum playback.
 *
 * Menggantikan MkvMediaItemBuilder.kt (ExoPlayer).
 *
 * Alur MKV lengkap yang ditangani:
 *
 *   backend serve: File.mkv via HTTP Range 206
 *       │
 *       ▼
 *   MpvView.playFile(url)
 *       │
 *       ▼
 *   libmpv.so demux MKV container:
 *   ┌─────────────────────────────────────────────────┐
 *   │  Video tracks   → MediaCodec (hardware decode)  │
 *   │  Audio tracks   → AudioTrack (semua codec)      │
 *   │  Sub embedded   → libass render PENUH           │
 *   │    ├── ASS/SSA  : full styling, karaoke, anim   │
 *   │    ├── SubRip   : styled text                   │
 *   │    ├── WebVTT   : styled text                   │
 *   │    └── PGS      : image-based (Blu-ray)         │
 *   │  Font attachments → auto-load ke libass         │
 *   │  Chapter markers → baca via readEmbeddedChapters│
 *   └─────────────────────────────────────────────────┘
 *       │
 *       ▼
 *   External sub (jika ada):
 *   ├── .ass/.ssa → request ke backend ?raw=1 (TIDAK dikonversi ke VTT)
 *   │               libass render penuh
 *   └── .vtt/.srt → request normal
 *                   mpv render sebagai teks biasa
 */
object MpvPlayerHelper {

    /**
     * Load episode ke MpvView dengan semua track terkonfigurasi.
     *
     * @param view         MpvView yang sudah di-initialize
     * @param episode      Data episode dari backend
     * @param settings     AppSettings user (subtitle, lang, volume)
     * @param startSeconds Posisi mulai (untuk continue watching)
     */
    fun loadEpisode(
        view:         MpvView,
        episode:      Episode,
        settings:     AppSettings,
        startSeconds: Double = 0.0
    ) {
        val options = mutableMapOf<String, String>()

        // Mulai dari posisi tertentu (continue watching)
        if (startSeconds > 5.0) {
            options["start"] = formatTime(startSeconds)
        }

        // Subtitle language preference
        if (settings.subtitleEnabled) {
            options["slang"] = when (settings.descLang) {
                "ja" -> "ja,id,en"
                "en" -> "en,id,ja"
                else -> "id,en,ja"  // default: Indonesia dulu
            }
        } else {
            // Nonaktifkan subtitle otomatis
            options["sid"] = "no"
        }

        // Volume dari settings (0.0–1.0 → 0–100)
        view.volume = (settings.volume * 100).toInt()

        // Load file
        view.playFile(episode.src, options)
    }

    /**
     * Tambah semua external subtitle dari episode ke mpv.
     * Dipanggil setelah EVENT_FILE_LOADED agar bisa lihat track mana
     * yang sudah ada (embedded) sebelum tambah external.
     *
     * Strategi per format:
     *  - .ass/.ssa → request backend dengan ?raw=1 agar serve raw file
     *                libass baca dan render PENUH (karaoke, posisi, font)
     *  - .vtt      → load langsung, mpv parse WebVTT
     *  - .srt      → load langsung, mpv parse SubRip
     *
     * @param view         MpvView yang sedang play
     * @param subtitles    List subtitle dari backend
     * @param preferredLang Lang yang dipilih user
     * @param baseUrl      Base URL tunnel (untuk build URL raw)
     */
    fun addExternalSubtitles(
        view:          MpvView,
        subtitles:     List<SubtitleTrack>,
        preferredLang: String = "id",
        baseUrl:       String = ""
    ) {
        subtitles.forEachIndexed { index, track ->
            val url = buildSubUrl(track.src, baseUrl)
            val selectThis = track.default ||
                (index == 0 && subtitles.none { it.default }) ||
                (preferredLang.isNotBlank() && track.lang == preferredLang)

            view.addSubtitle(
                url    = url,
                title  = track.label,
                lang   = track.lang,
                select = selectThis
            )
        }
    }

    /**
     * Build URL subtitle — untuk ASS/SSA tambahkan ?raw=1
     * agar backend tidak convert ke VTT.
     *
     * Dengan ?raw=1:
     *  server.py skip ASS→VTT conversion, serve file mentah
     *  libmpv + libass baca file ASS langsung → render PENUH
     */
    /**
     * Build subtitle URL.
     *
     * subtitle.src dari backend SUDAH absolute URL (backend inject base_url).
     * Hanya tambah ?raw=1 untuk .ass/.ssa agar libass baca langsung tanpa
     * konversi ke VTT oleh backend.
     */
    private fun buildSubUrl(src: String, baseUrl: String): String {
        // src sudah absolute — tidak perlu prepend baseUrl
        // baseUrl param dipertahankan untuk backward compat tapi tidak dipakai
        val url = src.trim()
        val isAss = url.contains(".ass", ignoreCase = true) ||
                    url.contains(".ssa", ignoreCase = true)
        return if (isAss) {
            val sep = if (url.contains("?")) "&" else "?"
            "${url}${sep}raw=1"
        } else {
            url
        }
    }

    /**
     * Configure mpv untuk handle chapters.
     *
     * mpv otomatis baca embedded chapters dari MKV (Matroska Editions).
     * Setelah file loaded, panggil [MpvView.readEmbeddedChapters] untuk
     * mendapatkan list chapter dan tampilkan di UI.
     *
     * Jika MKV tidak punya embedded chapters, gunakan XML dari backend
     * (/api/chapters) sebagai fallback via [BackendRepository.getChapters].
     */
    fun configureChapters(view: MpvView) {
        // mpv config sudah di set di MpvView.initialize()
        // chapter-merge-threshold=100 — merge chapter yang terlalu dekat
        // Tidak ada config tambahan di sini
    }

    /**
     * Set font directory untuk libass.
     * mpv secara otomatis extract font attachments dari MKV container.
     * Ini tambahan untuk font custom dari /api/fonts (dari SD Card).
     *
     * @param fontsDir Path ke direktori font lokal (bisa dari cacheDir)
     */
    fun setFontsDir(view: MpvView, fontsDir: String) {
        MPVLib.setPropertyString("sub-fonts-dir", fontsDir)
    }

    /**
     * Download dan cache font files dari backend ke local dir.
     * Dipanggil satu kali saat app start atau setelah scan.
     *
     * Font dari SD Card/Fonts/ folder (list via /api/fonts)
     * di-download dan disimpan di cacheDir/fonts/
     * mpv kemudian load semua font dari direktori tersebut.
     */
    suspend fun cacheFontsLocally(
        context:   Context,
        fontUrls:  List<String>,
        cacheDir:  java.io.File
    ): java.io.File {
        val fontsDir = java.io.File(cacheDir, "fonts").also { it.mkdirs() }

        fontUrls.forEach { url ->
            val filename = url.substringAfterLast("/")
            val dest     = java.io.File(fontsDir, filename)
            if (dest.exists()) return@forEach  // skip jika sudah ada

            try {
                val bytes = kotlinx.coroutines.Dispatchers.IO.run {
                    java.net.URL(url).readBytes()
                }
                dest.writeBytes(bytes)
            } catch (e: Exception) {
                android.util.Log.w("MpvPlayerHelper", "Gagal download font $url: ${e.message}")
            }
        }

        return fontsDir
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Konversi detik ke format HH:MM:SS.mmm untuk mpv start option */
    private fun formatTime(seconds: Double): String {
        val h   = seconds.toLong() / 3600
        val m   = (seconds.toLong() % 3600) / 60
        val s   = seconds % 60
        return "%02d:%02d:%06.3f".format(h, m, s)
    }
}
