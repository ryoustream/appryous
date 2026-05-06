package com.ryou.appryous.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ──────────────────────────────────────────────────────────────────────────────
// GIST
// ──────────────────────────────────────────────────────────────────────────────

/** Payload mentah dari GitHub Gist raw URL (ryou-backend.json) */
@JsonClass(generateAdapter = true)
data class GistPayload(
    @Json(name = "url")     val url:     String = "",
    @Json(name = "updated") val updated: String = ""
)

// ──────────────────────────────────────────────────────────────────────────────
// LIBRARY
// ──────────────────────────────────────────────────────────────────────────────

/** Response dari /api/library */
@JsonClass(generateAdapter = true)
data class LibraryResponse(
    @Json(name = "status")  val status:  String          = "ok",
    @Json(name = "data")    val data:    List<AnimeEntry> = emptyList(),
    @Json(name = "total")   val total:   Int             = 0,
    @Json(name = "message") val message: String          = ""
)

/**
 * Satu entri anime/drama dari /api/library.
 * Semua field opsional menggunakan default karena backend bisa mengirim null.
 *
 * Field MKV internal tag yang dibaca oleh scanner via mutagen:
 *   TITLE, SHOW, DATE → title / title_local / year
 */
@JsonClass(generateAdapter = true)
data class AnimeEntry(
    @Json(name = "id")              val id:             String  = "",
    @Json(name = "title")           val title:          String  = "",
    @Json(name = "title_en")        val titleEn:        String  = "",
    @Json(name = "title_ja")        val titleJa:        String  = "",
    @Json(name = "title_romaji")    val titleRomaji:    String  = "",
    /** Judul bersih dari nama file/folder setelah noise-strip */
    @Json(name = "title_local")     val titleLocal:     String  = "",
    /** Sinonim dari MAL/TMDB */
    @Json(name = "title_synonyms")  val titleSynonyms:  List<String> = emptyList(),
    @Json(name = "type")            val type:           String  = "TV",
    @Json(name = "year")            val year:           String  = "",
    @Json(name = "episodes")        val episodes:       Int     = 0,
    @Json(name = "rating")          val rating:         Double  = 0.0,
    @Json(name = "poster")          val poster:         String  = "",
    @Json(name = "banner")          val banner:         String  = "",
    @Json(name = "genres")          val genres:         List<String> = emptyList(),
    /** Nama genre untuk tampilan (hasil mapping dari MAL) */
    @Json(name = "genres_disp")     val genresDisp:     List<String> = emptyList(),
    /** Deskripsi bahasa default (EN dari MAL/TMDB) */
    @Json(name = "description")     val description:    String  = "",
    /** Deskripsi terjemahan Indonesia */
    @Json(name = "description_id")  val descriptionId:  String  = "",
    /** Deskripsi bahasa Jepang */
    @Json(name = "description_ja")  val descriptionJa:  String  = "",
    @Json(name = "status")          val status:         String  = "",
    @Json(name = "studio")          val studio:         String  = "",
    @Json(name = "aired")           val aired:          String  = "",
    @Json(name = "aired_end")       val airedEnd:       String  = "",
    @Json(name = "source")          val source:         String  = "",
    @Json(name = "mal_id")          val malId:          Int?    = null,
    @Json(name = "tmdb_id")         val tmdbId:         Int?    = null,
    @Json(name = "mdl_slug")        val mdlSlug:        String  = "",
    @Json(name = "trailer")         val trailer:        String  = "",
    /** Tema lagu OP/ED dari MAL */
    @Json(name = "themes")          val themes:         List<String> = emptyList(),
    /** Demografi dari MAL (Shounen, Seinen, dll) */
    @Json(name = "demographics")    val demographics:   List<String> = emptyList()
) {
    /** Deskripsi yang dipilih sesuai lang setting */
    fun descFor(lang: String): String = when (lang) {
        "id" -> descriptionId.ifBlank { description }
        "ja" -> descriptionJa.ifBlank { description }
        else -> description.ifBlank { descriptionId }
    }

    /** Genre gabungan (genresDisp lebih prioritas karena sudah di-display-mapped) */
    val displayGenres: List<String>
        get() = genresDisp.ifEmpty { genres }

    /** Apakah rating valid */
    val hasRating: Boolean get() = rating > 0.0

    /** Formatted rating */
    val ratingFormatted: String get() = if (hasRating) "%.1f".format(rating) else "—"
}

