package com.ryou.appryous.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.lifecycle.viewModelScope
import com.ryou.appryous.R
import com.ryou.appryous.container
import com.ryou.appryous.data.local.SettingsStore
import com.ryou.appryous.data.repository.BackendRepository
import com.ryou.appryous.databinding.FragmentSettingsBinding
import androidx.navigation.fragment.findNavController
import com.ryou.appryous.util.BaseViewModel
import com.ryou.appryous.util.ScanPoller
import com.ryou.appryous.util.showIf
import com.ryou.appryous.util.toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class SettingsViewModel(
    private val repo:  BackendRepository,
    private val store: SettingsStore
) : BaseViewModel() {

    private val _scanProgress = MutableLiveData<String?>(null)
    val scanProgress: LiveData<String?> = _scanProgress

    private val _scanRunning = MutableLiveData(false)
    val scanRunning: LiveData<Boolean> = _scanRunning

    fun triggerScan(force: Boolean = false) = launchSafe {
        _scanRunning.value = true
        repo.triggerScan(force)
        // Poll status dengan backoff (15s→30s→60s) — mirror epsilon
        viewModelScope.launch {
            ScanPoller.pollFlow(repo).collectLatest { state ->
                when (state) {
                    is ScanPoller.ScanPollState.Progress -> _scanProgress.value = state.msg
                    is ScanPoller.ScanPollState.Done     -> {
                        _scanProgress.value = null
                        _scanRunning.value  = false
                    }
                    is ScanPoller.ScanPollState.Error    -> {
                        _scanProgress.value = state.msg
                        _scanRunning.value  = false
                    }
                    else -> {}
                }
            }
        }
    }

    fun clearCache(type: String) = launchSafe {
        repo.clearCache(type)
    }
}

class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    private val vm: SettingsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(c: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(
                    requireContext().container.backendRepo,
                    requireContext().container.settingsStore
                ) as T
            }
        }
    }

    private val store get() = requireContext().container.settingsStore

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSettingsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        loadSettings()
        setupListeners()
        observeViewModel()
    }

    private fun loadSettings() {
        val cfg = store.get()
        b.switchAutoplay.isChecked    = cfg.autoplay
        b.switchAutoNext.isChecked    = cfg.autoNext
        b.switchRememberPos.isChecked = cfg.rememberPos
        b.switchSubtitle.isChecked    = cfg.subtitleEnabled
        b.switchAnimations.isChecked  = cfg.animations

        // Desc lang radio
        when (cfg.descLang) {
            "en" -> b.rgDescLang.check(R.id.rb_lang_en)
            "ja" -> b.rgDescLang.check(R.id.rb_lang_ja)
            else -> b.rgDescLang.check(R.id.rb_lang_id)
        }

        // Card layout
        when (cfg.cardLayout) {
            "wide" -> b.rgCardLayout.check(R.id.rb_layout_wide)
            else   -> b.rgCardLayout.check(R.id.rb_layout_poster)
        }

        // Accent color preview
        runCatching { b.vAccentPreview.setBackgroundColor(Color.parseColor(cfg.accentColor)) }
    }

    private fun setupListeners() {
        b.switchAutoplay.setOnCheckedChangeListener    { _, v -> store.setAutoplay(v) }
        b.switchAutoNext.setOnCheckedChangeListener    { _, v -> store.setAutoNext(v) }
        b.switchRememberPos.setOnCheckedChangeListener { _, v -> store.setRememberPos(v) }
        b.switchSubtitle.setOnCheckedChangeListener    { _, v -> store.setSubtitleEnabled(v) }
        b.switchAnimations.setOnCheckedChangeListener  { _, v -> store.setAnimations(v) }

        b.rgDescLang.setOnCheckedChangeListener { _, id ->
            store.setDescLang(when (id) {
                R.id.rb_lang_en -> "en"
                R.id.rb_lang_ja -> "ja"
                else             -> "id"
            })
        }
        b.rgCardLayout.setOnCheckedChangeListener { _, id ->
            store.setCardLayout(if (id == R.id.rb_layout_wide) "wide" else "poster")
        }

        // Accent color picker
        b.btnAccentColor.setOnClickListener { showColorPicker() }

        // Scan
        b.btnScan.setOnClickListener      { vm.triggerScan(false) }
        b.btnScanForce.setOnClickListener { vm.triggerScan(true) }

        // About
        b.btnAbout.setOnClickListener {
            findNavController().navigate(com.ryou.appryous.R.id.aboutFragment)
        }

        // Clear cache
        b.btnClearCache.setOnClickListener {
            vm.clearCache("all")
            requireContext().container.libCache.clear()
            toast(getString(R.string.settings_scan_done))
        }
    }

    private fun observeViewModel() {
        vm.scanRunning.observe(viewLifecycleOwner) { running ->
            b.btnScan.isEnabled      = !running
            b.btnScanForce.isEnabled = !running
            b.progressScan.showIf(running)
        }
        vm.scanProgress.observe(viewLifecycleOwner) { msg ->
            b.tvScanProgress.showIf(!msg.isNullOrBlank())
            b.tvScanProgress.text = msg ?: ""
        }
        vm.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) { toast(err); vm.clearError() }
        }
    }

    private fun showColorPicker() {
        val colors = mapOf(
            getString(R.string.accent_purple) to "#7c3aed",
            getString(R.string.accent_blue)   to "#2563eb",
            getString(R.string.accent_green)  to "#059669",
            getString(R.string.accent_red)    to "#dc2626",
            getString(R.string.accent_orange) to "#d97706",
            getString(R.string.accent_pink)   to "#db2777"
        )
        val names = colors.keys.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_accent_color))
            .setItems(names) { _, idx ->
                val hex = colors.values.toList()[idx]
                store.setAccentColor(hex)
                runCatching { b.vAccentPreview.setBackgroundColor(Color.parseColor(hex)) }
            }
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
