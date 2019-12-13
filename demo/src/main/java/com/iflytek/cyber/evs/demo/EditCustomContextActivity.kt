package com.iflytek.cyber.evs.demo

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_edit_custom_context.*

class EditCustomContextActivity : AppCompatActivity() {
    private var defaultContext: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_custom_context)

        defaultContext = intent.getStringExtra("default_context")

        title = "编辑自定义 Context"

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val customContext = pref.getString("custom_context", defaultContext)

        edit_text.setText(customContext)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_edit_custom_context, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.save_and_quit -> {
                val context = edit_text.text.toString()
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                pref.edit().putString("custom_context", context).apply()

                finish()
            }
            R.id.fill_current_context -> {
                edit_text.setText(defaultContext)
            }
            android.R.id.home -> {
                onBackPressed()
            }
        }
        return super.onOptionsItemSelected(item)
    }
}