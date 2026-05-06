package com.ryou.appryous.player.mpv

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager

/**
 * SurfaceView yang dipakai mpv untuk render video.
 *
 * Di-adapt dari mpv-android MPVView.kt.
 * Cara pakai di XML layout:
 *
 *   <com.ryou.appryous.player.mpv.MpvView
 *       android:id="@+id/mpv_view"
 *       android:layout_width="match_parent"
 *       android:layout_height="match_parent" />
 *
 * Di Activity/Fragment:
 *   binding.mpvView.initialize(filesDir.path, cacheDir.path)
 *   binding.mpvView.playFile(url)
 */
class MpvView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val tag = "MpvView"

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Initialize mpv dan set opsi dasar.
     * Panggil sebelum [playFile].
     *
     * @param configDir Path ke dir config mpv (biasanya filesDir)
     * @param cacheDir  Path ke dir cache (biasanya cacheDir)
     * @param logLevel  Level log mpv ("no", "fatal", "error", "warn", "info", "v", "debug")
     */
    fun initialize(configDir: String, cacheDir: String, logLevel: String = "warn") {
        MPVLib.create(context, logLevel)
        MPVLib.init()

        // Network — streaming via HTTP/HTTPS (tunnel Cloudflare)
        MPVLib.setOptionString("network-timeout",         "15")
        MPVLib.setOptionString("cache",                   "yes")
        MPVLib.setOptionString("cache-secs",              "30")
        MPVLib.setOptionString("demuxer-max-bytes",       "50MiB")
        MPVLib.setOptionString("demuxer-max-back-bytes",  "10MiB")
        // Buffer untuk streaming — hindari stutter di tunnel yang kadang lambat
        MPVLib.setOptionString("demuxer-readahead-secs",  "20")

        // Video
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        // Hardware decode — pakai MediaCodec Android untuk efisiensi baterai
        MPVLib.setOptionString("hwdec", "mediacodec-copy")
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,vp9,vp8,av1")
        // Fallback ke software decode jika hardware gagal
        MPVLib.setOptionString("hwdec-extra-frames", "4")

        // Audio
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("audio-channels", "stereo")
        // Volume normalization — cocok untuk file MKV dengan volume tidak konsisten
        MPVLib.setOptionString("af", "scaletempo2=min-speed=0.25:max-speed=4")

        // ── SUBTITLE — Ini yang bikin mpv superior ───────────────────────────
        // Aktifkan libass untuk render ASS/SSA
        MPVLib.setOptionString("sub-ass",           "yes")
        // JANGAN override styling dari file ASS — tampilkan apa adanya
        MPVLib.setOptionString("sub-ass-override",  "no")
        // Scale subtitle sesuai ukuran video
        MPVLib.setOptionString("sub-scale-by-window", "no")
        // Font scale normal
        MPVLib.setOptionString("sub-scale",         "0.9")
        // Sub delay default 0
        MPVLib.setOptionString("sub-delay",         "0")
        // Font fallback jika font ASS tidak tersedia di device
        MPVLib.setOptionString("sub-font",          "sans-serif")
        MPVLib.setOptionString("sub-font-size",     "44")
        MPVLib.setOptionString("sub-color",         "#FFFFFFFF")
        MPVLib.setOptionString("sub-border-color",  "#FF000000")
        MPVLib.setOptionString("sub-border-size",   "3")
        MPVLib.setOptionString("sub-shadow-offset",  "1")
        // Auto-select subtitle berdasarkan bahasa (dioverride oleh MpvPlayerHelper)
        MPVLib.setOptionString("slang",             "id,en,ja")

        // Chapters
        // mpv baca embedded chapter dari MKV container otomatis via --chapter-merge-threshold
        MPVLib.setOptionString("chapter-merge-threshold", "100")

        // Cache dan config dir
        MPVLib.setOptionString("config-dir", configDir)
        MPVLib.setOptionString("cache-dir",  cacheDir)

        // Screenshot (opsional — ke galeri)
        MPVLib.setOptionString("screenshot-directory", "~~desktop")

        // Refresh rate — sync dengan display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = context.display
            display?.mode?.refreshRate?.let { rate ->
                MPVLib.setOptionString("display-fps-override", rate.toInt().toString())
            }
        }

        holder.addCallback(this)
    }

    /** Panggil saat Activity/Fragment onDestroy */
    fun destroy() {
        holder.removeCallback(this)
        MPVLib.destroy()
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    /**
     * Load dan mulai putar URL.
     * Bisa berupa:
     *  - https://xxx.trycloudflare.com/media/Videos/Naruto/01.mkv
     *  - URL lain yang didukung mpv (HTTP/HTTPS/dll)
     *
     * mpv akan:
     *  1. Buka koneksi HTTP ke backend
     *  2. Demux MKV container (termasuk semua embedded tracks)
     *  3. Decode video via MediaCodec (hardware)
     *  4. Render subtitle via libass (full ASS support)
     */
    fun playFile(url: String) {
        MPVLib.command(arrayOf("loadfile", url))
    }

    /**
     * Load file dengan opsi tambahan.
     * @param options Map dari mpv property → value
     * Contoh: mapOf("start" to "00:01:30", "sub-file" to subtitleUrl)
     */
    fun playFile(url: String, options: Map<String, String>) {
        val optStr = options.entries.joinToString(",") { "${it.key}=${it.value}" }
        MPVLib.command(arrayOf("loadfile", url, "replace", "0", optStr))
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    var paused: Boolean
        get() = MPVLib.getPropertyBoolean("pause")
        set(v) { MPVLib.setPropertyBoolean("pause", v) }

    /** Posisi saat ini dalam detik */
    val timePos: Double
        get() = MPVLib.getPropertyDouble("time-pos").coerceAtLeast(0.0)

    /** Durasi total dalam detik (0 jika belum diketahui) */
    val duration: Double
        get() = MPVLib.getPropertyDouble("duration").coerceAtLeast(0.0)

    /** Seek ke posisi dalam detik */
    fun seekTo(seconds: Double, precise: Boolean = false) {
        val mode = if (precise) "absolute+exact" else "absolute"
        MPVLib.command(arrayOf("seek", seconds.toString(), mode))
    }

    /** Seek relatif dari posisi sekarang (bisa negatif) */
    fun seekRelative(seconds: Int) {
        MPVLib.command(arrayOf("seek", seconds.toString(), "relative"))
    }

    /** Volume 0–100 */
    var volume: Int
        get() = MPVLib.getPropertyInt("volume")
        set(v) { MPVLib.setPropertyInt("volume", v.coerceIn(0, 100)) }

    /** Speed playback (0.25–4.0) */
    var playbackSpeed: Double
        get() = MPVLib.getPropertyDouble("speed")
        set(v) { MPVLib.setPropertyDouble("speed", v.coerceIn(0.25, 4.0)) }

    // ── Subtitle ──────────────────────────────────────────────────────────────

    /**
     * Tambah external subtitle file.
     * Backend serve ASS as-is (raw=1) — libass render penuh tanpa konversi.
     * Untuk VTT, load langsung tanpa raw flag.
     *
     * @param url   URL ke subtitle file
     * @param title Label yang tampil di track selector
     * @param lang  Language code ("id", "en", "ja")
     * @param select Langsung pilih track ini
     */
    fun addSubtitle(url: String, title: String, lang: String, select: Boolean = false) {
        val flags = if (select) "select" else "auto"
        MPVLib.command(arrayOf("sub-add", url, flags, title, lang))
    }

    /** ID track subtitle yang aktif (-1 = tidak ada) */
    var subtitleTrack: Int
        get() = MPVLib.getPropertyInt("sid")
        set(v) { MPVLib.setPropertyInt("sid", v) }

    /** Aktif/nonaktifkan subtitle */
    var subtitleEnabled: Boolean
        get() = MPVLib.getPropertyString("sub-visibility") == "yes"
        set(v) { MPVLib.setPropertyString("sub-visibility", if (v) "yes" else "no") }

    // ── Audio Track ───────────────────────────────────────────────────────────

    /** ID audio track aktif */
    var audioTrack: Int
        get() = MPVLib.getPropertyInt("aid")
        set(v) { MPVLib.setPropertyInt("aid", v) }

    // ── Chapters ──────────────────────────────────────────────────────────────

    /** Jumlah chapter (0 jika tidak ada) */
    val chapterCount: Int
        get() = MPVLib.getPropertyInt("chapter-list/count")

    /** Chapter aktif saat ini (0-based index, -1 jika tidak ada) */
    var currentChapter: Int
        get() = MPVLib.getPropertyInt("chapter")
        set(v) { MPVLib.setPropertyInt("chapter", v) }

    /**
     * Baca semua chapter dari MKV container.
     * Data ini tersedia langsung setelah file loaded (EVENT_FILE_LOADED).
     * Tidak perlu request ke /api/chapters jika chapter embedded dalam MKV.
     *
     * @return List<Pair<timeSeconds, title>>
     */
    fun readEmbeddedChapters(): List<Pair<Double, String>> {
        val count = chapterCount
        if (count == 0) return emptyList()
        return (0 until count).mapNotNull { i ->
            val time  = MPVLib.getPropertyDouble("chapter-list/$i/time")
            val title = MPVLib.getPropertyString("chapter-list/$i/title") ?: "Chapter ${i + 1}"
            if (time >= 0) Pair(time, title) else null
        }
    }

    /** Loncat ke chapter berikutnya */
    fun nextChapter()     { MPVLib.command(arrayOf("add", "chapter", "1")) }

    /** Loncat ke chapter sebelumnya */
    fun prevChapter()     { MPVLib.command(arrayOf("add", "chapter", "-1")) }

    // ── Track Info ────────────────────────────────────────────────────────────

    /** Baca semua track (video/audio/subtitle) dari MKV */
    fun readTrackList(): List<MpvTrack> {
        val count = MPVLib.getPropertyInt("track-list/count")
        return (0 until count).map { i ->
            MpvTrack(
                id       = MPVLib.getPropertyInt("track-list/$i/id"),
                type     = MPVLib.getPropertyString("track-list/$i/type") ?: "",
                title    = MPVLib.getPropertyString("track-list/$i/title") ?: "",
                lang     = MPVLib.getPropertyString("track-list/$i/lang") ?: "",
                codec    = MPVLib.getPropertyString("track-list/$i/codec") ?: "",
                selected = MPVLib.getPropertyBoolean("track-list/$i/selected"),
                external = MPVLib.getPropertyBoolean("track-list/$i/external"),
                defaultTrack = MPVLib.getPropertyBoolean("track-list/$i/default")
            )
        }
    }

    // ── SurfaceHolder.Callback ────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(tag, "surfaceCreated")
        MPVLib.attachSurface(holder.surface)
        // Trigger video reconfig agar mpv render ke surface baru
        MPVLib.setPropertyString("vo", "gpu")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(tag, "surfaceChanged $width×$height")
        MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(tag, "surfaceDestroyed")
        MPVLib.detachSurface()
    }
}

/** Info satu track (video/audio/subtitle) dari MKV */
data class MpvTrack(
    val id:           Int,
    val type:         String,   // "video", "audio", "sub"
    val title:        String,
    val lang:         String,
    val codec:        String,
    val selected:     Boolean,
    val external:     Boolean,
    val defaultTrack: Boolean
) {
    val isVideo:    Boolean get() = type == "video"
    val isAudio:    Boolean get() = type == "audio"
    val isSubtitle: Boolean get() = type == "sub"

    /** Label untuk UI track selector */
    val displayLabel: String get() = when {
        title.isNotBlank() && lang.isNotBlank() -> "$title [$lang]"
        title.isNotBlank()                       -> title
        lang.isNotBlank()                        -> lang.uppercase()
        else                                     -> "Track $id"
    }.let { if (external) "$it (external)" else it }
}
