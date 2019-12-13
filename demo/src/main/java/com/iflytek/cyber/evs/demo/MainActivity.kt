package com.iflytek.cyber.evs.demo

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.iflytek.cyber.evs.demo.utils.DeviceUtils
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
        evs_settings_item.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        evs_video_item.setOnClickListener {
            startActivity(Intent(this, VideoActivity::class.java))
        }
        evs_video_item.visibility = View.INVISIBLE
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()

        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val clientId =
            pref.getString(getString(R.string.key_client_id), getString(R.string.default_client_id))
        val deviceId =
            pref.getString(getString(R.string.key_device_id), DeviceUtils.getDeviceId(this))
        val wsServerUrl =
            pref.getString(getString(R.string.key_evs_ws_url), getString(R.string.default_ws_url))
        val authUrl =
            pref.getString(getString(R.string.key_auth_url), getString(R.string.default_auth_url))

        current_config.text = """
            当前配置
            client id: $clientId
            设备 ID: $deviceId
            EVS 服务地址: $wsServerUrl
            授权服务地址: $authUrl
        """.trimIndent()
    }
}