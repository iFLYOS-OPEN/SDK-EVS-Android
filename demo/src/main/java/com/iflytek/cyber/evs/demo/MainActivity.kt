package com.iflytek.cyber.evs.demo

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.iflytek.cyber.evs.demo.databinding.ActivityMainBinding
import com.iflytek.cyber.evs.demo.utils.DeviceUtils


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.authItem.setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
        }
        binding.evsConnectItem.setOnClickListener {
            startActivity(Intent(this, EvsConnectActivity::class.java))
        }
        binding.evsSettingsItem.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.evsVideoItem.setOnClickListener {
            startActivity(Intent(this, VideoActivity::class.java))
        }
        binding.evsVideoItem.visibility = View.INVISIBLE
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

        binding.currentConfig.text = """
            当前配置
            client id: $clientId
            设备 ID: $deviceId
            EVS 服务地址: $wsServerUrl
            授权服务地址: $authUrl
        """.trimIndent()
    }
}