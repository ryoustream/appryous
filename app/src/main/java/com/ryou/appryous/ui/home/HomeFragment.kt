package com.ryou.appryous.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.ryou.appryous.container
import com.ryou.appryous.data.model.AnimeEntry
import com.ryou.appryous.databinding.FragmentHomeBinding
import com.ryou.appryous.ui.detail.DetailActivity
import com.ryou.appryous.ui.home.adapter.HeroAdapter
import com.ryou.appryous.ui.home.adapter.PosterCardAdapter
import com.ryou.appryous.util.showIf
import java.util.Timer
import java.util.TimerTask
import com.google.android.material.snackbar.Snackbar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val vm: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(c: Class<T>): T {
                val ctx = requireContext()
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(
                    ctx.container.backendRepo,
                    ctx.container.libCache,
                    ctx.container.historyStore,
                    ctx.container.positionStore
                ) as T
            }
        }
    }

    private lateinit var heroAdapter: HeroAdapter
    private lateinit var seriesAdapter: PosterCardAdapter
    private lateinit var moviesAdapter: PosterCardAdapter
    private var autoScrollTimer: Timer? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHero()
        setupSections()
        observeViewModel()

        binding.swipeRefresh.setOnRefreshListener {
            vm.loadLibrary(forceRefresh = true)
        }
    }

    private fun setupHero() {
        heroAdapter = HeroAdapter { anime -> openDetail(anime) }
        binding.vpHero.apply {
            adapter = heroAdapter
            offscreenPageLimit = 1
            // Page transformer — slight scale effect
            setPageTransformer { page, position ->
                page.translationX = -position * 40f
                page.alpha = 1f - (0.2f * Math.abs(position))
            }
        }
        TabLayoutMediator(binding.heroIndicator, binding.vpHero) { _, _ -> }.attach()
    }

    private fun setupSections() {
        val onAnimeClick = { anime: AnimeEntry -> openDetail(anime) }

        // Continue watching
        binding.rvContinue.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }

        // Series
        seriesAdapter = PosterCardAdapter(onAnimeClick)
        binding.rvSeries.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = seriesAdapter
        }

        // Movies
        moviesAdapter = PosterCardAdapter(onAnimeClick)
        binding.rvMovies.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = moviesAdapter
        }

        // Section headers
        binding.headerSeries.tvSectionTitle.text = getString(com.ryou.appryous.R.string.home_all_series)
        binding.headerMovies.tvSectionTitle.text  = getString(com.ryou.appryous.R.string.home_all_movies)
        binding.headerContinue.tvSectionTitle.text = getString(com.ryou.appryous.R.string.home_continue_watching)

        binding.headerSeries.tvSeeAll.setOnClickListener {
            // Navigate ke ArchiveFragment dengan filter type=series
        }
        binding.headerMovies.tvSeeAll.setOnClickListener {
            // Navigate ke ArchiveFragment dengan filter type=movie
        }
        binding.headerContinue.tvSeeAll.visibility = View.GONE
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }

        vm.isScanning.observe(viewLifecycleOwner) { scanning ->
            binding.cardScanning.showIf(scanning)
        }

        vm.heroItems.observe(viewLifecycleOwner) { items ->
            heroAdapter.submitList(items)
            if (items.size > 1) startAutoScroll()
        }

        vm.library.observe(viewLifecycleOwner) { library ->
            if (library.isEmpty()) return@observe
            seriesAdapter.submitList(vm.seriesItems)
            moviesAdapter.submitList(vm.movieItems)
            binding.sectionSeries.showIf(vm.seriesItems.isNotEmpty())
            binding.sectionMovies.showIf(vm.movieItems.isNotEmpty())
        }

        vm.continueWatching.observe(viewLifecycleOwner) { items ->
            binding.sectionContinue.showIf(items.isNotEmpty())
        }

        vm.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) {
                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, err, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .setAction(getString(com.ryou.appryous.R.string.common_retry)) { vm.loadLibrary(true) }
                    .show()
                vm.clearError()
            }
        }
    }

    private fun startAutoScroll() {
        autoScrollTimer?.cancel()
        autoScrollTimer = Timer()
        autoScrollTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                binding.vpHero.post {
                    val count = heroAdapter.itemCount
                    if (count > 1) {
                        val next = (binding.vpHero.currentItem + 1) % count
                        binding.vpHero.setCurrentItem(next, true)
                    }
                }
            }
        }, 4000L, 4000L)
    }

    private fun openDetail(anime: AnimeEntry) {
        val intent = Intent(requireContext(), DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_ANIME_ID,    anime.id)
            putExtra(DetailActivity.EXTRA_ANIME_TITLE, anime.title)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshContinueWatching()
        startAutoScroll()
    }

    override fun onPause() {
        super.onPause()
        autoScrollTimer?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoScrollTimer?.cancel()
        _binding = null
    }
}
