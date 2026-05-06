package com.ryou.appryous.ui.detail

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ryou.appryous.BuildConfig
import com.ryou.appryous.R
import com.ryou.appryous.container
import com.ryou.appryous.data.model.AnimeEntry
import com.ryou.appryous.data.model.Episode
import com.ryou.appryous.databinding.ActivityDetailBinding
import com.ryou.appryous.ui.player.PlayerActivity
import com.ryou.appryous.util.*

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ANIME_ID    = "anime_id"
        const val EXTRA_ANIME_TITLE = "anime_title"
    }

    private lateinit var binding: ActivityDetailBinding

    private val vm: DetailViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(c: Class<T>): T {
                val ctx = this@DetailActivity
                @Suppress("UNCHECKED_CAST")
                return DetailViewModel(
                    ctx.container.backendRepo,
                    ctx.container.historyStore,
                    ctx.container.positionStore,
                    ctx.container.settingsStore
                ) as T
            }
        }
    }

    private lateinit var episodeAdapter: EpisodeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val animeId    = intent.getStringExtra(EXTRA_ANIME_ID)    ?: return finish()
        val animeTitle = intent.getStringExtra(EXTRA_ANIME_TITLE) ?: ""

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupEpisodeList()
        observeViewModel(animeTitle)

        vm.load(animeId)
    }

    private fun setupEpisodeList() {
        episodeAdapter = EpisodeAdapter { episode ->
            openPlayer(episode)
        }
        binding.rvEpisodes.apply {
            layoutManager = LinearLayoutManager(this@DetailActivity)
            adapter = episodeAdapter
        }
        binding.etSearchEp.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                vm.filterEpisodes(newText ?: "")
                return true
            }
        })
    }

    private fun observeViewModel(defaultTitle: String) {
        vm.anime.observe(this) { anime ->
            anime ?: return@observe
            renderAnimeInfo(anime)
        }

        vm.filteredEpisodes.observe(this) { episodes ->
            episodeAdapter.submitList(episodes)
            binding.tvEpCount.text = getString(R.string.detail_episode_count, episodes.size)
        }

        vm.loading.observe(this) { loading ->
            binding.progressBar.showIf(loading)
        }

        vm.continueEp.observe(this) { ep ->
            if (ep != null) {
                binding.btnPlay.text = getString(R.string.detail_continue, ep.ep)
                binding.btnPlay.setOnClickListener { openPlayer(ep) }
            }
        }

        vm.error.observe(this) { err ->
            if (!err.isNullOrBlank()) {
                toast(err, long = true)
                vm.clearError()
            }
        }
    }

    private fun renderAnimeInfo(anime: AnimeEntry) {
        val settings = container.settingsStore.get()

        binding.ivBanner.loadBanner(anime.banner)
        binding.ivPoster.loadImage(anime.poster)
        binding.tvTitle.text  = anime.title
        binding.tvTitleEn.text = anime.titleEn.ifBlank { anime.titleRomaji }
        binding.tvTitleEn.showIf(binding.tvTitleEn.text.isNotBlank())

        // Info grid
        binding.tvType.text   = anime.type
        binding.tvYear.text   = anime.year
        binding.tvScore.text  = anime.ratingFormatted
        binding.tvStatus.text = anime.status
        binding.tvStudio.text = anime.studio.ifBlank { "—" }
        binding.tvSource.text = anime.source.ifBlank { "—" }

        // Description
        val desc = anime.descFor(settings.descLang)
        binding.tvDesc.text = desc
        if (desc.length > 200) {
            binding.tvDesc.maxLines = 4
            binding.tvReadMore.show()
            binding.tvReadMore.setOnClickListener {
                val collapsed = binding.tvDesc.maxLines == 4
                binding.tvDesc.maxLines    = if (collapsed) Int.MAX_VALUE else 4
                binding.tvReadMore.text    = getString(
                    if (collapsed) R.string.detail_read_less else R.string.detail_read_more
                )
            }
        }

        // Genres chips
        anime.displayGenres.forEach { genre ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = genre
                isClickable = false
                setChipBackgroundColorResource(R.color.surface_container)
                setTextColor(getColor(R.color.on_surface_variant))
            }
            binding.chipGroupGenres.addView(chip)
        }

        // Play button (default: ep 1)
        binding.btnPlay.setOnClickListener {
            vm.filteredEpisodes.value?.firstOrNull()?.let { openPlayer(it) }
        }
    }

    private fun openPlayer(episode: Episode) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_ANIME_ID,    vm.animeId)
            putExtra(PlayerActivity.EXTRA_ANIME_TITLE, vm.anime.value?.title ?: "")
            putExtra(PlayerActivity.EXTRA_EP_INDEX,    vm.allEpisodes.value?.indexOf(episode) ?: 0)
        }
        startActivity(intent)
    }
}
