package com.ryou.appryous.data.repository

import com.ryou.appryous.BuildConfig
import com.ryou.appryous.data.model.GistPayload
import com.ryou.appryous.data.remote.NetworkModule
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Resolve URL backend Cloudflare tunnel dari GitHub Gist raw.
 *
 * Flow:
 *  1. Cek [_cachedUrl] — kalau sudah ada, return langsung (in-memory cache)
 *  2. Fetch raw Gist: https://gist.githubusercontent.com/{user}/{id}/raw?t={ts}
 *  3. Parse response: bisa plain URL atau JSON {"url":"...","updated":"..."}
 *  4. Simpan ke [_cachedUrl]
 *  5. Buat BackendApi baru dengan URL tersebut
 *
 * Cache di-invalidate manual via [refresh] (misalnya saat tunnel restart).
 */
class GistRepository {

    private val api     = NetworkModule.gistApi
    private val moshi   = NetworkModule.moshi
    private val mutex   = Mutex()

    @Volatile private var _cachedUrl: String? = null

    // Gist ID & username dari BuildConfig (di-set via build.gradle.kts)
    private val gistUser = BuildConfig.GIST_USERNAME  // "ryoustream"
    private val gistId   = BuildConfig.GIST_ID        // "b324efa90678d7fa4f605c8c425a6596"

    /**
     * Kembalikan URL backend yang sudah di-resolve.
     * Thread-safe via Mutex.
     *
     * @throws GistException jika gagal fetch atau URL tidak valid
     */
    suspend fun resolveUrl(): String = mutex.withLock {
        _cachedUrl?.let { return@withLock it }

        val raw = fetchRawGist()
        val url = parseUrl(raw)
            ?: throw GistException("URL tidak valid di Gist: \"$raw\"")

        _cachedUrl = url
        url
    }

    /**
     * Force re-fetch dari Gist — panggil saat tunnel restart.
     */
    suspend fun refresh(): String = mutex.withLock {
        _cachedUrl = null
        val raw = fetchRawGist()
        val url = parseUrl(raw)
            ?: throw GistException("URL tidak valid setelah refresh: \"$raw\"")
        _cachedUrl = url
        url
    }

    /** URL yang sudah di-cache, null jika belum di-resolve */
    val cachedUrl: String? get() = _cachedUrl

    /** True jika sudah punya URL */
    val isResolved: Boolean get() = _cachedUrl != null

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun fetchRawGist(): String = withContext(Dispatchers.IO) {
        // Cache-bust via timestamp agar tidak dapat cached response
        val cacheBust = System.currentTimeMillis()
        val rawUrl    = "https://gist.githubusercontent.com/$gistUser/$gistId/raw?t=$cacheBust"

        try {
            api.fetchRaw(rawUrl).trim()
        } catch (e: Exception) {
            throw GistException("Gagal fetch Gist: ${e.message}", e)
        }
    }

    /**
     * Parse response Gist yang bisa berupa:
     *  - Plain URL: "https://xxx.trycloudflare.com"
     *  - JSON: {"url":"https://xxx.trycloudflare.com","updated":"2025-01-01"}
     *
     * @return URL bersih tanpa trailing slash, atau null jika tidak valid
     */
    private fun parseUrl(raw: String): String? {
        // Case 1: plain URL
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw.trimEnd('/')
        }

        // Case 2: JSON payload
        if (raw.trimStart().startsWith("{")) {
            return try {
                val adapter = moshi.adapter(GistPayload::class.java)
                val payload = adapter.fromJson(raw)
                val url     = payload?.url?.trim()
                if (!url.isNullOrBlank() && url.startsWith("http")) url.trimEnd('/')
                else null
            } catch (e: JsonDataException) {
                null
            }
        }

        return null
    }
}

class GistException(message: String, cause: Throwable? = null) : Exception(message, cause)
