package com.ryou.appryous.data.local

import android.content.Context
import android.content.SharedPreferences
import com.ryou.appryous.data.model.AppSettings
import com.ryou.appryous.data.model.HistoryItem
import com.ryou.appryous.data.model.WatchPosition
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// ──────────────────────────────────────────────────────────────────────────────
// BASE PREFS HELPER
// ──────────────────────────────────────────────────────────────────────────────

internal abstract class BaseStore(context: Context, name: String) {
    protected val prefs: SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    protected fun edit(block: SharedPreferences.Editor.() -> Unit) =
        prefs.edit().apply(block).apply()
}

// ──────────────────────────────────────────────────────────────────────────────
// SETTINGS STORE
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Penyimpanan AppSettings lokal.
 * Mirror dari DEFAULT_SETTINGS + Settings.set() di config.js epsilon.
 */
internal class SettingsStore(context: Context) : BaseStore(context, "rs_settings") {

    fun get(): AppSettings = AppSettings(
        autoplay        = prefs.getBoolean(KEY_AUTOPLAY,         false),
        autoNext        = prefs.getBoolean(KEY_AUTO_NEXT,        true),
        rememberPos     = prefs.getBoolean(KEY_REMEMBER_POS,     true),
        subtitleEnabled = prefs.getBoolean(KEY_SUBTITLE_ENABLED, true),
        volume          = prefs.getFloat  (KEY_VOLUME,           1.0f),
        accentColor     = prefs.getString (KEY_ACCENT_COLOR,     "#7c3aed") ?: "#7c3aed",
        descLang        = prefs.getString (KEY_DESC_LANG,        "id")      ?: "id",
        animations      = prefs.getBoolean(KEY_ANIMATIONS,       true),
        cardLayout      = prefs.getString (KEY_CARD_LAYOUT,      "poster")  ?: "poster"
    )

    fun setAutoplay       (v: Boolean) = edit { putBoolean(KEY_AUTOPLAY,         v) }
    fun setAutoNext       (v: Boolean) = edit { putBoolean(KEY_AUTO_NEXT,        v) }
    fun setRememberPos    (v: Boolean) = edit { putBoolean(KEY_REMEMBER_POS,     v) }
    fun setSubtitleEnabled(v: Boolean) = edit { putBoolean(KEY_SUBTITLE_ENABLED, v) }
    fun setVolume         (v: Float)   = edit { putFloat  (KEY_VOLUME,           v) }
    fun setAccentColor    (v: String)  = edit { putString (KEY_ACCENT_COLOR,     v) }
    fun setDescLang       (v: String)  = edit { putString (KEY_DESC_LANG,        v) }
    fun setAnimations     (v: Boolean) = edit { putBoolean(KEY_ANIMATIONS,       v) }
    fun setCardLayout     (v: String)  = edit { putString (KEY_CARD_LAYOUT,      v) }

    fun reset() = edit {
        putBoolean(KEY_AUTOPLAY,         false)
        putBoolean(KEY_AUTO_NEXT,        true)
        putBoolean(KEY_REMEMBER_POS,     true)
        putBoolean(KEY_SUBTITLE_ENABLED, true)
        putFloat  (KEY_VOLUME,           1.0f)
        putString (KEY_ACCENT_COLOR,     "#7c3aed")
        putString (KEY_DESC_LANG,        "id")
        putBoolean(KEY_ANIMATIONS,       true)
        putString (KEY_CARD_LAYOUT,      "poster")
    }

    companion object {
        private const val KEY_AUTOPLAY         = "autoplay"
        private const val KEY_AUTO_NEXT        = "autoNext"
        private const val KEY_REMEMBER_POS     = "rememberPos"
        private const val KEY_SUBTITLE_ENABLED = "subtitleEnabled"
        private const val KEY_VOLUME           = "volume"
        private const val KEY_ACCENT_COLOR     = "accentColor"
        private const val KEY_DESC_LANG        = "descLang"
        private const val KEY_ANIMATIONS       = "animations"
        private const val KEY_CARD_LAYOUT      = "cardLayout"
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// HISTORY STORE
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Riwayat tontonan — max 100 item, LIFO order.
 * Mirror dari History di config.js epsilon.
 */
internal class HistoryStore(context: Context) : BaseStore(context, "rs_watch_history") {

    private val moshi   = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, HistoryItem::class.java)
    private val adapter = moshi.adapter<List<HistoryItem>>(listType)

    fun getAll(): List<HistoryItem> =
        try { adapter.fromJson(prefs.getString(KEY_LIST, "[]") ?: "[]") ?: emptyList() }
        catch (e: Exception) { emptyList() }

    fun add(item: HistoryItem) {
        val list = getAll()
            .filter { !(it.animeId == item.animeId && it.ep == item.ep) }
            .toMutableList()
        list.add(0, item)
        save(list.take(100))
    }

    fun clear() = edit { putString(KEY_LIST, "[]") }

    fun has(animeId: String, ep: Int): Boolean =
        getAll().any { it.animeId == animeId && it.ep == ep }

    /** Semua item unik per anime (untuk continue watching — ambil episode terbaru per anime) */
    fun continueWatching(): List<HistoryItem> {
        val seen = mutableSetOf<String>()
        return getAll().filter { seen.add(it.animeId) }.take(24)
    }

    private fun save(list: List<HistoryItem>) =
        edit { putString(KEY_LIST, adapter.toJson(list)) }

    companion object { private const val KEY_LIST = "list" }
}

// ──────────────────────────────────────────────────────────────────────────────
// POSITION STORE (Continue Watching)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Simpan posisi menonton tiap episode.
 * Mirror dari Positions di config.js epsilon.
 *
 * Key format: "{animeId}_{ep}" — sama persis dengan JS: `${animeId}_${ep}`
 * Value: { time, duration, ts }
 */
internal class PositionStore(context: Context) : BaseStore(context, "rs_positions") {

