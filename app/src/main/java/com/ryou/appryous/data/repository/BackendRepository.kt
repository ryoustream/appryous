package com.ryou.appryous.data.repository

import com.ryou.appryous.data.model.*
import com.ryou.appryous.data.remote.BackendApi
import com.ryou.appryous.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository untuk semua komunikasi ke backend RyouStream.
 *
 * BackendApi di-create ulang setiap kali [updateBaseUrl] dipanggil
 * (saat Gist resolve atau tunnel refresh). Singleton-safe via @Volatile.
 */
class BackendRepository(private val gistRepo: GistRepository) {

    @Volatile private var _api: BackendApi? = null

    /**
     * Pastikan _api sudah siap — resolve URL dari Gist jika belum.
     */
    private suspend fun api(): BackendApi {
        _api?.let { return it }
        val url = gistRepo.resolveUrl()
        return NetworkModule.createBackendApi(url).also { _api = it }
    }

    /**
     * Paksa update base URL — panggil setelah Gist refresh.
     */
    suspend fun updateBaseUrl() {
        val url = gistRepo.refresh()
        _api = NetworkModule.createBackendApi(url)
    }

    // ── Library ───────────────────────────────────────────────────────────────

    /**
     * Fetch seluruh library.
     * Saat status == "scanning" artinya backend sedang scan SD Card.
     */
    suspend fun getLibrary(): Result<LibraryResponse> = safeCall { api().getLibrary() }

    // ── Episodes ──────────────────────────────────────────────────────────────

    /**
     * Fetch semua episode beserta subtitle, chapter file, dan src URL-nya.
     * Backend sudah inject base_url ke [Episode.src] dan [SubtitleTrack.src].
     *
     * MKV handling:
     *  - [Episode.src] → URL ke file .mkv, di-load ExoPlayer via MatroskaExtractor
     *  - [Episode.subtitles] → external .vtt tracks, server convert ASS/SRT → VTT
     *  - [Episode.chapterFile] → path XML di SD Card, fetch via [getChapters]
     */
    suspend fun getEpisodes(animeId: String): Result<EpisodesResponse> =
        safeCall { api().getEpisodes(animeId) }

    // ── Scan ──────────────────────────────────────────────────────────────────

    /**
     * Trigger scan library.
     * @param force true = hapus cache episode titles & fetch ulang dari MAL/TMDB
     */
    suspend fun triggerScan(force: Boolean = false): Result<ScanResponse> =
        safeCall { api().triggerScan(if (force) 1 else 0) }

    /** Poll status scan yang sedang berjalan */
    suspend fun getScanStatus(): Result<ScanStatus> =
        safeCall { api().getScanStatus() }

    // ── Settings ──────────────────────────────────────────────────────────────

    suspend fun getServerSettings(): Result<ServerSettings> =
        safeCall { api().getServerSettings() }

    // ── Cache ─────────────────────────────────────────────────────────────────

    /**
     * Hapus cache backend.
     * @param type "all" | "library" | "meta"
     */
    suspend fun clearCache(type: String = "all"): Result<ClearCacheResponse> =
        safeCall { api().clearCache(type) }

    // ── Chapters ──────────────────────────────────────────────────────────────

    /**
     * Fetch chapters dari XML Matroska di SD Card.
     *
     * [mediaPath] = nilai [Episode.chapterFile] yang dikembalikan oleh /api/episodes.
     * Server parse format:
     *   <ChapterAtom>
     *     <ChapterTimeStart>HH:MM:SS.mmm</ChapterTimeStart>
     *     <ChapterDisplay><ChapterString>Title</ChapterString></ChapterDisplay>
     *   </ChapterAtom>
     *
     * Hasilnya dipakai untuk:
     *  1. Menampilkan chapter list di PlayerActivity
     *  2. Seek ke chapter tertentu via ExoPlayer.seekTo(chapter.timeMs)
     *  3. Chapter marker di progress bar
     *
     * Note: ExoPlayer juga bisa membaca embedded chapter dari MKV container
     * secara otomatis via MatroskaExtractor — lihat PlayerHelper.kt.
     */
    suspend fun getChapters(mediaPath: String): Result<ChaptersResponse> =
        safeCall { api().getChapters(mediaPath) }

    // ── Fonts ─────────────────────────────────────────────────────────────────

    /**
     * List font files dari SD Card/Fonts folder.
     * Backend: server sudah convert ASS → VTT sehingga font tidak diperlukan
     * untuk rendering subtitle default. Tersedia untuk future ASS renderer.
     */
    suspend fun getFonts(): Result<FontsResponse> =
        safeCall { api().getFonts() }

    // ── Ping ──────────────────────────────────────────────────────────────────

    /**
     * Cek apakah backend online.
     * @return "online" | "scanning" | "offline"
     */
    suspend fun ping(): String = withContext(Dispatchers.IO) {
        try {
            val result = getLibrary()
            when {
                result.isFailure                          -> "offline"
                result.getOrNull()?.status == "scanning" -> "scanning"
                else                                      -> "online"
            }
        } catch (e: Exception) {
            "offline"
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Build URL media lengkap dari relative path */
    fun mediaUrl(relativePath: String): String {
        val base = gistRepo.cachedUrl ?: return relativePath
        return if (relativePath.startsWith("/")) "$base$relativePath"
        else "$base/$relativePath"
    }

    // ── Safe call wrapper ─────────────────────────────────────────────────────

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: retrofit2.HttpException) {
                Result.failure(BackendException("HTTP ${e.code()}: ${e.message()}", e.code()))
            } catch (e: java.net.UnknownHostException) {
                Result.failure(BackendException("Tidak bisa terhubung ke server (host tidak dikenal)", 0))
            } catch (e: java.net.SocketTimeoutException) {
                Result.failure(BackendException("Request timeout — cek koneksi tunnel", 408))
            } catch (e: java.io.IOException) {
                Result.failure(BackendException("Network error: ${e.message}", 0))
            } catch (e: Exception) {
                Result.failure(BackendException("Error: ${e.message}", -1))
            }
        }
}

class BackendException(message: String, val statusCode: Int) : Exception(message)
