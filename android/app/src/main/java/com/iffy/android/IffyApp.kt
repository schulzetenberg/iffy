package com.iffy.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class IffyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            SpotifyService.CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
