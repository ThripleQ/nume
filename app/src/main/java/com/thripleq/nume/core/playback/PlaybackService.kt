package com.thripleq.nume.core.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.thripleq.nume.MainActivity

/**
 * Keeps playback alive in the background and surfaces a system media
 * notification (cast/car friendly) via Media3's default notification provider.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        PlayerHolder.release()
        super.onDestroy()
    }
}