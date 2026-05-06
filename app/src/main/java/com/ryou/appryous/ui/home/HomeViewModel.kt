package com.ryou.appryous.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ryou.appryous.data.local.HistoryStore
import com.ryou.appryous.data.local.LibCache
import com.ryou.appryous.data.local.PositionStore
import com.ryou.appryous.data.model.AnimeEntry
import com.ryou.appryous.data.model.HistoryItem
import com.ryou.appryous.data.repository.BackendRepository
import com.ryou.appryous.util.BaseViewModel

class HomeViewModel(
    private val repo:     BackendRepository,
    private val cache:    LibCache,
    private val history:  HistoryStore,
    private val position: PositionStore
) : BaseViewModel() {

    private val _library = MutableLiveData<List<AnimeEntry>>(emptyList())
    val library: LiveData<List<AnimeEntry>> = _library

    private val _continueWatching = MutableLiveData<List<HistoryItem>>(emptyList())
    val continueWatching: LiveData<List<HistoryItem>> = _continueWatching

    private val _heroItems = MutableLiveData<List<AnimeEntry>>(emptyList())
    val heroItems: LiveData<List<AnimeEntry>> = _heroItems

    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning

    init { loadLibrary() }

    fun loadLibrary(forceRefresh: Boolean = false) = launchSafe {
        // 1. Coba dari cache dulu (< 5 menit)
        if (!forceRefresh) {
            cache.load()?.let { cached ->
                applyLibrary(cached)
                return@launchSafe
            }
        }

        // 2. Fetch dari backend
        val result = repo.getLibrary().unwrap() ?: return@launchSafe

        if (result.status == "scanning") {
            _isScanning.value = true
            return@launchSafe
        }

        _isScanning.value = false
        cache.save(result.data)
        applyLibrary(result.data)
    }

    private fun applyLibrary(data: List<AnimeEntry>) {
        _library.value = data

        // Hero carousel: ambil 6 dengan rating tertinggi yang punya banner
        _heroItems.value = data
            .filter { it.banner.isNotBlank() && it.hasRating }
            .sortedByDescending { it.rating }
            .take(6)

        // Continue watching
        _continueWatching.value = history.continueWatching()
    }

    /** Series (TV, ONA, OVA, Special) */
    val seriesItems: List<AnimeEntry>
        get() = (_library.value ?: emptyList())
            .filter { it.type.uppercase() != "MOVIE" }
            .sortedByDescending { it.rating }
            .take(20)

    /** Movies */
    val movieItems: List<AnimeEntry>
        get() = (_library.value ?: emptyList())
            .filter { it.type.uppercase() == "MOVIE" }
            .sortedByDescending { it.rating }
            .take(20)

    fun refreshContinueWatching() {
        _continueWatching.value = history.continueWatching()
    }
}