// ──────────────────────────────────────────────────────────────────────────────
// EPISODES
// ──────────────────────────────────────────────────────────────────────────────

/** Response dari /api/episodes/:id */
@JsonClass(generateAdapter = true)
data class EpisodesResponse(
    @Json(name = "id")       val id:       String         = "",
    @Json(name = "title")    val title:    String         = "",
    @Json(name = "episodes") val episodes: List<Episode>  = emptyList()
)

/**
 * Satu episode.
 *
 * MKV-specific:
 *  - [src]         → URL ke file .mkv — di-serve backend dengan Range request support
 *  - [subtitles]   → External subtitle files (.vtt/.srt/.ass) — server convert ASS→VTT on-the-fly
 *  - [chapterFile] → Path ke file XML Matroska chapter di SD Card
 *                    Ambil via /api/chapters?path=<chapterFile>
 *
 * ExoPlayer secara otomatis membaca:
 *  - Embedded subtitle tracks di dalam MKV (SubRip, ASS, PGS dll via MatroskaExtractor)
 *  - Embedded chapter markers di dalam MKV container
 * External tracks dari [subtitles] ditambahkan via MediaItem.SubtitleConfiguration
 */
@JsonClass(generateAdapter = true)
data class Episode(
    @Json(name = "ep")           val ep:          Int              = 0,
    @Json(name = "title")        val title:        String           = "",
    @Json(name = "duration")     val duration:     String           = "",
    @Json(name = "thumbnail")    val thumbnail:    String           = "",
    /** URL streaming — mendukung .mkv, .mp4, .webm, .m3u8, .mpd */
    @Json(name = "src")          val src:          String           = "",
    /** External subtitle tracks (server sudah convert ke VTT) */
    @Json(name = "subtitles")    val subtitles:    List<SubtitleTrack> = emptyList(),
    /**
     * Path absolut ke file .xml Matroska chapters di SD Card.
     * Null jika tidak ada file .xml di samping file video.
     * Gunakan endpoint /api/chapters?path=<chapterFile> untuk mendapatkan daftar chapter.
     */
    @Json(name = "chapter_file") val chapterFile:  String?          = null
) {
    val hasExternalSubs: Boolean get() = subtitles.isNotEmpty()
    val hasChapters:     Boolean get() = !chapterFile.isNullOrBlank()

    /** Mime type berdasarkan ekstensi src */
    val mimeType: String get() = when {
        src.contains(".m3u8", ignoreCase = true)  -> "application/x-mpegurl"
        src.contains(".mpd",  ignoreCase = true)  -> "application/dash+xml"
        src.contains(".mkv",  ignoreCase = true)  -> "video/x-matroska"
        src.contains(".webm", ignoreCase = true)  -> "video/webm"
        src.contains(".ts",   ignoreCase = true)  -> "video/mp2t"
        else                                       -> "video/mp4"
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// SUBTITLES
// ──────────────────────────────────────────────────────────────────────────────

/**
 * External subtitle track.
 *
 * Backend scanner menemukan file .vtt/.srt/.ass/.ssa di samping video.
 * File ASS/SSA dan SRT di-convert ke VTT on-the-fly oleh server
 * saat request /media/... dengan ekstensi tersebut.
 *
 * Cara ExoPlayer load external subtitle:
 *   MediaItem.SubtitleConfiguration.Builder(Uri.parse(src))
 *     .setMimeType(MimeTypes.TEXT_VTT)
 *     .setLanguage(lang)
 *     .setLabel(label)
 *     .setSelectionFlags(C.SELECTION_FLAG_DEFAULT bila default = true)
 *     .build()
 */
@JsonClass(generateAdapter = true)
data class SubtitleTrack(
    @Json(name = "label")   val label:   String  = "Indonesia",
    @Json(name = "lang")    val lang:    String  = "id",
    /** URL ke subtitle file — server serve sebagai VTT */
    @Json(name = "src")     val src:     String  = "",
    @Json(name = "default") val default: Boolean = false
) {
    /**
     * Mime type untuk MediaItem.SubtitleConfiguration.
     * Server selalu serve sebagai VTT (convert on-the-fly), tapi kita
     * bisa detect dari extension URL untuk fallback.
     */
    val mimeType: String get() = when {
        src.contains(".vtt",  ignoreCase = true) -> "text/vtt"
        src.contains(".srt",  ignoreCase = true) -> "application/x-subrip"
        src.contains(".ass",  ignoreCase = true) -> "text/x-ssa"
        src.contains(".ssa",  ignoreCase = true) -> "text/x-ssa"
        else                                      -> "text/vtt"  // server convert semua ke VTT
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// CHAPTERS
// ──────────────────────────────────────────────────────────────────────────────

/** Response dari /api/chapters?path=... */
@JsonClass(generateAdapter = true)
data class ChaptersResponse(
    @Json(name = "chapters") val chapters: List<Chapter> = emptyList(),
    @Json(name = "count")    val count:    Int           = 0
)

/**
 * Satu chapter dari file XML Matroska.
 *
 * Format XML aslinya (Matroska chapter XML):
 *   <ChapterAtom>
 *     <ChapterTimeStart>00:01:23.456</ChapterTimeStart>
 *     <ChapterDisplay>
 *       <ChapterString>Opening</ChapterString>
 *     </ChapterDisplay>
 *   </ChapterAtom>
 *
 * Backend parse → { time: 83.456, title: "Opening" }
 *
 * Note: ExoPlayer juga bisa baca embedded chapter dari MKV container langsung
 * via player.currentTimeline.getWindow(...).mediaItem.requestMetadata
 * tapi external XML chapters dari backend lebih reliable.
 */
@JsonClass(generateAdapter = true)
data class Chapter(
    /** Waktu dalam detik (dengan desimal) */
    @Json(name = "time")  val time:  Double = 0.0,
    @Json(name = "title") val title: String = ""
) {
    /** Waktu dalam milliseconds untuk ExoPlayer seekTo() */
    val timeMs: Long get() = (time * 1000).toLong()

    /** Format HH:MM:SS atau MM:SS */
    val timeFormatted: String get() {
        val totalSec = time.toLong()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// SCAN
// ──────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ScanResponse(
    @Json(name = "status")   val status:   String = "",
    @Json(name = "force")    val force:    Boolean = false,
    @Json(name = "progress") val progress: String  = ""
)

@JsonClass(generateAdapter = true)
data class ScanStatus(
    @Json(name = "running")   val running:  Boolean = false,
    @Json(name = "progress")  val progress: String  = "",
    @Json(name = "last_scan") val lastScan: Long    = 0L
)

// ──────────────────────────────────────────────────────────────────────────────
// SERVER SETTINGS
// ──────────────────────────────────────────────────────────────────────────────

/** Response dari /api/settings */
@JsonClass(generateAdapter = true)
data class ServerSettings(
    @Json(name = "version")         val version:        String       = "",
    @Json(name = "sdcard_root")     val sdcardRoot:     String       = "",
    @Json(name = "movies_path")     val moviesPath:     String       = "",
    @Json(name = "videos_path")     val videosPath:     String       = "",
    @Json(name = "port")            val port:           Int          = 8080,
    @Json(name = "cache_ttl_hours") val cacheTtlHours:  Int          = 168,
    @Json(name = "video_exts")      val videoExts:      List<String> = emptyList(),
    @Json(name = "sub_exts")        val subExts:        List<String> = emptyList(),
    @Json(name = "has_tmdb_key")    val hasTmdbKey:     Boolean      = false,
    @Json(name = "mdl_base")        val mdlBase:        String       = "",
    @Json(name = "cache_dir")       val cacheDir:       String       = ""
)

// ──────────────────────────────────────────────────────────────────────────────
// FONTS
// ──────────────────────────────────────────────────────────────────────────────

/** Response dari /api/fonts — untuk ASS subtitle custom font support */
@JsonClass(generateAdapter = true)
data class FontsResponse(
    @Json(name = "fonts") val fonts: List<FontEntry> = emptyList(),
    @Json(name = "count") val count: Int             = 0
)

/**
 * Satu font file dari SD Card/Fonts folder.
 * Digunakan oleh subtitle renderer untuk ASS dengan custom font.
 * ExoPlayer tidak support ASS font embedding secara native;
 * server sudah convert ASS → VTT (strip styling) sehingga font tidak diperlukan
 * untuk rendering subtitle, namun disediakan untuk future ASS renderer.
 */
@JsonClass(generateAdapter = true)
data class FontEntry(
    @Json(name = "name") val name: String = "",
    @Json(name = "url")  val url:  String = ""
)

// ──────────────────────────────────────────────────────────────────────────────
// CLEAR CACHE
// ──────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ClearCacheResponse(
    @Json(name = "status")  val status:  String = "",
    @Json(name = "message") val message: String = "",
    @Json(name = "type")    val type:    String = ""
)

// ──────────────────────────────────────────────────────────────────────────────
// APP SETTINGS (local, disimpan di SharedPreferences)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Setting aplikasi yang disimpan lokal.
 * Mirror dari DEFAULT_SETTINGS di config.js epsilon.
 */
data class AppSettings(
    /** Autoplay episode saat dibuka */
    val autoplay:        Boolean = false,
    /** Auto lanjut ke episode berikutnya */
    val autoNext:        Boolean = true,
    /** Ingat posisi terakhir tiap episode */
    val rememberPos:     Boolean = true,
    /** Aktifkan subtitle otomatis */
    val subtitleEnabled: Boolean = true,
    /** Volume default 0.0–1.0 */
    val volume:          Float   = 1.0f,
    /** Warna aksen hex, contoh "#7c3aed" */
    val accentColor:     String  = "#7c3aed",
    /** Bahasa deskripsi: "id", "en", "ja" */
    val descLang:        String  = "id",
    /** Aktifkan animasi transisi */
    val animations:      Boolean = true,
    /** Layout card: "poster" atau "wide" */
    val cardLayout:      String  = "poster"
)

// ──────────────────────────────────────────────────────────────────────────────
// HISTORY & POSITION (local store)
// ──────────────────────────────────────────────────────────────────────────────

/** Satu item riwayat tontonan */
data class HistoryItem(
    val animeId:    String,
    val animeTitle: String,
    val poster:     String,
    val ep:         Int,
    val epTitle:    String,
    /** Unix timestamp milliseconds */
    val ts:         Long = System.currentTimeMillis()
)

/** Posisi terakhir menonton satu episode */
data class WatchPosition(
    val animeId:  String,
    val ep:       Int,
    /** Posisi dalam detik */
    val time:     Double,
    val duration: Double,
    val ts:       Long = System.currentTimeMillis()
) {
    /** Persentase progress 0–100 */
    val pct: Int get() = if (duration > 0) ((time / duration) * 100).toInt().coerceIn(0, 100) else 0
    /** Apakah sudah selesai (>= 90%) */
    val isFinished: Boolean get() = pct >= 90
}
