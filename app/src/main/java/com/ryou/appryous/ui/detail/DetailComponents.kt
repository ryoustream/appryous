package com.ryou.appryous.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ryou.appryous.data.local.HistoryStore
import com.ryou.appryous.data.local.PositionStore
import com.ryou.appryous.data.local.SettingsStore
import com.ryou.appryous.data.model.AnimeEntry
import com.ryou.appryous.data.model.Episode
import com.ryou.appryous.databinding.ItemEpisodeBinding
import com.ryou.appryous.data.repository.BackendRepository
import com.ryou.appryous.util.BaseViewModel
import com.ryou.appryous.util.showIf

// ── ViewModel ─────────────────────────────────────────────────────────────────

class DetailViewModel(
    private val repo:     BackendRepository,
    private val history:  HistoryStore,
    private val position: PositionStore,
    private val settings: SettingsStore
) : BaseViewModel() {

    var animeId = ""
        private set

    private val _anime    = MutableLiveData<AnimeEntry?>()
    val anime: LiveData<AnimeEntry?> = _anime

    private val _allEpisodes = MutableLiveData<List<Episode>>(emptyList())
    val allEpisodes: LiveData<List<Episode>> = _allEpisodes

    private val _filteredEpisodes = MutableLiveData<List<Episode>>(emptyList())
    val filteredEpisodes: LiveData<List<Episode>> = _filteredEpisodes

    private val _continueEp = MutableLiveData<Episode?>()
    val continueEp: LiveData<Episode?> = _continueEp

    private var searchQuery = ""

    fun load(id: String) {
        animeId = id
        launchSafe {
            // Library untuk data anime
            val libResult = repo.getLibrary().unwrap() ?: return@launchSafe
            val anime = libResult.data.find { it.id == id }
            _anime.value = anime

            // Episodes
            val epResult = repo.getEpisodes(id).unwrap() ?: return@launchSafe
            _allEpisodes.value = epResult.episodes
            applyFilter()

            // Continue watching
            val lastHistory = history.getAll().firstOrNull { it.animeId == id }
            if (lastHistory != null) {
                _continueEp.value = epResult.episodes.find { it.ep == lastHistory.ep }
            }
        }
    }

    fun filterEpisodes(q: String) {
        searchQuery = q
        applyFilter()
    }

    private fun applyFilter() {
        val all = _allEpisodes.value ?: emptyList()
        _filteredEpisodes.value = if (searchQuery.isBlank()) all
        else all.filter {
            it.ep.toString().contains(searchQuery) ||
            it.title.contains(searchQuery, ignoreCase = true)
        }
    }
}

// ── Episode Adapter ───────────────────────────────────────────────────────────

class EpisodeAdapter(
    private val onClick: (Episode) -> Unit
) : ListAdapter<Episode, EpisodeAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemEpisodeBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(ep: Episode) {
            b.tvEpNum.text   = "Ep ${ep.ep}"
            b.tvEpTitle.text = ep.title.ifBlank { "Episode ${ep.ep}" }
            b.tvDuration.text = ep.duration.ifBlank { "" }
            b.tvDuration.showIf(ep.duration.isNotBlank())
            b.ivThumb.showIf(ep.thumbnail.isNotBlank())
            if (ep.thumbnail.isNotBlank()) {
                com.bumptech.glide.Glide.with(b.root)
                    .load(ep.thumbnail)
                    .placeholder(com.ryou.appryous.R.drawable.placeholder_banner)
                    .into(b.ivThumb)
            }
            b.root.setOnClickListener { onClick(ep) }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int) =
        VH(ItemEpisodeBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Episode>() {
            override fun areItemsTheSame(a: Episode, b: Episode) = a.ep == b.ep
            override fun areContentsTheSame(a: Episode, b: Episode) = a == b
        }
    }
}
