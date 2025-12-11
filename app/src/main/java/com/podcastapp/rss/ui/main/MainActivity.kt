package com.podcastapp.rss.ui.main

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.podcastapp.rss.R
import com.podcastapp.rss.data.model.Episode
import com.podcastapp.rss.data.model.Podcast
import com.podcastapp.rss.data.repository.PodcastRepository
import com.podcastapp.rss.databinding.ActivityMainBinding
import com.podcastapp.rss.service.PodcastPlaybackService
import com.podcastapp.rss.ui.episodes.EpisodeDetailActivity
import com.podcastapp.rss.ui.settings.SettingsActivity
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: PodcastRepository
    private lateinit var podcastAdapter: PodcastAdapter
    private lateinit var episodeAdapter: EpisodeAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var currentEpisode: Episode? = null
    private var currentPodcast: Podcast? = null
    private var podcasts: List<Podcast> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        repository = PodcastRepository(this)

        setupRecyclerViews()
        setupTabs()
        setupBottomPlayer()
        setupSwipeRefresh()
        setupFab()

        loadContent()
    }

    override fun onStart() {
        super.onStart()
        initializeMediaController()
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

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updatePlayerUI()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlayerUI()
            }
        })
    }

    private fun setupRecyclerViews() {
        // Podcasts adapter
        podcastAdapter = PodcastAdapter(
            onPodcastClick = { podcast ->
                showPodcastEpisodes(podcast)
            },
            onPodcastLongClick = { podcast ->
                showPodcastOptions(podcast)
            }
        )

        // Episodes adapter
        episodeAdapter = EpisodeAdapter(
            onEpisodeClick = { episode, podcast ->
                playEpisode(episode, podcast)
            },
            onEpisodeLongClick = { episode, podcast ->
                showEpisodeDetails(episode, podcast)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = podcastAdapter
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showPodcasts()
                    1 -> showAllEpisodes()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupBottomPlayer() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomPlayer.root)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        binding.bottomPlayer.apply {
            btnPlayPause.setOnClickListener {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        controller.pause()
                    } else {
                        controller.play()
                    }
                }
            }

            btnSkipBack.setOnClickListener {
                mediaController?.seekBack()
            }

            btnSkipForward.setOnClickListener {
                mediaController?.seekForward()
            }

            root.setOnClickListener {
                currentEpisode?.let { episode ->
                    currentPodcast?.let { podcast ->
                        showEpisodeDetails(episode, podcast)
                    }
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            loadContent(forceRefresh = true)
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddFeedActivity::class.java))
        }
    }

    private fun loadContent(forceRefresh: Boolean = false) {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val results = repository.fetchAllPodcasts(forceRefresh)
                podcasts = results.mapNotNull { it.getOrNull() }

                if (podcasts.isEmpty()) {
                    showEmptyState()
                } else {
                    binding.emptyView.visibility = View.GONE
                    when (binding.tabLayout.selectedTabPosition) {
                        0 -> showPodcasts()
                        1 -> showAllEpisodes()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading podcasts: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showEmptyState() {
        binding.emptyView.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun showPodcasts() {
        binding.recyclerView.adapter = podcastAdapter
        binding.recyclerView.visibility = View.VISIBLE
        podcastAdapter.submitList(podcasts)

        if (podcasts.isEmpty()) {
            showEmptyState()
        } else {
            binding.emptyView.visibility = View.GONE
        }
    }

    private fun showAllEpisodes() {
        binding.recyclerView.adapter = episodeAdapter
        binding.recyclerView.visibility = View.VISIBLE

        val allEpisodes = podcasts.flatMap { podcast ->
            podcast.episodes.map { it to podcast }
        }.sortedByDescending { it.first.publishDate }

        episodeAdapter.submitList(allEpisodes)

        if (allEpisodes.isEmpty()) {
            showEmptyState()
        } else {
            binding.emptyView.visibility = View.GONE
        }
    }

    private fun showPodcastEpisodes(podcast: Podcast) {
        binding.recyclerView.adapter = episodeAdapter
        episodeAdapter.submitList(podcast.episodes.map { it to podcast })

        // Update toolbar title
        supportActionBar?.title = podcast.title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun showPodcastOptions(podcast: Podcast) {
        val options = arrayOf("View Episodes", "Unsubscribe", "Open Website")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(podcast.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPodcastEpisodes(podcast)
                    1 -> {
                        repository.unsubscribeFeed(podcast.feedUrl)
                        loadContent()
                        Toast.makeText(this, "Unsubscribed", Toast.LENGTH_SHORT).show()
                    }
                    2 -> podcast.link?.let { openUrl(it) }
                }
            }
            .show()
    }

    private fun playEpisode(episode: Episode, podcast: Podcast) {
        currentEpisode = episode
        currentPodcast = podcast

        // Start playback service
        val intent = Intent(this, PodcastPlaybackService::class.java).apply {
            putExtra("episode", episode)
            putExtra("podcast", podcast)
        }
        startService(intent)

        // Show bottom player
        showBottomPlayer(episode, podcast)
    }

    private fun showBottomPlayer(episode: Episode, podcast: Podcast) {
        binding.bottomPlayer.apply {
            tvTitle.text = episode.title
            tvPodcast.text = podcast.title

            val imageUrl = episode.imageUrl ?: podcast.imageUrl
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(this@MainActivity)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_podcast_placeholder)
                    .error(R.drawable.ic_podcast_placeholder)
                    .into(ivArtwork)
            }
        }

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun updatePlayerUI() {
        val controller = mediaController ?: return
        updatePlayPauseButton(controller.isPlaying)
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.bottomPlayer.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun showEpisodeDetails(episode: Episode, podcast: Podcast) {
        val intent = Intent(this, EpisodeDetailActivity::class.java).apply {
            putExtra("episode", episode)
            putExtra("podcast", podcast)
        }
        startActivity(intent)
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open URL", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                supportActionBar?.title = getString(R.string.app_name)
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
                showPodcasts()
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                loadContent(forceRefresh = true)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        loadContent()
    }
}
