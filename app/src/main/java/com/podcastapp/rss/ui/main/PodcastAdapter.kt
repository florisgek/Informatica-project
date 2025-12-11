package com.podcastapp.rss.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.podcastapp.rss.R
import com.podcastapp.rss.data.model.Podcast
import com.podcastapp.rss.databinding.ItemPodcastBinding

class PodcastAdapter(
    private val onPodcastClick: (Podcast) -> Unit,
    private val onPodcastLongClick: (Podcast) -> Unit
) : ListAdapter<Podcast, PodcastAdapter.PodcastViewHolder>(PodcastDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PodcastViewHolder {
        val binding = ItemPodcastBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PodcastViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PodcastViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PodcastViewHolder(
        private val binding: ItemPodcastBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(podcast: Podcast) {
            binding.apply {
                tvTitle.text = podcast.title
                tvAuthor.text = podcast.author ?: "Unknown Author"
                tvEpisodeCount.text = "${podcast.episodes.size} episodes"

                // Load podcast artwork
                if (!podcast.imageUrl.isNullOrEmpty()) {
                    Glide.with(ivArtwork.context)
                        .load(podcast.imageUrl)
                        .placeholder(R.drawable.ic_podcast_placeholder)
                        .error(R.drawable.ic_podcast_placeholder)
                        .centerCrop()
                        .into(ivArtwork)
                } else {
                    ivArtwork.setImageResource(R.drawable.ic_podcast_placeholder)
                }

                root.setOnClickListener { onPodcastClick(podcast) }
                root.setOnLongClickListener {
                    onPodcastLongClick(podcast)
                    true
                }
            }
        }
    }

    class PodcastDiffCallback : DiffUtil.ItemCallback<Podcast>() {
        override fun areItemsTheSame(oldItem: Podcast, newItem: Podcast): Boolean {
            return oldItem.feedUrl == newItem.feedUrl
        }

        override fun areContentsTheSame(oldItem: Podcast, newItem: Podcast): Boolean {
            return oldItem == newItem
        }
    }
}
