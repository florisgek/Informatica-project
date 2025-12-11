package com.podcastapp.rss.data.model

import java.io.Serializable

/**
 * Represents a podcast/feed subscription
 */
data class Podcast(
    val id: Long = 0,
    val title: String,
    val description: String,
    val feedUrl: String,
    val imageUrl: String? = null,
    val author: String? = null,
    val link: String? = null,
    val language: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val episodes: List<Episode> = emptyList()
) : Serializable

/**
 * Represents a podcast episode
 */
data class Episode(
    val id: Long = 0,
    val podcastId: Long = 0,
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String? = null,
    val duration: Long = 0,
    val durationText: String? = null,
    val publishDate: Long = 0,
    val publishDateText: String? = null,
    val fileSize: Long = 0,
    val isPlayed: Boolean = false,
    val playbackPosition: Long = 0,
    val guid: String? = null
) : Serializable {

    fun getFormattedDuration(): String {
        if (durationText != null) return durationText
        if (duration <= 0) return ""

        val hours = duration / 3600
        val minutes = (duration % 3600) / 60
        val seconds = duration % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    fun getFormattedFileSize(): String {
        if (fileSize <= 0) return ""

        val kb = fileSize / 1024.0
        val mb = kb / 1024.0

        return if (mb >= 1) {
            String.format("%.1f MB", mb)
        } else {
            String.format("%.0f KB", kb)
        }
    }
}

/**
 * Represents the current playback state
 */
data class PlaybackState(
    val episode: Episode? = null,
    val podcast: Podcast? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val playbackSpeed: Float = 1.0f
) {
    val progress: Float
        get() = if (duration > 0) currentPosition.toFloat() / duration else 0f
}
