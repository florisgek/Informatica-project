package com.podcastapp.rss.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.podcastapp.rss.data.model.Episode
import com.podcastapp.rss.data.model.Podcast
import com.podcastapp.rss.data.repository.PodcastRepository
import com.podcastapp.rss.ui.main.MainActivity

class PodcastPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private lateinit var repository: PodcastRepository

    private var currentEpisode: Episode? = null
    private var currentPodcast: Podcast? = null

    override fun onCreate() {
        super.onCreate()
        repository = PodcastRepository(this)
        initializePlayer()
        initializeMediaSession()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10000) // 10 seconds
            .setSeekForwardIncrementMs(30000) // 30 seconds
            .build()

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    currentEpisode?.let { episode ->
                        repository.markAsPlayed(episode.guid ?: episode.audioUrl)
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                saveCurrentPosition()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    saveCurrentPosition()
                }
            }
        })
    }

    private fun initializeMediaSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        intent?.let {
            val episode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getSerializableExtra("episode", Episode::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getSerializableExtra("episode") as? Episode
            }

            val podcast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getSerializableExtra("podcast", Podcast::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getSerializableExtra("podcast") as? Podcast
            }

            if (episode != null && podcast != null) {
                playEpisode(episode, podcast)
            }
        }

        return START_STICKY
    }

    private fun playEpisode(episode: Episode, podcast: Podcast) {
        // Save position of previous episode
        saveCurrentPosition()

        currentEpisode = episode
        currentPodcast = podcast

        val imageUrl = episode.imageUrl ?: podcast.imageUrl

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(podcast.title)
            .setAlbumTitle(podcast.title)
            .setDescription(episode.description)
            .apply {
                if (!imageUrl.isNullOrEmpty()) {
                    setArtworkUri(android.net.Uri.parse(imageUrl))
                }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(episode.audioUrl)
            .setMediaMetadata(mediaMetadata)
            .build()

        player?.apply {
            setMediaItem(mediaItem)
            prepare()

            // Resume from saved position if available
            val savedPosition = repository.getPlaybackPosition(episode.guid ?: episode.audioUrl)
            if (savedPosition > 0) {
                seekTo(savedPosition)
            }

            play()
        }
    }

    private fun saveCurrentPosition() {
        val episode = currentEpisode ?: return
        val position = player?.currentPosition ?: return

        if (position > 0) {
            repository.savePlaybackPosition(episode.guid ?: episode.audioUrl, position)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        saveCurrentPosition()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveCurrentPosition()
        val player = mediaSession?.player
        if (player?.playWhenReady == true) {
            // Continue playing in background
        } else {
            stopSelf()
        }
    }
}
