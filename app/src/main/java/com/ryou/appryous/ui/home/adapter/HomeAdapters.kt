package com.ryou.appryous.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ryou.appryous.data.model.AnimeEntry
import com.ryou.appryous.databinding.ItemHeroBinding
import com.ryou.appryous.databinding.ItemPosterCardBinding
import com.ryou.appryous.util.loadBanner
import com.ryou.appryous.util.loadImage
import com.ryou.appryous.util.showIf

// ── Hero Carousel Adapter ─────────────────────────────────────────────────────

class HeroAdapter(
    private val onClick: (AnimeEntry) -> Unit
) : ListAdapter<AnimeEntry, HeroAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemHeroBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: AnimeEntry) {
            b.ivBanner.loadBanner(item.banner)
            b.tvTitle.text  = item.title
            b.tvType.text   = item.type
            b.tvYear.text   = item.year
            b.tvRating.text = item.ratingFormatted
            b.tvRating.showIf(item.hasRating)
            b.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int) =
        VH(ItemHeroBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AnimeEntry>() {
            override fun areItemsTheSame(a: AnimeEntry, b: AnimeEntry) = a.id == b.id
            override fun areContentsTheSame(a: AnimeEntry, b: AnimeEntry) = a == b
        }
    }
}

// ── Poster Card Adapter (horizontal rows) ─────────────────────────────────────

class PosterCardAdapter(
    private val onClick: (AnimeEntry) -> Unit
) : ListAdapter<AnimeEntry, PosterCardAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemPosterCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: AnimeEntry) {
            b.ivPoster.loadImage(item.poster)
            b.tvTitle.text = item.title
            b.tvMeta.text  = listOfNotNull(
                item.type.ifBlank { null },
                item.year.ifBlank { null }
            ).joinToString(" · ")
            b.tvRating.text   = item.ratingFormatted
            b.tvRating.showIf(item.hasRating)
            b.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int) =
        VH(ItemPosterCardBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AnimeEntry>() {
            override fun areItemsTheSame(a: AnimeEntry, b: AnimeEntry) = a.id == b.id
            override fun areContentsTheSame(a: AnimeEntry, b: AnimeEntry) = a == b
        }
    }
}
