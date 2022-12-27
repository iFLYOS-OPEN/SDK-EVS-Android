package com.iflytek.cyber.evs.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.iflytek.cyber.evs.demo.databinding.ActivityVideoplayerBinding
import com.iflytek.cyber.evs.sdk.agent.impl.VideoPlayerInstance

class VideoActivity : AppCompatActivity() {
    var playerInstance: VideoPlayerInstance? = null
    private lateinit var binding: ActivityVideoplayerBinding

    val VIDEO_URL = "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoplayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playerInstance = VideoPlayerInstance(
            this,
            binding.videoView
        )

        binding.btnPlay.setOnClickListener {
            playerInstance?.play(VIDEO_URL)
        }

        binding.btnPause.setOnClickListener {
            playerInstance?.pause()
        }

        binding.btnResume.setOnClickListener {
            playerInstance?.resume()
        }

        binding.btnStop.setOnClickListener {
            playerInstance?.stop()
        }
    }

}