    private val moshi   = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val mapType = Types.newParameterizedType(
        Map::class.java, String::class.java, WatchPosition::class.java
    )
    private val adapter = moshi.adapter<Map<String, WatchPosition>>(mapType)

    private fun loadAll(): MutableMap<String, WatchPosition> =
        try { adapter.fromJson(prefs.getString(KEY_MAP, "{}") ?: "{}")?.toMutableMap() ?: mutableMapOf() }
        catch (e: Exception) { mutableMapOf() }

    private fun saveAll(map: Map<String, WatchPosition>) =
        edit { putString(KEY_MAP, adapter.toJson(map)) }

    private fun key(animeId: String, ep: Int) = "${animeId}_${ep}"

    fun save(animeId: String, ep: Int, time: Double, duration: Double) {
        val map = loadAll()
        map[key(animeId, ep)] = WatchPosition(animeId, ep, time, duration)
        saveAll(map)
    }

    fun get(animeId: String, ep: Int): WatchPosition? =
        loadAll()[key(animeId, ep)]

    /** Posisi terakhir dalam detik (0 jika tidak ada) */
    fun getTime(animeId: String, ep: Int): Double =
        get(animeId, ep)?.time ?: 0.0

    /** Persentase progress 0–100 */
    fun getPct(animeId: String, ep: Int): Int =
        get(animeId, ep)?.pct ?: 0

    fun remove(animeId: String, ep: Int) {
        val map = loadAll()
        map.remove(key(animeId, ep))
        saveAll(map)
    }

    fun clear() = edit { putString(KEY_MAP, "{}") }

    companion object { private const val KEY_MAP = "map" }
}

// ──────────────────────────────────────────────────────────────────────────────
// LIB CACHE
// ──────────────────────────────────────────────────────────────────────────────

/**
 * In-memory + SharedPreferences cache untuk library hasil /api/library.
 * TTL: 5 menit (sama dengan web epsilon).
 * In-memory cache hilang saat app di-kill; SharedPreferences persist.
 *
 * Mirror dari LibCache di config.js epsilon.
 */
internal class LibCache(context: Context) : BaseStore(context, "rs_lib_cache") {

    private val moshi    = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(
        List::class.java, com.ryou.appryous.data.model.AnimeEntry::class.java
    )
    private val adapter = moshi.adapter<List<com.ryou.appryous.data.model.AnimeEntry>>(listType)

    // In-memory cache
    @Volatile private var _data: List<com.ryou.appryous.data.model.AnimeEntry>? = null
    @Volatile private var _ts:   Long = 0L

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L  // 5 menit
        private const val KEY_DATA     = "data"
        private const val KEY_TS       = "ts"
    }

    fun load(): List<com.ryou.appryous.data.model.AnimeEntry>? {
        // In-memory hit
        _data?.let { if (!isStale()) return it }

        // SharedPreferences hit
        return try {
            val ts  = prefs.getLong(KEY_TS, 0L)
            val raw = prefs.getString(KEY_DATA, null) ?: return null
            if (System.currentTimeMillis() - ts > CACHE_TTL_MS) return null
            val list = adapter.fromJson(raw) ?: return null
            _data = list
            _ts   = ts
            list
        } catch (e: Exception) { null }
    }

    fun save(data: List<com.ryou.appryous.data.model.AnimeEntry>) {
        _data = data
        _ts   = System.currentTimeMillis()
        edit {
            putString(KEY_DATA, adapter.toJson(data))
            putLong  (KEY_TS,   _ts)
        }
    }

    fun clear() {
        _data = null
        _ts   = 0L
        edit {
            remove(KEY_DATA)
            remove(KEY_TS)
        }
    }

    fun isStale(): Boolean =
        _data == null || (System.currentTimeMillis() - _ts > CACHE_TTL_MS)
}
