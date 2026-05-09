package com.ryou.appryous.player.mpv

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import `is`.xyz.mpv.MPVLib

/**
 * SurfaceView untuk mpv — delegate ke is.xyz.mpv.MPVLib (JNI correct).
 *
 * Cara pakai di layout XML:
 *   <com.ryou.appryous.player.mpv.MpvView
 *       android:id="@+id/mpv_view"
 *       android:layout_width="match_parent"
 *       android:layout_height="match_parent" />
 */
class MpvView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val tag = "MpvView"

    fun initialize(configDir: String, cacheDir: String, logLevel: String = "warn") {
        MPVLib.create(context, logLevel)
        MPVLib.init()

        // ── Network / Streaming ───────────────────────────────────────────────
        // Backend serve via Cloudflare tunnel (HTTPS) atau LAN (HTTP)
        MPVLib.setOptionString("network-timeout",        "15")
        MPVLib.setOptionString("tls-verify",             "no")   // Cloudflare self-signed OK
        MPVLib.setOptionString("cache",                  "yes")
        MPVLib.setOptionString("cache-secs",             "30")
        MPVLib.setOptionString("demuxer-max-bytes",      "50MiB")
        MPVLib.setOptionString("demuxer-max-back-bytes", "10MiB")
        MPVLib.setOptionString("demuxer-readahead-secs", "20")
        // User-agent agar backend tidak reject request
        MPVLib.setOptionString("user-agent", "AppRyous/1.0 (Android; mpv)")

        // ── Video ─────────────────────────────────────────────────────────────
        MPVLib.setOptionString("vo",             "gpu")
        MPVLib.setOptionString("gpu-context",    "android")
        MPVLib.setOptionString("opengl-es",      "yes")
        MPVLib.setOptionString("hwdec",          "mediacodec-copy")
        MPVLib.setOptionString("hwdec-codecs",   "h264,hevc,vp9,vp8,av1")
        MPVLib.setOptionString("hwdec-extra-frames", "4")

        // ── Audio ─────────────────────────────────────────────────────────────
        MPVLib.setOptionString("ao",             "audiotrack,opensles")
        MPVLib.setOptionString("audio-channels", "stereo")
        MPVLib.setOptionString("af",             "scaletempo2=min-speed=0.25:max-speed=4")

        // ── Subtitle (libass penuh) ───────────────────────────────────────────
        MPVLib.setOptionString("sub-ass",           "yes")
        MPVLib.setOptionString("sub-ass-override",  "no")   // hormati styling ASS asli
        MPVLib.setOptionString("sub-scale-by-window", "no")
        MPVLib.setOptionString("sub-scale",         "0.9")
        MPVLib.setOptionString("sub-delay",         "0")
        MPVLib.setOptionString("sub-font",          "sans-serif")
        MPVLib.setOptionString("sub-font-size",     "44")
        MPVLib.setOptionString("sub-color",         "#FFFFFFFF")
        MPVLib.setOptionString("sub-border-color",  "#FF000000")
        MPVLib.setOptionString("sub-border-size",   "3")
        MPVLib.setOptionString("sub-shadow-offset",  "1")
        MPVLib.setOptionString("slang",             "id,en,ja")

        // ── Chapter ───────────────────────────────────────────────────────────
        MPVLib.setOptionString("chapter-merge-threshold", "100")

        // ── Config & cache dir ────────────────────────────────────────────────
        MPVLib.setOptionString("config-dir", configDir)
        MPVLib.setOptionString("cache-dir",  cacheDir)

        // ── Display FPS sync ──────────────────────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.mode?.refreshRate?.let {
                MPVLib.setOptionString("display-fps-override", it.toInt().toString())
            }
        }

        holder.addCallback(this)
    }

    fun destroy() {
        holder.removeCallback(this)
        MPVLib.destroy()
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    /** Load URL dan mulai play. episode.src sudah ABSOLUTE dari backend. */
    fun playFile(url: String) {
        MPVLib.command(arrayOf("loadfile", url))
    }

    /** Load URL dengan mpv options tambahan. */
    fun playFile(url: String, options: Map<String, String>) {
        val optStr = options.entries.joinToString(",") { "${it.key}=${it.value}" }
        MPVLib.command(arrayOf("loadfile", url, "replace", "0", optStr))
    }

    var paused: Boolean
        get() = MPVLib.getPropertyBoolean("pause")
        set(v) { MPVLib.setPropertyBoolean("pause", v) }

    val timePos: Double
        get() = MPVLib.getPropertyDouble("time-pos").coerceAtLeast(0.0)

    val duration: Double
        get() = MPVLib.getPropertyDouble("duration").coerceAtLeast(0.0)

    fun seekTo(seconds: Double, precise: Boolean = false) {
        val mode = if (precise) "absolute+exact" else "absolute"
        MPVLib.command(arrayOf("seek", seconds.toString(), mode))
    }

    fun seekRelative(seconds: Int) {
        MPVLib.command(arrayOf("seek", seconds.toString(), "relative"))
    }

    var volume: Int
        get() = MPVLib.getPropertyInt("volume")
        set(v) { MPVLib.setPropertyInt("volume", v.coerceIn(0, 100)) }

    var playbackSpeed: Double
        get() = MPVLib.getPropertyDouble("speed")
        set(v) { MPVLib.setPropertyDouble("speed", v.coerceIn(0.25, 4.0)) }

    // ── Subtitle ──────────────────────────────────────────────────────────────

    /**
     * Tambah external subtitle.
     * Backend sudah serve subtitle sebagai absolute URL.
     * Untuk ASS: request ?raw=1 agar libass baca langsung tanpa konversi ke VTT.
     */
    fun addSubtitle(url: String, title: String, lang: String, select: Boolean = false) {
        val flags = if (select) "select" else "auto"
        MPVLib.command(arrayOf("sub-add", url, flags, title, lang))
    }

    var subtitleTrack: Int
        get() = MPVLib.getPropertyInt("sid")
        set(v) { MPVLib.setPropertyInt("sid", v) }

    var subtitleEnabled: Boolean
        get() = MPVLib.getPropertyString("sub-visibility") == "yes"
        set(v) { MPVLib.setPropertyString("sub-visibility", if (v) "yes" else "no") }

    // ── Audio ─────────────────────────────────────────────────────────────────

    var audioTrack: Int
        get() = MPVLib.getPropertyInt("aid")
        set(v) { MPVLib.setPropertyInt("aid", v) }

    // ── Chapters ──────────────────────────────────────────────────────────────

    val chapterCount: Int get() = MPVLib.getPropertyInt("chapter-list/count")

    var currentChapter: Int
        get() = MPVLib.getPropertyInt("chapter")
        set(v) { MPVLib.setPropertyInt("chapter", v) }

    /** Baca embedded chapters dari MKV container (via Matroska Editions). */
    fun readEmbeddedChapters(): List<Pair<Double, String>> {
        val count = chapterCount
        if (count <= 0) return emptyList()
        return (0 until count).mapNotNull { i ->
            val time  = MPVLib.getPropertyDouble("chapter-list/$i/time")
            val title = MPVLib.getPropertyString("chapter-list/$i/title") ?: "Chapter ${i + 1}"
            if (time >= 0) Pair(time, title) else null
        }
    }

    fun nextChapter() { MPVLib.command(arrayOf("add", "chapter", "1")) }
    fun prevChapter() { MPVLib.command(arrayOf("add", "chapter", "-1")) }

    // ── Track list ────────────────────────────────────────────────────────────

    fun readTrackList(): List<MpvTrack> {
        val count = MPVLib.getPropertyInt("track-list/count")
        return (0 until count).map { i ->
            MpvTrack(
                id           = MPVLib.getPropertyInt("track-list/$i/id"),
                type         = MPVLib.getPropertyString("track-list/$i/type")    ?: "",
                title        = MPVLib.getPropertyString("track-list/$i/title")   ?: "",
                lang         = MPVLib.getPropertyString("track-list/$i/lang")    ?: "",
                codec        = MPVLib.getPropertyString("track-list/$i/codec")   ?: "",
                selected     = MPVLib.getPropertyBoolean("track-list/$i/selected"),
                external     = MPVLib.getPropertyBoolean("track-list/$i/external"),
                defaultTrack = MPVLib.getPropertyBoolean("track-list/$i/default")
            )
        }
    }

    // ── SurfaceHolder.Callback ────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(tag, "surfaceCreated")
        MPVLib.attachSurface(holder.surface)
        MPVLib.setPropertyString("vo", "gpu")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        Log.d(tag, "surfaceChanged ${w}x${h}")
        MPVLib.setPropertyString("android-surface-size", "${w}x${h}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(tag, "surfaceDestroyed")
        MPVLib.detachSurface()
    }
}

data class MpvTrack(
    val id: Int, val type: String, val title: String,
    val lang: String, val codec: String,
    val selected: Boolean, val external: Boolean, val defaultTrack: Boolean
) {
    val isVideo    get() = type == "video"
    val isAudio    get() = type == "audio"
    val isSubtitle get() = type == "sub"
    val displayLabel: String get() = when {
        title.isNotBlank() && lang.isNotBlank() -> "$title [$lang]"
        title.isNotBlank()                       -> title
        lang.isNotBlank()                        -> lang.uppercase()
        else                                     -> "Track $id"
    }.let { if (external) "$it (ext)" else it }
}
