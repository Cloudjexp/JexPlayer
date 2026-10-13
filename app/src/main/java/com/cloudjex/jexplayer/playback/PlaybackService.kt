package com.cloudjex.jexplayer.playback

import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService


@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, CircularPlayer(player))
            .setMediaButtonPreferences(
                listOf(
                    CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                        .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
                        .setDisplayName("Aleatorio")
                        .build()
                )
            )
            .apply {
                packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                    setSessionActivity(
                        PendingIntent.getActivity(
                            this@PlaybackService,
                            0,
                            launchIntent,
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
