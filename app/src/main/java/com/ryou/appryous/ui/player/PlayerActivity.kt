package com.ryou.appryous.ui.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ryou.appryous.R
import com.ryou.appryous.container
import com.ryou.appryous.data.model.Chapter
import com.ryou.appryous.databinding.ActivityPlayerBinding
import com.ryou.appryous.player.mpv.MPVLib  // typealias → is.xyz.mpv.MPVLib
import com.ryou.appryous.player.mpv.MpvPlayerHelper
import com.ryou.appryous.player.mpv.MpvTrack
import com.ryou.appryous.util.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class PlayerActivity : AppCompatActivity(), MPVLib.EventObserver {

    companion object {
        const val EXTRA_ANIME_ID    = "anime_id"
        const val EXTRA_ANIME_TITLE = "anime_title"
        const val EXTRA_EP_INDEX    = "ep_index"
    }

    private lateinit var binding: ActivityPlayerBinding

    private val vm: PlayerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(c: Class<T>): T {
                val ctx = this@PlayerActivity
                @Suppress("UNCHECKED_CAST")
                return PlayerViewModel(
                    ctx.container.backendRepo,
                    ctx.container.historyStore,
                    ctx.container.positionStore,
                    ctx.container.settingsStore
                ) as T
            }
        }
    }

    private lateinit var episodeDrawerAdapter: PlayerEpisodeAdapter
    private var positionUpdateJob: Job? = null
    private var controlsHideTimer: Timer? = null
    private var autoNextTimer: Timer? = null
    private var controlsVisible = true

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()

        val animeId    = intent.getStringExtra(EXTRA_ANIME_ID)    ?: return finish()
        val animeTitle = intent.getStringExtra(EXTRA_ANIME_TITLE) ?: ""
        val epIndex    = intent.getIntExtra(EXTRA_EP_INDEX, 0)

        // Init mpv
        binding.mpvView.initialize(
            configDir = filesDir.path,
            cacheDir  = cacheDir.path,
            logLevel  = if (com.ryou.appryous.BuildConfig.DEBUG) "info" else "warn"
        )
        MPVLib.addObserver(this)

        setupControls()
        setupEpisodeDrawer()
        observeViewModel(animeTitle)

        vm.load(animeId, epIndex)
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupControls() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }
        binding.btnRewind.setOnClickListener   { binding.mpvView.seekRelative(-10) }
        binding.btnForward.setOnClickListener  { binding.mpvView.seekRelative(10) }
        binding.btnSubtitle.setOnClickListener { showTrackSelector("sub") }
        binding.btnAudio.setOnClickListener    { showTrackSelector("audio") }
        binding.btnChapters.setOnClickListener { showChaptersSheet() }
        binding.btnEpisodes.setOnClickListener { toggleDrawer() }
        binding.btnPip.setOnClickListener      { enterPiP() }

        // Seek bar
        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurrentTime.text = (p.toLong() * 1000L).msToTimeString()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar) {
                binding.mpvView.seekTo(sb.progress.toDouble())
            }
        })

        // Tap to toggle controls
        binding.mpvView.setOnClickListener { toggleControls() }

        // Auto-next overlay
        binding.btnCancelAutoNext.setOnClickListener {
            cancelAutoNext()
            binding.layoutAutoNext.hide()
        }
    }

    private fun setupEpisodeDrawer() {
        episodeDrawerAdapter = PlayerEpisodeAdapter { episode ->
            vm.playEpisode(episode)
            binding.drawerEpisodes.hide()
        }
        binding.rvDrawerEpisodes.apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = episodeDrawerAdapter
        }
        binding.etDrawerSearch.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                vm.filterEpisodes(newText ?: "")
                return true
            }
        })
    }

    // ── ViewModel observers ───────────────────────────────────────────────────

    private fun observeViewModel(animeTitle: String) {
        vm.currentEpisode.observe(this) { ep ->
            ep ?: return@observe
            binding.tvTitle.text = "$animeTitle – Ep ${ep.ep}"

            // Load ke mpv
            val settings    = container.settingsStore.get()
            val startPos    = if (settings.rememberPos)
                container.positionStore.getTime(vm.animeId, ep.ep) else 0.0

            MpvPlayerHelper.loadEpisode(
                view         = binding.mpvView,
                episode      = ep,
                settings     = settings,
                startSeconds = startPos
            )

            // Chapters dari backend (external XML) — sebagai fallback
            if (ep.hasChapters) {
                vm.loadChapters(ep.chapterFile!!)
            }
        }

        vm.filteredEpisodes.observe(this) { eps ->
            episodeDrawerAdapter.submitList(eps)
        }

        vm.chapters.observe(this) { chapters ->
            // Tampilkan chapter markers di seek bar
            renderChapterMarkers(chapters)
            binding.btnChapters.showIf(chapters.isNotEmpty())
        }

        vm.isPlaying.observe(this) { playing ->
            binding.btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        vm.showAutoNext.observe(this) { show ->
            if (show) startAutoNextCountdown()
            else binding.layoutAutoNext.hide()
        }
    }

    // ── mpv EventObserver ─────────────────────────────────────────────────────

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.EVENT_FILE_LOADED -> runOnUiThread {
                vm.onFileLoaded()
                loadExternalSubtitles()
                readEmbeddedChapters()
                startPositionUpdate()
            }
            MPVLib.EVENT_END_FILE -> runOnUiThread {
                val settings = container.settingsStore.get()
                if (settings.autoNext) vm.requestAutoNext()
            }
            MPVLib.EVENT_SEEK -> runOnUiThread {
                binding.progressBuffering.hide()
            }
        }
    }

    override fun eventProperty(property: String) {}

    override fun eventProperty(property: String, value: Long) {
        if (property == "time-pos") runOnUiThread { updateSeekBar(value.toDouble()) }
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause") runOnUiThread { vm.setPlaying(!value) }
    }

    override fun eventProperty(property: String, value: Double) {
        if (property == "time-pos") runOnUiThread { updateSeekBar(value) }
    }

    override fun eventProperty(property: String, value: String) {}

    // ── Player Actions ────────────────────────────────────────────────────────

    private fun togglePlayPause() {
        binding.mpvView.paused = !binding.mpvView.paused
    }

    private fun updateSeekBar(timeSecs: Double) {
        val duration = binding.mpvView.duration
        if (duration <= 0) return
        binding.seekBar.max      = duration.toInt()
        binding.seekBar.progress = timeSecs.toInt()
        binding.tvCurrentTime.text = (timeSecs.toLong() * 1000L).msToTimeString()
        binding.tvDuration.text    = (duration.toLong() * 1000L).msToTimeString()

        // Save position
        vm.savePosition(timeSecs, duration)
    }

    private fun loadExternalSubtitles() {
        val ep       = vm.currentEpisode.value ?: return
        val baseUrl  = container.gistRepo.cachedUrl ?: return
        val settings = container.settingsStore.get()
        MpvPlayerHelper.addExternalSubtitles(
            view          = binding.mpvView,
            subtitles     = ep.subtitles,
            preferredLang = settings.descLang,
            baseUrl       = baseUrl
        )
    }

    /**
     * Baca embedded chapters dari MKV — ini yang otomatis terbaca libmpv
     * via MatroskaExtractor (Matroska Editions/Chapters block).
     */
    private fun readEmbeddedChapters() {
        val embedded = binding.mpvView.readEmbeddedChapters()
        if (embedded.isNotEmpty()) {
            vm.setEmbeddedChapters(embedded.map { (time, title) ->
                Chapter(time = time, title = title)
            })
        }
    }

    private fun startPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = lifecycleScope.launch {
            while (isActive) {
                MPVLib.observeProperty("time-pos", MPVLib.FORMAT_DOUBLE)
                delay(1000)
            }
        }
    }

    private fun renderChapterMarkers(chapters: List<Chapter>) {
        // Render marker di atas seekbar via custom overlay
        binding.chapterMarkerContainer.removeAllViews()
        val duration = binding.mpvView.duration
        if (duration <= 0 || chapters.isEmpty()) return

        chapters.forEach { ch ->
            val pct = (ch.time / duration).toFloat().coerceIn(0f, 1f)
            val marker = View(this).apply {
                setBackgroundColor(getColor(R.color.accent_purple))
                layoutParams = android.widget.FrameLayout.LayoutParams(2.dpToPx(), 12.dpToPx()).apply {
                    leftMargin = (pct * binding.seekBar.width).toInt()
                }
            }
            binding.chapterMarkerContainer.addView(marker)
        }
    }

    private fun Int.dpToPx() = (this * resources.displayMetrics.density).toInt()

    // ── Controls visibility ───────────────────────────────────────────────────

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        controlsVisible = true
        binding.controlsTop.show()
        binding.controlsBottom.show()
        scheduleControlsHide()
    }

    private fun hideControls() {
        controlsVisible = false
        binding.controlsTop.hide()
        binding.controlsBottom.hide()
    }

    private fun scheduleControlsHide() {
        controlsHideTimer?.cancel()
        controlsHideTimer = Timer()
        controlsHideTimer?.schedule(object : TimerTask() {
            override fun run() { runOnUiThread { if (!binding.mpvView.paused) hideControls() } }
        }, 3000)
    }

    private fun toggleDrawer() {
        if (binding.drawerEpisodes.visibility == View.VISIBLE) {
            binding.drawerEpisodes.hide()
        } else {
            binding.drawerEpisodes.show()
        }
    }

    // ── Auto-next ─────────────────────────────────────────────────────────────

    private fun startAutoNextCountdown() {
        var countdown = 5
        binding.layoutAutoNext.show()
        binding.tvAutoNextCountdown.text = getString(R.string.player_auto_next, countdown)

        autoNextTimer?.cancel()
        autoNextTimer = Timer()
        autoNextTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                countdown--
                runOnUiThread {
                    if (countdown <= 0) {
                        binding.layoutAutoNext.hide()
                        vm.playNext()
                    } else {
                        binding.tvAutoNextCountdown.text =
                            getString(R.string.player_auto_next, countdown)
                    }
                }
            }
        }, 1000, 1000)
    }

    private fun cancelAutoNext() {
        autoNextTimer?.cancel()
        vm.cancelAutoNext()
    }

    // ── PiP ───────────────────────────────────────────────────────────────────

    private fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPiP: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPiP, newConfig)
        if (isInPiP) {
            binding.controlsTop.hide()
            binding.controlsBottom.hide()
            binding.drawerEpisodes.hide()
        } else {
            showControls()
        }
    }

    // ── Track selector ────────────────────────────────────────────────────────

    private fun showTrackSelector(type: String) {
        val tracks = binding.mpvView.readTrackList()
            .filter { it.type == type }
        TrackSelectorSheet(tracks) { track ->
            when (type) {
                "sub"   -> binding.mpvView.subtitleTrack = track.id
                "audio" -> binding.mpvView.audioTrack    = track.id
            }
        }.show(supportFragmentManager, "track_$type")
    }

    private fun showChaptersSheet() {
        val chapters = vm.chapters.value ?: return
        ChaptersSheet(chapters) { chapter ->
            binding.mpvView.seekTo(chapter.time, precise = true)
        }.show(supportFragmentManager, "chapters")
    }

    // ── System UI ─────────────────────────────────────────────────────────────

    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode) binding.mpvView.paused = true
    }

    override fun onDestroy() {
        super.onDestroy()
        positionUpdateJob?.cancel()
        controlsHideTimer?.cancel()
        autoNextTimer?.cancel()
        MPVLib.removeObserver(this)
        binding.mpvView.destroy()
    }
}
