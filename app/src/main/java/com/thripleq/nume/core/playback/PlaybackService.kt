package com.thripleq.nume.core.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.thripleq.nume.MainActivity

/**
 * Keeps playback alive in the background and surfaces a system media
 * notification (cast/car friendly) via Media3's default notification provider.
 *
 * Media3's service defers its own startForeground() until the player reaches a
 * state it wants to post, which can blow Android's 5s startForegroundService()
 * window on slow loads. We post a lightweight foreground notification in
 * onCreate immediately so the timeout never bites, and keep it until the player
 * reports READY — at that point Media3 has taken over the foreground with its
 * own notification, so we only drop the placeholder. We never stopForeground(),
 * otherwise the service falls back to background while onStartCommand() won't
 * re-post a foreground notification and the next startForegroundService() call
 * crashes after 5s (ForegroundServiceDidNotStartInTimeException).
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null

    override fun onCreate() {
        super.onCreate()
        // Post an immediate lightweight foreground notification so Android's 5s
        // startForegroundService() window never blows on slow loads. It stays
        // until the player is READY, at which point Media3's own notification
        // provider owns the foreground and we can drop it.
        startForeground(
            PLACEHOLDER_NOTIF_ID,
            buildFirstForegroundNotice(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        val player = PlayerHolder.get(this)
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (player.playbackState == Player.STATE_READY) {
                    dropPlaceholder(player)
                }
            }
        }
        playerListener = listener
        player.addListener(listener)
        // Rebuild path (service re-created while already playing): player is
        // already READY, no new event will arrive, so drop the placeholder now.
        if (player.playbackState == Player.STATE_READY) {
            dropPlaceholder(player)
        }
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
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(activityIntent)
            .build()
    }

    private fun dropPlaceholder(player: Player) {
        getSystemService(NotificationManager::class.java)?.cancel(PLACEHOLDER_NOTIF_ID)
        playerListener?.let { player.removeListener(it) }
        playerListener = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        playerListener?.let { PlayerHolder.get(this).removeListener(it) }
        playerListener = null
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