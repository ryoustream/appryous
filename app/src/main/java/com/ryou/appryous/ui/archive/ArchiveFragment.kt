package com.ryou.appryous.ui.archive

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.GridLayoutManager
import com.ryou.appryous.R
import com.ryou.appryous.container
import com.ryou.appryous.data.local.LibCache
import com.ryou.appryous.data.model.AnimeEntry
import com.ryou.appryous.data.repository.BackendRepository
import com.ryou.appryous.databinding.FragmentArchiveBinding
import com.ryou.appryous.ui.detail.DetailActivity
import com.ryou.appryous.ui.home.adapter.PosterCardAdapter
import com.ryou.appryous.util.BaseViewModel
import com.ryou.appryous.util.showIf

// ── ViewModel ─────────────────────────────────────────────────────────────────

enum class SortMode {
    TITLE_AZ, TITLE_ZA, YEAR_NEW, YEAR_OLD, RATING, EPISODES, ADDED
}

class ArchiveViewModel(
    private val repo: BackendRepository,
    private val cache: LibCache
) : BaseViewModel() {

    private var allData:    List<AnimeEntry> = emptyList()
    private var sortMode:   SortMode = SortMode.TITLE_AZ
    private var filterType: String   = ""
    private var filterGenre:String   = ""
    private var page:       Int      = 0
    private val pageSize    = 40

    private val _items    = MutableLiveData<List<AnimeEntry>>(emptyList())
    val items: LiveData<List<AnimeEntry>> = _items

    private val _total = MutableLiveData(0)
    val total: LiveData<Int> = _total

    init { load() }

    fun load() = launchSafe {
        allData = cache.load() ?: run {
            val lib = repo.getLibrary().getOrNull()?.data ?: emptyList()
            cache.save(lib); lib
        }
        apply()
    }

    fun setSort(mode: SortMode)   { sortMode = mode;    apply() }
    fun setFilterType(t: String)  { filterType = t;     page = 0; apply() }
    fun setFilterGenre(g: String) { filterGenre = g;    page = 0; apply() }
    fun nextPage()                { page++;              apply() }
    fun reset()                   { filterType = ""; filterGenre = ""; page = 0; apply() }

    private fun apply() {
        var list = allData
        if (filterType.isNotBlank())  list = list.filter { it.type.equals(filterType,  true) }
        if (filterGenre.isNotBlank()) list = list.filter { it.genres.any { g -> g.equals(filterGenre, true) } }
        list = when (sortMode) {
            SortMode.TITLE_AZ  -> list.sortedBy      { it.title }
            SortMode.TITLE_ZA  -> list.sortedByDescending { it.title }
            SortMode.YEAR_NEW  -> list.sortedByDescending { it.year }
            SortMode.YEAR_OLD  -> list.sortedBy      { it.year }
            SortMode.RATING    -> list.sortedByDescending { it.rating }
            SortMode.EPISODES  -> list.sortedByDescending { it.episodes }
            SortMode.ADDED     -> list
        }
        _total.value = list.size
        _items.value = list.take((page + 1) * pageSize)
    }
}

// ── Fragment ──────────────────────────────────────────────────────────────────

class ArchiveFragment : Fragment() {
    private var _b: FragmentArchiveBinding? = null
    private val b get() = _b!!

    private val vm: ArchiveViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(c: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ArchiveViewModel(requireContext().container.backendRepo,
                    requireContext().container.libCache) as T
            }
        }
    }

    private val adapter = PosterCardAdapter { anime ->
        startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_ANIME_ID, anime.id)
            putExtra(DetailActivity.EXTRA_ANIME_TITLE, anime.title)
        })
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentArchiveBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        b.rvArchive.layoutManager = GridLayoutManager(requireContext(), 3)
        b.rvArchive.adapter = adapter
        vm.items.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            b.tvEmpty.showIf(it.isEmpty())
        }
        vm.total.observe(viewLifecycleOwner) {
            b.tvCount.text = getString(R.string.archive_results, it)
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
