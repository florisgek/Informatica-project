package com.podcastapp.rss.ui.main

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.podcastapp.rss.data.repository.PodcastRepository
import com.podcastapp.rss.databinding.ActivityAddFeedBinding
import kotlinx.coroutines.launch

class AddFeedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddFeedBinding
    private lateinit var repository: PodcastRepository
    private lateinit var searchAdapter: SearchResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Add Podcast"

        repository = PodcastRepository(this)

        setupSearch()
        setupSuggestions()
    }

    private fun setupSearch() {
        searchAdapter = SearchResultAdapter { feedUrl, name ->
            subscribeToPodcast(feedUrl, name)
        }

        binding.recyclerResults.apply {
            layoutManager = LinearLayoutManager(this@AddFeedActivity)
            adapter = searchAdapter
        }

        binding.btnSearch.setOnClickListener {
            performSearch()
        }

        binding.etFeedUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        binding.btnAddDirect.setOnClickListener {
            val url = binding.etFeedUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                addDirectFeed(url)
            }
        }
    }

    private fun setupSuggestions() {
        val suggestionsAdapter = SearchResultAdapter { feedUrl, name ->
            subscribeToPodcast(feedUrl, name)
        }

        binding.recyclerSuggestions.apply {
            layoutManager = LinearLayoutManager(this@AddFeedActivity)
            adapter = suggestionsAdapter
        }

        suggestionsAdapter.submitList(PodcastRepository.SAMPLE_FEEDS)
    }

    private fun performSearch() {
        val query = binding.etFeedUrl.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter a search term or URL", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if it's a URL
        if (query.startsWith("http://") || query.startsWith("https://")) {
            addDirectFeed(query)
            return
        }

        // Search for podcasts
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerResults.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val results = repository.searchPodcasts(query)
                searchAdapter.submitList(results)

                binding.recyclerResults.visibility = View.VISIBLE

                if (results.isEmpty()) {
                    Toast.makeText(this@AddFeedActivity, "No podcasts found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddFeedActivity, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun addDirectFeed(url: String) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = repository.fetchPodcast(url)
                result.fold(
                    onSuccess = { podcast ->
                        repository.subscribeFeed(url)
                        Toast.makeText(this@AddFeedActivity, "Subscribed to ${podcast.title}", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@AddFeedActivity, "Invalid feed: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@AddFeedActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun subscribeToPodcast(feedUrl: String, name: String) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = repository.fetchPodcast(feedUrl)
                result.fold(
                    onSuccess = { podcast ->
                        repository.subscribeFeed(feedUrl)
                        Toast.makeText(this@AddFeedActivity, "Subscribed to ${podcast.title}", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@AddFeedActivity, "Failed to subscribe: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@AddFeedActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

// Search result adapter
class SearchResultAdapter(
    private val onItemClick: (String, String) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Pair<String, String>, SearchResultAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<Pair<String, String>>() {
        override fun areItemsTheSame(oldItem: Pair<String, String>, newItem: Pair<String, String>) = oldItem.first == newItem.first
        override fun areContentsTheSame(oldItem: Pair<String, String>, newItem: Pair<String, String>) = oldItem == newItem
    }
) {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (url, name) = getItem(position)
        holder.bind(name, url)
    }

    inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        private val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)

        fun bind(name: String, url: String) {
            textView.text = name
            itemView.setOnClickListener { onItemClick(url, name) }
        }
    }
}
