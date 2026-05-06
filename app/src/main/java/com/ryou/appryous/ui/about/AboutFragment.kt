package com.ryou.appryous.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.ryou.appryous.BuildConfig
import com.ryou.appryous.R
import com.ryou.appryous.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {
    private var _b: FragmentAboutBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAboutBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        b.tvVersion.text = getString(
            R.string.about_version,
            BuildConfig.APP_VERSION,
            BuildConfig.APP_CODENAME
        )

        // Tech stack links
        b.btnMpv.setOnClickListener       { openUrl("https://mpv.io") }
        b.btnLibass.setOnClickListener    { openUrl("https://github.com/libass/libass") }
        b.btnRetrofit.setOnClickListener  { openUrl("https://square.github.io/retrofit") }
        b.btnMoshi.setOnClickListener     { openUrl("https://github.com/square/moshi") }
        b.btnGlide.setOnClickListener     { openUrl("https://github.com/bumptech/glide") }
        b.btnMedia3.setOnClickListener    { openUrl("https://developer.android.com/media/media3") }

        // Data sources
        b.btnMal.setOnClickListener       { openUrl("https://myanimelist.net") }
        b.btnTmdb.setOnClickListener      { openUrl("https://www.themoviedb.org") }
        b.btnMdl.setOnClickListener       { openUrl("https://mydramalist.com") }

        // Source
        b.btnGithub.setOnClickListener    { openUrl("https://github.com/ryoustream/appryous") }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
