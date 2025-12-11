package com.podcastapp.rss.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.podcastapp.rss.R
import com.podcastapp.rss.data.model.Episode
import com.podcastapp.rss.data.model.Podcast
import com.podcastapp.rss.databinding.ItemEpisodeBinding

class EpisodeAdapter(
    private val onEpisodeClick: (Episode, Podcast) -> Unit,
    private val onEpisodeLongClick: (Episode, Podcast) -> Unit
) : ListAdapter<Pair<Episode, Podcast>, EpisodeAdapter.EpisodeViewHolder>(EpisodeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = ItemEpisodeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EpisodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val (episode, podcast) = getItem(position)
        holder.bind(episode, podcast)
    }

    inner class EpisodeViewHolder(
        private val binding: ItemEpisodeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(episode: Episode, podcast: Podcast) {
            binding.apply {
                tvTitle.text = episode.title
                tvPodcast.text = podcast.title
                tvDate.text = episode.publishDateText ?: ""

                // Duration info
                val durationText = episode.getFormattedDuration()
                if (durationText.isNotEmpty()) {
                    tvDuration.text = durationText
                    tvDuration.visibility = View.VISIBLE
                } else {
                    tvDuration.visibility = View.GONE
                }

                // Show played indicator
                if (episode.isPlayed) {
                    tvTitle.alpha = 0.6f
                    ivPlayed.visibility = View.VISIBLE
                } else {
                    tvTitle.alpha = 1.0f
                    ivPlayed.visibility = View.GONE
                }

                // Show progress if partially played
                if (episode.playbackPosition > 0 && !episode.isPlayed) {
                    progressBar.visibility = View.VISIBLE
                    val progress = if (episode.duration > 0) {
                        ((episode.playbackPosition.toFloat() / episode.duration) * 100).toInt()
                    } else 0
                    progressBar.progress = progress
                } else {
                    progressBar.visibility = View.GONE
                }

                // Load episode/podcast artwork
                val imageUrl = episode.imageUrl ?: podcast.imageUrl
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(ivArtwork.context)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_podcast_placeholder)
                        .error(R.drawable.ic_podcast_placeholder)
                        .centerCrop()
                        .into(ivArtwork)
                } else {
                    ivArtwork.setImageResource(R.drawable.ic_podcast_placeholder)
                }

                root.setOnClickListener { onEpisodeClick(episode, podcast) }
                root.setOnLongClickListener {
                    onEpisodeLongClick(episode, podcast)
                    true
                }
            }
        }
    }

    class EpisodeDiffCallback : DiffUtil.ItemCallback<Pair<Episode, Podcast>>() {
        override fun areItemsTheSame(
            oldItem: Pair<Episode, Podcast>,
            newItem: Pair<Episode, Podcast>
        ): Boolean {
            return oldItem.first.guid == newItem.first.guid
        }

        override fun areContentsTheSame(
            oldItem: Pair<Episode, Podcast>,
            newItem: Pair<Episode, Podcast>
        ): Boolean {
            return oldItem == newItem
        }
    }
}
