package com.ryou.appryous.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.ryou.appryous.container
import com.ryou.appryous.data.model.AnimeEntry
import com.ryou.appryous.databinding.FragmentSearchBinding
import com.ryou.appryous.ui.detail.DetailActivity
import com.ryou.appryous.ui.home.adapter.PosterCardAdapter
import com.ryou.appryous.util.BaseViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ryou.appryous.data.local.LibCache
import com.ryou.appryous.data.repository.BackendRepository
import com.ryou.appryous.util.showIf

class SearchViewModel(
    private val repo: BackendRepository,
    private val cache: LibCache
) : BaseViewModel() {

    private val _results = MutableLiveData<List<AnimeEntry>>(emptyList())
    val results: LiveData<List<AnimeEntry>> = _results

    private var allData: List<AnimeEntry> = emptyList()

    init {
        launchSafe(showLoading = false) {
            allData = cache.load() ?: run {
                val lib = repo.getLibrary().getOrNull()?.data ?: emptyList()
                cache.save(lib); lib
            }
        }
    }

    fun search(q: String) {
        if (q.isBlank()) { _results.value = emptyList(); return }
        val lower = q.lowercase()
        _results.value = allData.filter {
            it.title.contains(lower, true) ||
            it.titleEn.contains(lower, true) ||
            it.titleJa.contains(lower, true) ||
            it.genres.any { g -> g.contains(lower, true) }
        }
    }
}

class SearchFragment : Fragment() {
    private var _b: FragmentSearchBinding? = null
    private val b get() = _b!!
    private val vm: SearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(c: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(requireContext().container.backendRepo,
                    requireContext().container.libCache) as T
            }
        }
    }
    private val adapter = PosterCardAdapter { openDetail(it) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSearchBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        b.rvResults.layoutManager = GridLayoutManager(requireContext(), 3)
        b.rvResults.adapter = adapter
        b.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = true.also { vm.search(q ?: "") }
            override fun onQueryTextChange(q: String?) = true.also { vm.search(q ?: "") }
        })
        vm.results.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            b.tvEmpty.showIf(it.isEmpty() && b.searchView.query.isNotBlank())
        }
    }

    private fun openDetail(anime: AnimeEntry) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_ANIME_ID, anime.id)
            putExtra(DetailActivity.EXTRA_ANIME_TITLE, anime.title)
        })
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
