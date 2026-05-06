package com.ryou.appryous.util

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.ryou.appryous.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ── View ──────────────────────────────────────────────────────────────────────

fun View.show()      { visibility = View.VISIBLE }
fun View.hide()      { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun View.showIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

/** Apply window insets ke padding untuk edge-to-edge support */
fun View.applySystemBarPadding(
    applyTop:    Boolean = false,
    applyBottom: Boolean = true
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(
            view.paddingLeft,
            if (applyTop)    view.paddingTop    + bars.top    else view.paddingTop,
            view.paddingRight,
            if (applyBottom) view.paddingBottom + bars.bottom else view.paddingBottom
        )
        WindowInsetsCompat.CONSUMED
    }
}

/** Set margin dinamis */
fun View.setMargins(
    start:  Int? = null,
    top:    Int? = null,
    end:    Int? = null,
    bottom: Int? = null
) {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    start?.let  { lp.marginStart  = it }
    top?.let    { lp.topMargin    = it }
    end?.let    { lp.marginEnd    = it }
    bottom?.let { lp.bottomMargin = it }
    layoutParams = lp
}

// ── ImageView / Glide ─────────────────────────────────────────────────────────

/**
 * Load gambar dengan Glide + crossfade.
 * Placeholder dan error pakai drawable default.
 */
fun ImageView.loadImage(
    url:         String?,
    placeholder: Int = R.drawable.placeholder_poster,
    crossfade:   Boolean = true
) {
    if (url.isNullOrBlank()) {
        setImageResource(placeholder)
        return
    }
    Glide.with(context)
        .load(url)
        .placeholder(placeholder)
        .error(placeholder)
        .apply {
            if (crossfade) transition(DrawableTransitionOptions.withCrossFade())
        }
        .into(this)
}

fun ImageView.loadBanner(url: String?, placeholder: Int = R.drawable.placeholder_banner) =
    loadImage(url, placeholder)

// ── Context ───────────────────────────────────────────────────────────────────

fun Context.toast(msg: String, long: Boolean = false) =
    Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

fun Fragment.toast(msg: String, long: Boolean = false) =
    requireContext().toast(msg, long)

/** Get color dari attr theme */
@ColorInt
fun Context.themeColor(@AttrRes attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return tv.data
}

/** dp → px */
fun Context.dpToPx(dp: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

// ── Lifecycle / Coroutines ────────────────────────────────────────────────────

/**
 * Safe collect di Fragment — hanya jalan saat STARTED, auto-cancel saat DESTROYED.
 * Pengganti repeatOnLifecycle boilerplate.
 */
fun LifecycleOwner.launchWhenStarted(block: suspend CoroutineScope.() -> Unit) =
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }

// ── Time Formatting ───────────────────────────────────────────────────────────

/** Konversi detik ke format MM:SS atau H:MM:SS */
fun Long.toTimeString(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    val s = this % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}

/** Konversi ms ke format MM:SS atau H:MM:SS */
fun Long.msToTimeString(): String = (this / 1000).toTimeString()

// ── String ────────────────────────────────────────────────────────────────────

/** Singkat teks jika lebih dari maxChars */
fun String.ellipsize(maxChars: Int): String =
    if (length > maxChars) substring(0, maxChars - 1) + "…" else this

/** Capitalize first letter */
fun String.capitalize(): String =
    if (isEmpty()) this else this[0].uppercaseChar() + substring(1)

// ── Number ────────────────────────────────────────────────────────────────────

/** Format rating dengan 1 desimal, atau "—" jika 0 */
fun Double.formatRating(): String = if (this > 0) "%.1f".format(this) else "—"
