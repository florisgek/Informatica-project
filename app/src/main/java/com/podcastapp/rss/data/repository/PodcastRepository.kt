package com.podcastapp.rss.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.podcastapp.rss.data.model.Episode
import com.podcastapp.rss.data.model.Podcast
import com.podcastapp.rss.data.network.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Repository for managing podcast data
 * Handles fetching from RSS feeds and local storage
 */
class PodcastRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val rssParser = RssParser()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cachedPodcasts = mutableMapOf<String, Podcast>()

    companion object {
        private const val PREFS_NAME = "podcast_prefs"
        private const val KEY_FEEDS = "subscribed_feeds"
        private const val KEY_PLAYBACK_POSITIONS = "playback_positions"
        private const val KEY_PLAYED_EPISODES = "played_episodes"

        // Sample feeds for discovery
        val SAMPLE_FEEDS = listOf(
            "https://feeds.simplecast.com/54nAGcIl" to "The Daily",
            "https://feeds.npr.org/510289/podcast.xml" to "Planet Money",
            "https://feeds.megaphone.fm/sciencevs" to "Science Vs",
            "https://rss.art19.com/smartless" to "SmartLess",
            "https://feeds.simplecast.com/qm_9xx0g" to "Crime Junkie"
        )
    }

    /**
     * Get all subscribed feed URLs
     */
    fun getSubscribedFeeds(): List<String> {
        val feedsJson = prefs.getString(KEY_FEEDS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(feedsJson)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Subscribe to a new feed
     */
    fun subscribeFeed(feedUrl: String) {
        val feeds = getSubscribedFeeds().toMutableList()
        if (!feeds.contains(feedUrl)) {
            feeds.add(feedUrl)
            saveFeedsList(feeds)
        }
    }

    /**
     * Unsubscribe from a feed
     */
    fun unsubscribeFeed(feedUrl: String) {
        val feeds = getSubscribedFeeds().toMutableList()
        feeds.remove(feedUrl)
        saveFeedsList(feeds)
        cachedPodcasts.remove(feedUrl)
    }

    private fun saveFeedsList(feeds: List<String>) {
        val jsonArray = JSONArray(feeds)
        prefs.edit().putString(KEY_FEEDS, jsonArray.toString()).apply()
    }

    /**
     * Fetch podcast from RSS feed URL
     */
    suspend fun fetchPodcast(feedUrl: String, forceRefresh: Boolean = false): Result<Podcast> {
        return withContext(Dispatchers.IO) {
            try {
                // Return cached if available and not forcing refresh
                if (!forceRefresh && cachedPodcasts.containsKey(feedUrl)) {
                    return@withContext Result.success(cachedPodcasts[feedUrl]!!)
                }

                val request = Request.Builder()
                    .url(feedUrl)
                    .header("User-Agent", "PodcastRSS/1.0 Android")
                    .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch feed: ${response.code}")
                    )
                }

                val xmlContent = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))

                val podcast = rssParser.parse(feedUrl, xmlContent)

                // Apply saved playback states
                val podcastWithState = applyPlaybackStates(podcast)

                cachedPodcasts[feedUrl] = podcastWithState
                Result.success(podcastWithState)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Fetch all subscribed podcasts
     */
    suspend fun fetchAllPodcasts(forceRefresh: Boolean = false): List<Result<Podcast>> {
        return getSubscribedFeeds().map { feedUrl ->
            fetchPodcast(feedUrl, forceRefresh)
        }
    }

    /**
     * Get all episodes from all subscribed podcasts, sorted by date
     */
    suspend fun getAllEpisodes(forceRefresh: Boolean = false): List<Pair<Episode, Podcast>> {
        val allEpisodes = mutableListOf<Pair<Episode, Podcast>>()

        fetchAllPodcasts(forceRefresh).forEach { result ->
            result.getOrNull()?.let { podcast ->
                podcast.episodes.forEach { episode ->
                    allEpisodes.add(episode to podcast)
                }
            }
        }

        return allEpisodes.sortedByDescending { it.first.publishDate }
    }

    /**
     * Save playback position for an episode
     */
    fun savePlaybackPosition(episodeGuid: String, position: Long) {
        val positionsJson = prefs.getString(KEY_PLAYBACK_POSITIONS, "{}") ?: "{}"
        val positions = try {
            JSONObject(positionsJson)
        } catch (e: Exception) {
            JSONObject()
        }
        positions.put(episodeGuid, position)
        prefs.edit().putString(KEY_PLAYBACK_POSITIONS, positions.toString()).apply()
    }

    /**
     * Get saved playback position for an episode
     */
    fun getPlaybackPosition(episodeGuid: String): Long {
        val positionsJson = prefs.getString(KEY_PLAYBACK_POSITIONS, "{}") ?: "{}"
        return try {
            JSONObject(positionsJson).optLong(episodeGuid, 0)
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Mark episode as played
     */
    fun markAsPlayed(episodeGuid: String) {
        val playedJson = prefs.getString(KEY_PLAYED_EPISODES, "[]") ?: "[]"
        val played = try {
            val arr = JSONArray(playedJson)
            (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
        } catch (e: Exception) {
            mutableSetOf()
        }
        played.add(episodeGuid)
        prefs.edit().putString(KEY_PLAYED_EPISODES, JSONArray(played.toList()).toString()).apply()
    }

    /**
     * Check if episode is played
     */
    fun isPlayed(episodeGuid: String): Boolean {
        val playedJson = prefs.getString(KEY_PLAYED_EPISODES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(playedJson)
            (0 until arr.length()).any { arr.getString(it) == episodeGuid }
        } catch (e: Exception) {
            false
        }
    }

    private fun applyPlaybackStates(podcast: Podcast): Podcast {
        val updatedEpisodes = podcast.episodes.map { episode ->
            val guid = episode.guid ?: episode.audioUrl
            episode.copy(
                playbackPosition = getPlaybackPosition(guid),
                isPlayed = isPlayed(guid)
            )
        }
        return podcast.copy(episodes = updatedEpisodes)
    }

    /**
     * Search for podcasts (using iTunes Search API)
     */
    suspend fun searchPodcasts(query: String): List<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://itunes.apple.com/search?term=$encodedQuery&media=podcast&limit=20"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "PodcastRSS/1.0 Android")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()

                val json = JSONObject(body)
                val results = json.getJSONArray("results")

                (0 until results.length()).mapNotNull { i ->
                    val result = results.getJSONObject(i)
                    val feedUrl = result.optString("feedUrl", "")
                    val name = result.optString("collectionName", "")
                    if (feedUrl.isNotEmpty() && name.isNotEmpty()) {
                        feedUrl to name
                    } else null
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
