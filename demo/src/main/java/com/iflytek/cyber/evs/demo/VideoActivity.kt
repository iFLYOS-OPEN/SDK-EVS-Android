package com.iflytek.cyber.evs.demo

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import com.iflytek.cyber.evs.sdk.agent.impl.VideoPlayerInstance
import kotlinx.android.synthetic.main.activity_videoplayer.*

class VideoActivity : AppCompatActivity() {
    var playerInstance : VideoPlayerInstance? = null

    val VIDEO_URL = "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_videoplayer)

        playerInstance = VideoPlayerInstance(this,
            video_view as com.google.android.exoplayer2.ui.PlayerView)

        btn_play.setOnClickListener {
            playerInstance?.play(VIDEO_URL)
        }

        btn_pause.setOnClickListener {
            playerInstance?.pause()
        }

        btn_resume.setOnClickListener {
            playerInstance?.resume()
        }

        btn_stop.setOnClickListener {
            playerInstance?.stop()
        }
    }

}