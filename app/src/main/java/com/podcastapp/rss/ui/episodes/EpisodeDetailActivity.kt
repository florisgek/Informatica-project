package com.podcastapp.rss.ui.episodes

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bumptech.glide.Glide
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.podcastapp.rss.R
import com.podcastapp.rss.data.model.Episode
import com.podcastapp.rss.data.model.Podcast
import com.podcastapp.rss.data.repository.PodcastRepository
import com.podcastapp.rss.databinding.ActivityEpisodeDetailBinding
import com.podcastapp.rss.service.PodcastPlaybackService

class EpisodeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEpisodeDetailBinding
    private lateinit var repository: PodcastRepository

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var episode: Episode? = null
    private var podcast: Podcast? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpisodeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Now Playing"

        repository = PodcastRepository(this)

        // Get episode and podcast from intent
        episode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("episode", Episode::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("episode") as? Episode
        }

        podcast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("podcast", Podcast::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("podcast") as? Podcast
        }

        setupUI()
        setupControls()
    }

    override fun onStart() {
        super.onStart()
        initializeMediaController()
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateProgressRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateProgressRunnable)
    }

    override fun onStop() {
        super.onStop()
        releaseMediaController()
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, PodcastPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            setupPlayerListener()
            updatePlayerUI()
        }, MoreExecutors.directExecutor())
    }

    private fun releaseMediaController() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseButton(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlayerUI()
            }
        })
    }

    private fun setupUI() {
        val ep = episode ?: return
        val pod = podcast ?: return

        binding.apply {
            tvTitle.text = ep.title
            tvPodcast.text = pod.title
            tvDescription.text = ep.description
            tvDate.text = ep.publishDateText ?: ""

            // Load artwork
            val imageUrl = ep.imageUrl ?: pod.imageUrl
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(this@EpisodeDetailActivity)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_podcast_placeholder)
                    .error(R.drawable.ic_podcast_placeholder)
                    .into(ivArtwork)
            }
        }
    }

    private fun setupControls() {
        binding.apply {
            btnPlayPause.setOnClickListener {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        controller.pause()
                    } else {
                        // If not currently playing this episode, start it
                        if (controller.mediaItemCount == 0) {
                            startPlayback()
                        } else {
                            controller.play()
                        }
                    }
                } ?: startPlayback()
            }

            btnSkipBack.setOnClickListener {
                mediaController?.seekBack()
            }

            btnSkipForward.setOnClickListener {
                mediaController?.seekForward()
            }

            btnRewind.setOnClickListener {
                mediaController?.let { controller ->
                    val newPosition = (controller.currentPosition - 10000).coerceAtLeast(0)
                    controller.seekTo(newPosition)
                }
            }

            btnFastForward.setOnClickListener {
                mediaController?.let { controller ->
                    val duration = controller.duration
                    if (duration > 0) {
                        val newPosition = (controller.currentPosition + 30000).coerceAtMost(duration)
                        controller.seekTo(newPosition)
                    }
                }
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val duration = mediaController?.duration ?: 0
                        val position = (progress.toLong() * duration) / 100
                        binding.tvCurrentTime.text = formatTime(position)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.let {
                        val duration = mediaController?.duration ?: 0
                        val position = (it.progress.toLong() * duration) / 100
                        mediaController?.seekTo(position)
                    }
                }
            })

            // Speed control
            btnSpeed.setOnClickListener {
                showSpeedSelector()
            }
        }
    }

    private fun startPlayback() {
        val ep = episode ?: return
        val pod = podcast ?: return

        val intent = Intent(this, PodcastPlaybackService::class.java).apply {
            putExtra("episode", ep)
            putExtra("podcast", pod)
        }
        startService(intent)
    }

    private fun updatePlayerUI() {
        val controller = mediaController ?: return
        updatePlayPauseButton(controller.isPlaying)
        updateProgress()
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause_large else R.drawable.ic_play_large
        )
    }

    private fun updateProgress() {
        val controller = mediaController ?: return

        val duration = controller.duration
        val position = controller.currentPosition

        if (duration > 0) {
            val progress = ((position.toFloat() / duration) * 100).toInt()
            binding.seekBar.progress = progress
            binding.tvCurrentTime.text = formatTime(position)
            binding.tvDuration.text = formatTime(duration)
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun showSpeedSelector() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setItems(speeds) { _, which ->
                mediaController?.setPlaybackSpeed(speedValues[which])
                binding.btnSpeed.text = speeds[which]
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
