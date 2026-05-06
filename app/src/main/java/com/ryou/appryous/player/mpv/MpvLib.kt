package com.ryou.appryous.player.mpv

import android.content.Context
import android.view.Surface

/**
 * JNI wrapper untuk libmpv.so
 *
 * Di-adapt dari mpv-android (https://github.com/mpv-android/mpv-android)
 * Sumber: app/src/main/java/is/xyz/mpv/MPVLib.kt
 *
 * libmpv.so di-load dari jniLibs/ — sudah pre-built, tidak perlu NDK compilation.
 * Download via scripts/setup-mpv.sh atau GitHub Actions workflow.
 *
 * Alur MKV:
 *   URL tunnel (https://xxx.trycloudflare.com/media/Videos/...)
 *       │
 *       ▼  mpv buka URL via http/https protocol
 *   libmpv.so — demux MKV container (demuxer=lavf atau native mkv)
 *       ├── Video → hardware decode (MediaCodec) atau software (ffmpeg)
 *       ├── Audio → decode semua codec (AAC/DTS/FLAC/Opus/TrueHD...)
 *       ├── Embedded subtitle (ASS/SRT/PGS) → libass render PENUH
 *       ├── Chapter markers → baca dari container
 *       └── Font attachments → extract dan load ke libass
 */
object MPVLib {

    // ── JNI Native Functions ──────────────────────────────────────────────────

    @JvmStatic external fun create(ctx: Context, logLvl: String): Boolean
    @JvmStatic external fun init()
    @JvmStatic external fun destroy()

    @JvmStatic external fun command(cmd: Array<String>)

    @JvmStatic external fun setOptionString(name: String, value: String)

    @JvmStatic external fun setPropertyString (name: String, value: String)
    @JvmStatic external fun setPropertyInt    (name: String, value: Int)
    @JvmStatic external fun setPropertyDouble (name: String, value: Double)
    @JvmStatic external fun setPropertyBoolean(name: String, value: Boolean)

    @JvmStatic external fun getPropertyString (name: String): String?
    @JvmStatic external fun getPropertyInt    (name: String): Int
    @JvmStatic external fun getPropertyDouble (name: String): Double
    @JvmStatic external fun getPropertyBoolean(name: String): Boolean

    /** Observe property — callback via [EventObserver.eventProperty] */
    @JvmStatic external fun observeProperty(name: String, format: Int)

    @JvmStatic external fun attachSurface(surface: Surface)
    @JvmStatic external fun detachSurface()

    // ── Observer Pattern ──────────────────────────────────────────────────────

    interface EventObserver {
        fun eventProperty(property: String)
        fun eventProperty(property: String, value: Long)
        fun eventProperty(property: String, value: Boolean)
        fun eventProperty(property: String, value: Double)
        fun eventProperty(property: String, value: String)
        fun event(eventId: Int)
    }

    interface LogObserver {
        fun logMessage(prefix: String, level: Int, text: String)
    }

    private val observers    = mutableListOf<EventObserver>()
    private var logObserver: LogObserver? = null

    @JvmStatic
    fun addObserver(o: EventObserver)    { synchronized(observers) { observers.add(o) } }

    @JvmStatic
    fun removeObserver(o: EventObserver) { synchronized(observers) { observers.remove(o) } }

    @JvmStatic
    fun setLogObserver(o: LogObserver?)  { logObserver = o }

    // ── JNI Callbacks (dipanggil dari native thread) ──────────────────────────

    @JvmStatic
    @Suppress("unused") // dipanggil dari JNI
    fun eventProperty(property: String, value: Long) {
        synchronized(observers) { observers.forEach { it.eventProperty(property, value) } }
    }

    @JvmStatic
    @Suppress("unused")
    fun eventProperty(property: String, value: Boolean) {
        synchronized(observers) { observers.forEach { it.eventProperty(property, value) } }
    }

    @JvmStatic
    @Suppress("unused")
    fun eventProperty(property: String, value: Double) {
        synchronized(observers) { observers.forEach { it.eventProperty(property, value) } }
    }

    @JvmStatic
    @Suppress("unused")
    fun eventProperty(property: String, value: String) {
        synchronized(observers) { observers.forEach { it.eventProperty(property, value) } }
    }

    @JvmStatic
    @Suppress("unused")
    fun eventProperty(property: String) {
        synchronized(observers) { observers.forEach { it.eventProperty(property) } }
    }

    @JvmStatic
    @Suppress("unused")
    fun event(eventId: Int) {
        synchronized(observers) { observers.forEach { it.event(eventId) } }
    }

    @JvmStatic
    @Suppress("unused")
    fun logMessage(prefix: String, level: Int, text: String) {
        logObserver?.logMessage(prefix, level, text)
    }

    // ── Event IDs (dari mpv_event_id di client.h) ─────────────────────────────

    const val EVENT_NONE              = 0
    const val EVENT_SHUTDOWN          = 1
    const val EVENT_LOG_MESSAGE       = 2
    const val EVENT_GET_PROPERTY_REPLY= 3
    const val EVENT_SET_PROPERTY_REPLY= 4
    const val EVENT_COMMAND_REPLY     = 5
    const val EVENT_START_FILE        = 6
    const val EVENT_END_FILE          = 7
    const val EVENT_FILE_LOADED       = 8
    const val EVENT_IDLE              = 11
    const val EVENT_TICK              = 14
    const val EVENT_CLIENT_MESSAGE    = 16
    const val EVENT_VIDEO_RECONFIG    = 17
    const val EVENT_AUDIO_RECONFIG    = 18
    const val EVENT_SEEK              = 20
    const val EVENT_PLAYBACK_RESTART  = 21
    const val EVENT_PROPERTY_CHANGE   = 22
    const val EVENT_QUEUE_OVERFLOW    = 24
    const val EVENT_HOOK              = 25

    // ── Format IDs (dari mpv_format di client.h) ──────────────────────────────

    const val FORMAT_NONE    = 0
    const val FORMAT_STRING  = 1
    const val FORMAT_OSD     = 2
    const val FORMAT_FLAG    = 3  // boolean
    const val FORMAT_INT64   = 4
    const val FORMAT_DOUBLE  = 5
    const val FORMAT_NODE    = 6

    // ── Load libmpv.so ────────────────────────────────────────────────────────

    init {
        System.loadLibrary("mpv")
    }
}
