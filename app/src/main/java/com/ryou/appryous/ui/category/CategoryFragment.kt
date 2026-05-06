package com.ryou.appryous.ui.category

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.ryou.appryous.R
import com.ryou.appryous.container
import com.ryou.appryous.data.local.LibCache
import com.ryou.appryous.data.model.AnimeEntry
import com.ryou.appryous.data.repository.BackendRepository
import com.ryou.appryous.databinding.FragmentCategoryBinding
import com.ryou.appryous.ui.detail.DetailActivity
import com.ryou.appryous.ui.home.adapter.PosterCardAdapter
import com.ryou.appryous.util.BaseViewModel
import com.ryou.appryous.util.showIf

private val GENRES = listOf(
    "Action","Adventure","Comedy","Drama","Fantasy",
    "Horror","Mystery","Romance","Sci-Fi","Slice of Life",
    "Sports","Supernatural","Thriller","Music","Isekai"
)

class CategoryViewModel(
    private val repo:  BackendRepository,
    private val cache: LibCache
) : BaseViewModel() {

    private var allData = emptyList<AnimeEntry>()

    private val _selected = MutableLiveData("Action")
    val selected: LiveData<String> = _selected

    private val _items = MutableLiveData<List<AnimeEntry>>(emptyList())
    val items: LiveData<List<AnimeEntry>> = _items

    val genres: List<String> get() = GENRES

    init { load() }

    private fun load() = launchSafe {
        allData = cache.load() ?: run {
            val lib = repo.getLibrary().getOrNull()?.data ?: emptyList()
            cache.save(lib); lib
        }
        selectGenre(_selected.value ?: "Action")
    }

    fun selectGenre(genre: String) {
        _selected.value = genre
        _items.value = allData.filter {
            it.genres.any { g -> g.equals(genre, true) } ||
            it.genresDisp.any { g -> g.equals(genre, true) }
        }.sortedByDescending { it.rating }
    }
}

class CategoryFragment : Fragment() {
    private var _b: FragmentCategoryBinding? = null
    private val b get() = _b!!

    private val vm: CategoryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(c: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CategoryViewModel(
                    requireContext().container.backendRepo,
                    requireContext().container.libCache
                ) as T
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
        FragmentCategoryBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        // Genre chips
        vm.genres.forEach { genre ->
            val chip = Chip(requireContext()).apply {
                text = genre
                isCheckable = true
                setChipBackgroundColorResource(R.color.surface_variant)
                setTextColor(requireContext().getColor(R.color.on_surface_variant))
                setOnClickListener { vm.selectGenre(genre) }
            }
            b.chipGroupGenres.addView(chip)
        }

        b.rvItems.layoutManager = GridLayoutManager(requireContext(), 3)
        b.rvItems.adapter = adapter

        vm.selected.observe(viewLifecycleOwner) { selected ->
            b.tvGenreTitle.text = selected
            // Update chip checked state
            for (i in 0 until b.chipGroupGenres.childCount) {
                val chip = b.chipGroupGenres.getChildAt(i) as? Chip ?: continue
                chip.isChecked = chip.text == selected
            }
        }

        vm.items.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            b.tvEmpty.showIf(items.isEmpty())
        }

        vm.loading.observe(viewLifecycleOwner) { b.progress.showIf(it) }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
