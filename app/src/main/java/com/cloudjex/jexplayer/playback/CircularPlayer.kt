package com.cloudjex.jexplayer.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class CircularPlayer(player: Player) : ForwardingPlayer(player) {

    override fun seekToNext() {
        if (hasNextMediaItem()) super.seekToNextMediaItem() else wrap(forward = true)
    }

    override fun seekToNextMediaItem() {
        if (hasNextMediaItem()) super.seekToNextMediaItem() else wrap(forward = true)
    }

    override fun seekToPrevious() {

        if (hasPreviousMediaItem()) super.seekToPreviousMediaItem() else wrap(forward = false)
    }

    override fun seekToPreviousMediaItem() {
        if (hasPreviousMediaItem()) super.seekToPreviousMediaItem() else wrap(forward = false)
    }

    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands()
        if (mediaItemCount <= 1) return commands

        return Player.Commands.Builder()
            .addAll(commands)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()
    }

    private fun wrap(forward: Boolean) {
        val target = CircularQueue.wrapTarget(mediaItemCount, forward)
        if (target != CircularQueue.NONE) {
            seekTo(target, /* positionMs= */ 0L)
        }
    }
}
internal object CircularQueue {
    const val NONE = -1

    fun wrapTarget(itemCount: Int, forward: Boolean): Int = when {
        itemCount <= 0 -> NONE
        forward -> 0
        else -> itemCount - 1
    }
}
