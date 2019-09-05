package com.iflytek.cyber.evs.sdk.agent.impl

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.iflytek.cyber.embeddedclient.player.MediaSourceFactory
import com.iflytek.cyber.evs.sdk.agent.Alarm

class AlarmPlayerInstance(context: Context) {

    private val player = ExoPlayerFactory.newSimpleInstance(
        context,
        DefaultRenderersFactory(context),
        DefaultTrackSelector(),
        DefaultLoadControl()
    )
    private val mMediaSourceFactory: MediaSourceFactory

    private val type = "Alarm"
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_SONIFICATION)
        .setUsage(C.USAGE_ALARM)
        .build()

    private var listener: OnAlarmStateChangeListener? = null

    init {
        player.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {

                    }
                    Player.STATE_ENDED -> {
                        if (playWhenReady)
                            listener?.onStopped()
                    }
                    Player.STATE_READY -> {
                        if (playWhenReady)
                            listener?.onStarted()
                    }
                    Player.STATE_IDLE -> {

                    }
                }
            }

            override fun onPlayerError(error: ExoPlaybackException?) {
            }
        })
        mMediaSourceFactory = MediaSourceFactory(context, type)
        player.audioAttributes = audioAttributes
        player.playWhenReady = false
    }

    fun setOnAlarmStateChangeListener(listener: OnAlarmStateChangeListener) {
        this.listener = listener
    }

    fun removeOnAlarmStateChangeListener() {
        listener = null
    }

    fun play(url: String) {
        player.stop(true)

        val uri = Uri.parse(url)
        val mediaSource = mMediaSourceFactory.createHttpMediaSource(uri)
        player.prepare(mediaSource, true, false)
        player.playWhenReady = true
    }

    fun playLocalAlarm() {
        // todo
    }

    fun stop() {
        player.playWhenReady = false
    }

    interface OnAlarmStateChangeListener {
        fun onStarted()
        fun onStopped()
    }
}