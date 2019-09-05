package com.iflytek.cyber.evs.demo

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth_item.setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
        }
        evs_connect_item.setOnClickListener {
            startActivity(Intent(this, EvsConnectActivity::class.java))
        }

        evs_video_item.setOnClickListener {
            startActivity(Intent(this, VideoActivity::class.java))
        }
        evs_video_item.visibility = View.INVISIBLE
    }

}