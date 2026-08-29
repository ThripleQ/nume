package com.thripleq.nume.core.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.thripleq.nume.MainActivity

/**
 * Keeps playback alive in the background and surfaces a system media
 * notification (cast/car friendly) via Media3's default notification provider.
 *
 * Media3's service defers its own startForeground() until the player reaches a
 * state it wants to post, which can blow Android's 5s startForegroundService()
 * window on slow loads and crash. We post a lightweight foreground notification
 * in onCreate immediately so the timeout never bites; Media3's provider replaces
 * it once real playback metadata arrives.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Post an immediate lightweight foreground notification so Android's 5s
        // startForegroundService() window never blows on slow loads. Media3's own
        // notification provider later re-posts under its own id; once the session
        // is built we cancel this placeholder so it does not linger as a stale
        // "准备播放…" item.
        startForeground(
            PLACEHOLDER_NOTIF_ID,
            buildFirstForegroundNotice(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        val launchIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.takeIf { it.action == Intent.ACTION_MAIN }
            ?: Intent(this, MainActivity::class.java)
        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, PlayerHolder.get(this))
            .setSessionActivity(activityIntent)
            .build()

        // STOP_FOREGROUND_DETACH drops the foreground binding but does NOT remove
        // the placeholder notification — it would linger as a stale "准备播放…"
        // item. Cancel it explicitly now that Media3 owns the notification and will
        // repost under its own id on the first player-state change.
        getSystemService(NotificationManager::class.java)?.cancel(PLACEHOLDER_NOTIF_ID)
        stopForeground(Service.STOP_FOREGROUND_DETACH)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        // Do NOT release the player here. PlayerHolder is a process-scoped
        // singleton the UI retains; the service stopping (e.g. user dismisses the
        // media notification) must not tear down an instance still in use.
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Same reasoning: keep playback/player alive; playback continues until the
        // user pauses or the OS reclaims the process.
        super.onTaskRemoved(rootIntent)
    }

    private fun buildFirstForegroundNotice(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("nume")
            .setContentText("准备播放…")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val PLACEHOLDER_NOTIF_ID = 11
    }
}