package com.podcastapp.rss

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PodcastApplication : Application() {

    companion object {
        const val PLAYBACK_CHANNEL_ID = "podcast_playback"
        lateinit var instance: PodcastApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Podcast Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows current playing podcast"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
