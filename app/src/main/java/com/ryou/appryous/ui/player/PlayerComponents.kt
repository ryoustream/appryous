package com.ryou.appryous.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ryou.appryous.R
import com.ryou.appryous.data.local.HistoryStore
import com.ryou.appryous.data.local.PositionStore
import com.ryou.appryous.data.local.SettingsStore
import com.ryou.appryous.data.model.Chapter
import com.ryou.appryous.data.model.Episode
import com.ryou.appryous.data.model.HistoryItem
import com.ryou.appryous.data.repository.BackendRepository
import com.ryou.appryous.databinding.ItemEpisodeDrawerBinding
import com.ryou.appryous.databinding.SheetChaptersBinding
import com.ryou.appryous.databinding.SheetTrackSelectorBinding
import com.ryou.appryous.player.mpv.MpvTrack
import com.ryou.appryous.util.BaseViewModel
import com.ryou.appryous.util.showIf
import com.ryou.appryous.util.toTimeString
import android.graphics.Typeface

// ── ViewModel ─────────────────────────────────────────────────────────────────

class PlayerViewModel(
    private val repo:     BackendRepository,
    private val history:  HistoryStore,
    private val position: PositionStore,
    private val settings: SettingsStore
) : BaseViewModel() {

    var animeId = ""
        private set

    private val _allEpisodes = MutableLiveData<List<Episode>>(emptyList())
    val allEpisodes: LiveData<List<Episode>> = _allEpisodes

    private val _filteredEpisodes = MutableLiveData<List<Episode>>(emptyList())
    val filteredEpisodes: LiveData<List<Episode>> = _filteredEpisodes

    private val _currentEpisode = MutableLiveData<Episode?>()
    val currentEpisode: LiveData<Episode?> = _currentEpisode

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _chapters = MutableLiveData<List<Chapter>>(emptyList())
    val chapters: LiveData<List<Chapter>> = _chapters

    private val _showAutoNext = MutableLiveData(false)
    val showAutoNext: LiveData<Boolean> = _showAutoNext

    private var currentIndex = 0

    fun load(id: String, startIndex: Int) {
        animeId = id
        launchSafe {
            val result = repo.getEpisodes(id).unwrap() ?: return@launchSafe
            _allEpisodes.value = result.episodes
            _filteredEpisodes.value = result.episodes
            playAtIndex(startIndex)
        }
    }

    fun playEpisode(ep: Episode) {
        val idx = _allEpisodes.value?.indexOf(ep) ?: 0
        playAtIndex(idx)
    }

    fun playNext() {
        val list = _allEpisodes.value ?: return
        if (currentIndex < list.size - 1) playAtIndex(currentIndex + 1)
    }

    fun requestAutoNext() {
        val list = _allEpisodes.value ?: return
        if (currentIndex < list.size - 1) _showAutoNext.value = true
    }

    fun cancelAutoNext() { _showAutoNext.value = false }

    fun filterEpisodes(q: String) {
        val all = _allEpisodes.value ?: return
        _filteredEpisodes.value = if (q.isBlank()) all
        else all.filter {
            it.ep.toString().contains(q) || it.title.contains(q, ignoreCase = true)
        }
    }

    fun onFileLoaded() { _showAutoNext.value = false }

    fun setPlaying(playing: Boolean) { _isPlaying.value = playing }

    fun savePosition(timeSec: Double, duration: Double) {
        val ep = _currentEpisode.value ?: return
        position.save(animeId, ep.ep, timeSec, duration)
    }

    fun loadChapters(path: String) = launchSafe(showLoading = false) {
        val result = repo.getChapters(path).unwrap() ?: return@launchSafe
        if (result.chapters.isNotEmpty()) _chapters.value = result.chapters
    }

    fun setEmbeddedChapters(chapters: List<Chapter>) {
        if (_chapters.value.isNullOrEmpty()) _chapters.value = chapters
    }

    private fun playAtIndex(index: Int) {
        val list = _allEpisodes.value ?: return
        if (index !in list.indices) return
        currentIndex = index
        val ep = list[index]
        _currentEpisode.value = ep
        _chapters.value = emptyList()

        // Simpan ke history
        history.add(HistoryItem(
            animeId    = animeId,
            animeTitle = "",
            poster     = "",
            ep         = ep.ep,
            epTitle    = ep.title
        ))
    }
}

