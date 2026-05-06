package com.ryou.appryous.data.remote

import com.ryou.appryous.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

// ──────────────────────────────────────────────────────────────────────────────
// GIST API
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Fetch backend URL dari GitHub Gist raw URL.
 *
 * URL format: https://gist.githubusercontent.com/{user}/{id}/raw?t={cache_bust}
 * Response: plain text URL atau JSON { "url": "...", "updated": "..." }
 *
 * Tidak butuh auth token — public gist, raw URL bebas diakses.
 * Cache-bust via ?t=timestamp agar selalu dapat URL tunnel terbaru.
 */
interface GistApi {
    /**
     * Fetch raw content dari Gist.
     * @return String plain — bisa berupa URL langsung atau JSON payload
     */
    @GET
    suspend fun fetchRaw(@Url url: String): String
}

// ──────────────────────────────────────────────────────────────────────────────
// BACKEND API
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Semua endpoint backend RyouStream.
 * Base URL di-set secara dinamis setelah GistRepository resolve tunnel URL.
 *
 * Backend berjalan di port 8080 via Cloudflare tunnel.
 * Semua endpoint GET kecuali /api/scan yang juga terima POST.
 */
interface BackendApi {

    /**
     * GET /api/library
     *
     * Kembalikan seluruh library anime/drama.
     * Status 202 + status:"scanning" jika library kosong dan sedang scan.
     *
     * @return LibraryResponse { status, data: List<AnimeEntry>, total }
     */
    @GET("api/library")
    suspend fun getLibrary(): LibraryResponse

    /**
     * GET /api/episodes/{id}
     *
     * Kembalikan semua episode dari satu judul.
     * Setiap episode sudah include:
     *   - src: absolute URL ke file video (MKV/MP4/dll)
     *   - subtitles: external tracks (sudah di-inject base_url oleh server)
     *   - chapter_file: path ke XML chapter di SD Card (null jika tidak ada)
     *
     * @param id ID judul (4-digit zero-padded, contoh "0001")
     */
    @GET("api/episodes/{id}")
    suspend fun getEpisodes(@Path("id") id: String): EpisodesResponse

    /**
     * GET /api/scan?force=0|1
     *
     * Trigger scan library.
     * force=1 → hapus cache episode titles, fetch ulang dari MAL/TMDB/MDL.
     * force=0 → scan inkremental, skip judul yang sudah di-cache.
     */
    @GET("api/scan")
    suspend fun triggerScan(@Query("force") force: Int = 0): ScanResponse

    /**
     * GET /api/scan/status
     *
     * Cek status scan yang sedang berjalan.
     * @return ScanStatus { running, progress, last_scan }
     */
    @GET("api/scan/status")
    suspend fun getScanStatus(): ScanStatus

    /**
     * GET /api/settings
     *
     * Info server: versi, path SD Card, ekstensi video yang didukung, dll.
     */
    @GET("api/settings")
    suspend fun getServerSettings(): ServerSettings

    /**
     * GET /api/clear_cache?type=all|library|meta
     *
     * Hapus cache backend.
     * type="library" → hapus cache list anime saja
     * type="meta"    → hapus cache metadata MAL/TMDB/MDL
     * type="all"     → hapus semua
     */
    @GET("api/clear_cache")
    suspend fun clearCache(@Query("type") type: String = "all"): ClearCacheResponse

    /**
     * GET /api/chapters?path={mediaPath}
     *
     * Parse Matroska XML chapter file di SD Card.
     * [mediaPath] adalah nilai [Episode.chapterFile] — path absolut ke .xml
     * di samping file video.
     *
     * Contoh format XML yang di-parse backend:
     *   <ChapterAtom>
     *     <ChapterTimeStart>00:01:23.456</ChapterTimeStart>
     *     <ChapterDisplay><ChapterString>Opening</ChapterString></ChapterDisplay>
     *   </ChapterAtom>
     *
     * Note: ExoPlayer juga membaca embedded chapter dari MKV container secara
     * otomatis via MatroskaExtractor. External XML chapters ini sebagai fallback
     * atau untuk video yang chapter-nya disimpan terpisah.
     *
     * @return ChaptersResponse { chapters: List<Chapter>, count }
     */
    @GET("api/chapters")
    suspend fun getChapters(@Query("path") mediaPath: String): ChaptersResponse

    /**
     * GET /api/fonts
     *
     * List font files dari SD Card/Fonts folder.
     * Untuk ASS subtitle yang butuh custom font.
     * Backend: server sudah convert ASS → VTT (strip styling + font ref),
     * tapi font list tetap tersedia untuk implementasi ASS renderer di masa depan.
     */
    @GET("api/fonts")
    suspend fun getFonts(): FontsResponse

    /**
     * GET /api/dirlist?path={dirPath}
     *
     * List isi direktori di SD Card.
     * Berguna untuk debug atau file browser.
     */
    @GET("api/dirlist")
    suspend fun getDirList(@Query("path") dirPath: String): Map<String, Any>
}
