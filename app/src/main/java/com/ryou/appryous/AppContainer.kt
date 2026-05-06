package com.ryou.appryous

import android.content.Context
import com.ryou.appryous.data.local.HistoryStore
import com.ryou.appryous.data.local.LibCache
import com.ryou.appryous.data.local.PositionStore
import com.ryou.appryous.data.local.SettingsStore
import com.ryou.appryous.data.repository.BackendRepository
import com.ryou.appryous.data.repository.GistRepository

/**
 * Manual DI container — singleton yang hidup selama proses app.
 * Tidak pakai Hilt/Dagger untuk menjaga proyek tetap ringan.
 *
 * Semua dependency dibuat lazy agar tidak berat saat startup.
 * Init dipanggil dari [AppRyousApp.onCreate].
 *
 * Alur dependency:
 *   [gistRepo]      → resolve URL dari Gist
 *   [backendRepo]   → pakai URL dari gistRepo, buat BackendApi
 *   [libCache]      → cache library hasil /api/library
 *   [settingsStore] → AppSettings lokal
 *   [historyStore]  → riwayat tontonan
 *   [positionStore] → posisi menonton per episode
 */
class AppContainer(private val context: Context) {

    /** Resolve URL backend dari GitHub Gist raw URL */
    val gistRepo: GistRepository by lazy { GistRepository() }

    /**
     * Semua API call ke backend.
     * Base URL dinamis — di-update setelah Gist resolve.
     */
    val backendRepo: BackendRepository by lazy { BackendRepository(gistRepo) }

    /** Cache library 5 menit (in-memory + SharedPreferences) */
    val libCache: LibCache by lazy { LibCache(context) }

    /** AppSettings lokal (autoplay, subtitle, accent, dll) */
    val settingsStore: SettingsStore by lazy { SettingsStore(context) }

    /** Riwayat tontonan — max 100 item */
    val historyStore: HistoryStore by lazy { HistoryStore(context) }

    /** Posisi menonton per episode (continue watching) */
    val positionStore: PositionStore by lazy { PositionStore(context) }
}

/**
 * Extension untuk akses AppContainer dari Activity/Fragment.
 * Contoh: val repo = requireActivity().container.backendRepo
 */
val android.app.Activity.container: AppContainer
    get() = (application as AppRyousApp).container

val android.content.Context.container: AppContainer
    get() = (applicationContext as AppRyousApp).container
