package com.iflytek.cyber.evs.sdk.agent.impl

import android.content.Context
import android.util.Log
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.iflytek.cyber.evs.sdk.agent.VideoPlayer

class VideoPlayerImpl(context: Context, playerView: PlayerView) : VideoPlayer() {
    companion object {
        private const val TAG = "VideoPlayerImpl"
    }

    private var player: VideoPlayerInstance? = null

    init {
        initPlayer(context, playerView)
    }

    private val listener = object : VideoPlayerInstance.Listener {
        private var playWhenReady = false

        override fun onPlayerStateChanged(player: VideoPlayerInstance, playWhenReady: Boolean, playbackState: Int) {
            Log.d(TAG, "onPlayerStateChanged($playWhenReady, $playbackState)")
            val isPlayingChanged = this.playWhenReady == playWhenReady
            this.playWhenReady = playWhenReady

            when (playbackState) {
                Player.STATE_ENDED -> {
                    if (playWhenReady)
                        onCompleted(player.resourceId ?: "")
                }
                Player.STATE_BUFFERING -> {
                    // ignore
                }
                Player.STATE_IDLE -> {
                    if (!playWhenReady) {
                        onStopped(player.resourceId ?: "")
                    }
                }
                Player.STATE_READY -> {
                    if (isPlayingChanged) {
                        if (playWhenReady) {
                            onResumed(player.resourceId ?: "")
                        } else {
                            onPaused(player.resourceId ?: "")
                        }
                    }
                }
            }
        }

        override fun onPlayerPositionUpdated(player: VideoPlayerInstance, position: Long) {
            onPositionUpdated(player.resourceId ?: "", position)
        }

        override fun onPlayerError(player: VideoPlayerInstance, error: ExoPlaybackException?) {
            val errorCode: String = when (error?.type) {
                ExoPlaybackException.TYPE_UNEXPECTED -> {
                    MEDIA_ERROR_UNKNOWN
                }
                ExoPlaybackException.TYPE_SOURCE -> {
                    MEDIA_ERROR_INVALID_REQUEST
                }
                ExoPlaybackException.TYPE_REMOTE -> {
                    MEDIA_ERROR_SERVICE_UNAVAILABLE
                }
                ExoPlaybackException.TYPE_RENDERER -> {
                    MEDIA_ERROR_INTERNAL_SERVER_ERROR
                }
                ExoPlaybackException.TYPE_OUT_OF_MEMORY -> {
                    MEDIA_ERROR_INTERNAL_DEVICE_ERROR
                }
                else -> {
                    MEDIA_ERROR_UNKNOWN
                }
            }
            onError(player.resourceId ?: "", errorCode)
        }
    }

    private fun initPlayer(context: Context, playerView: PlayerView) {
        player?.destroy()
        player = VideoPlayerInstance(context, playerView)
        player?.setListener(listener)
    }

    private fun getPlayer(): VideoPlayerInstance? {
        return player
    }

    override fun play(resourceId: String, url: String): Boolean {
        Log.d(TAG, "try to play $url on video player")
        val player = getPlayer()
        player?.let {
            it.resourceId = resourceId
            it.play(url)
            onStarted(player.resourceId ?: "")
            return true
        } ?: run {
            return false
        }
    }

    override fun resume(): Boolean {
        val player = getPlayer()
        player?.resume() ?: run {
            return false
        }
        return true
    }

    override fun pause(): Boolean {
        val player = getPlayer()
        player?.pause() ?: run {
            return false
        }
        return true
    }

    override fun stop(): Boolean {
        val player = getPlayer()
        player?.stop() ?: run {
            return false
        }
        return true
    }

    override fun seekTo(offset: Long): Boolean {
        val player = getPlayer()
        player?.let {
            it.seekTo(offset)
            return true
        } ?: run {
            return false
        }
    }

    override fun getOffset(): Long {
        return getPlayer()?.getOffset() ?: 0
    }

    override fun getDuration(): Long {
        return getPlayer()?.getDuration() ?: 0
    }
}