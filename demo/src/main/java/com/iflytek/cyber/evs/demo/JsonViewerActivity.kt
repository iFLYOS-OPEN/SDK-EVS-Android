package com.iflytek.cyber.evs.demo

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.iflytek.cyber.evs.demo.databinding.ActivityJsonViewerBinding
import org.json.JSONObject

class JsonViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityJsonViewerBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJsonViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.run {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        val text = intent.getStringExtra("json")
        try {
            binding.json.text = JSONObject(text).toString(2)
            binding.json.setTextIsSelectable(true)
        } catch (e: Exception) {
            binding.json.text = text
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home)
            onBackPressedDispatcher.onBackPressed()
        return super.onOptionsItemSelected(item)
    }
}