// ── Player Episode Drawer Adapter ─────────────────────────────────────────────

class PlayerEpisodeAdapter(
    private val onClick: (Episode) -> Unit
) : ListAdapter<Episode, PlayerEpisodeAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemEpisodeDrawerBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(ep: Episode) {
            b.tvEpNum.text   = "Ep ${ep.ep}"
            b.tvEpTitle.text = ep.title.ifBlank { "Episode ${ep.ep}" }
            b.root.setOnClickListener { onClick(ep) }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int) =
        VH(ItemEpisodeDrawerBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Episode>() {
            override fun areItemsTheSame(a: Episode, b: Episode) = a.ep == b.ep
            override fun areContentsTheSame(a: Episode, b: Episode) = a == b
        }
    }
}

// ── Track Selector Bottom Sheet ───────────────────────────────────────────────

class TrackSelectorSheet(
    private val tracks:   List<MpvTrack>,
    private val onSelect: (MpvTrack) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val b = SheetTrackSelectorBinding.inflate(i, c, false)
        b.rvTracks.layoutManager = LinearLayoutManager(requireContext())
        b.rvTracks.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class VH(val tv: android.widget.TextView) : RecyclerView.ViewHolder(tv)
            override fun getItemCount() = tracks.size + 1  // +1 untuk "Tidak Ada"
            override fun onCreateViewHolder(p: ViewGroup, v: Int): RecyclerView.ViewHolder {
                val tv = android.widget.TextView(requireContext()).apply {
                    setPadding(48, 32, 48, 32)
                    setTextColor(context.getColor(R.color.on_surface))
                    textSize = 14f
                }
                return VH(tv)
            }
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                val tv = (h as VH).tv
                if (pos == 0) {
                    tv.text = getString(R.string.player_no_sub)
                    tv.setOnClickListener {
                        onSelect(MpvTrack(-1, "", "", "", "", false, false, false))
                        dismissAllowingStateLoss()
                    }
                } else {
                    val track = tracks[pos - 1]
                    tv.text = track.displayLabel
                    if (track.selected) tv.setTypeface(null, android.graphics.Typeface.BOLD)
                    tv.setOnClickListener { onSelect(track); dismissAllowingStateLoss() }
                }
            }
        }
        return b.root
    }
}

// ── Chapters Bottom Sheet ─────────────────────────────────────────────────────

class ChaptersSheet(
    private val chapters: List<Chapter>,
    private val onSeek:   (Chapter) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val b = SheetChaptersBinding.inflate(i, c, false)
        b.rvChapters.layoutManager = LinearLayoutManager(requireContext())
        b.rvChapters.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class VH(val root: ViewGroup) : RecyclerView.ViewHolder(root)
            override fun getItemCount() = chapters.size
            override fun onCreateViewHolder(p: ViewGroup, v: Int): RecyclerView.ViewHolder {
                val row = android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(48, 24, 48, 24)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                return VH(row)
            }
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                val ch   = chapters[pos]
                val root = (h as VH).root as android.widget.LinearLayout
                root.removeAllViews()
                android.widget.TextView(requireContext()).apply {
                    text      = ch.timeFormatted
                    textSize  = 12f
                    minWidth  = 80.dpToPx()
                    setTextColor(context.getColor(R.color.on_surface_variant))
                }.also { root.addView(it) }
                android.widget.TextView(requireContext()).apply {
                    text      = ch.title
                    textSize  = 14f
                    setTextColor(context.getColor(R.color.on_surface))
                }.also { root.addView(it) }
                root.setOnClickListener { onSeek(ch); dismissAllowingStateLoss() }
            }
        }
        return b.root
    }

    private fun Int.dpToPx() = (this * resources.displayMetrics.density).toInt()
}
