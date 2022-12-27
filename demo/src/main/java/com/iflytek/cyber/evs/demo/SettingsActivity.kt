package com.iflytek.cyber.evs.demo

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.textfield.TextInputLayout
import com.iflytek.cyber.evs.demo.utils.DeviceUtils
import kotlin.math.roundToInt


class SettingsActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_QR_CDOE = 10101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val frameLayout = FrameLayout(this)
        setContentView(frameLayout)
        frameLayout.id = (Math.random() * Int.MAX_VALUE).roundToInt()

        title = getString(R.string.settings)
        supportActionBar?.let {
            it.setHomeButtonEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
        }

        supportFragmentManager.beginTransaction().replace(frameLayout.id, SettingsFragment())
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private var clientIdView: View? = null

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_preference, rootKey)

            (findPreference(getString(R.string.key_evs_ws_url)) as? EditTextPreference)?.let {
                it.summary = it.text
                it.setOnPreferenceChangeListener { preference, value ->
                    preference.summary = value.toString()
                    true
                }
            }
            (findPreference(getString(R.string.key_auth_url)) as? EditTextPreference)?.let {
                it.summary = it.text
                it.setOnPreferenceChangeListener { preference, value ->
                    preference.summary = value.toString()
                    true
                }
            }
            (findPreference<Preference>(getString(R.string.key_client_id)))?.let {
                it.summary = it.sharedPreferences?.getString(
                    getString(R.string.key_client_id),
                    getString(R.string.default_client_id)
                )
                it.setOnPreferenceChangeListener { preference, value ->
                    preference.summary = value.toString()
                    true
                }
                it.setOnPreferenceClickListener { pre ->
                    val view =
                        LayoutInflater.from(pre.context).inflate(R.layout.dialog_stub_input, null)
                    val editText = view.findViewById<EditText>(R.id.input_message)
                    val inputLayout = view.findViewById<TextInputLayout>(R.id.input_layout)
                    inputLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
                    inputLayout.setEndIconDrawable(R.drawable.ic_scan)
                    inputLayout.setEndIconOnClickListener {
                        if (PermissionChecker.checkSelfPermission(
                                pre.context,
                                Manifest.permission.CAMERA
                            ) == PermissionChecker.PERMISSION_GRANTED
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                val intent = Intent(context, ScanQRCodeActivity::class.java)
                                startActivityForResult(intent, REQUEST_QR_CDOE)
                            } else {
                                Toast.makeText(
                                    context,
                                    "扫码功能仅支持 Android 5.0 以上",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            activity?.let { activity ->
                                ActivityCompat.requestPermissions(
                                    activity,
                                    arrayOf(Manifest.permission.CAMERA),
                                    REQUEST_QR_CDOE
                                )
                            }
                            Toast.makeText(context, "请先允许相机权限", Toast.LENGTH_SHORT).show()
                        }
                    }
                    editText.setText(pre.summary)
                    pre.summary?.length?.let { length ->
                        editText.setSelection(length)
                    }
                    editText.post {
                        editText.requestFocus()
                    }
                    AlertDialog.Builder(pre.context)
                        .setView(view)
                        .setTitle(R.string.title_client_id)
                        .setPositiveButton(android.R.string.yes) { _, _ ->
                            val text = editText.text.toString()
                            pre.sharedPreferences?.edit()
                                ?.putString(getString(R.string.key_client_id), text)?.apply()
                            pre.summary = text
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(R.string.reset) { _, _ ->
                            val clearAuth = Intent(context, EngineService::class.java)
                            clearAuth.action = EngineService.ACTION_CLEAR_AUTH
                            context?.startService(clearAuth)

                            val defaultClientId = getString(R.string.default_client_id)
                            pre.sharedPreferences?.edit()
                                ?.putString(
                                    getString(R.string.key_client_id),
                                    defaultClientId
                                )?.apply()
                            pre.summary = defaultClientId
                        }
                        .setOnDismissListener {
                            if (clientIdView == view) {
                                clientIdView = null
                            }
                        }
                        .show()
                    clientIdView = view
                    true
                }
            }
            (findPreference(getString(R.string.key_device_id)) as? EditTextPreference)?.let {
                if (it.text.isNullOrEmpty()) {
                    it.text = DeviceUtils.getDeviceId(it.context)
                }
                it.summary = it.text
                it.setOnPreferenceChangeListener { preference, value ->
                    preference.summary = value.toString()
                    true
                }
            }
            findPreference<Preference>(getString(R.string.key_reset))?.let {
                it.setOnPreferenceClickListener { _ ->
                    AlertDialog.Builder(it.context)
                        .setTitle(R.string.reset)
                        .setMessage("点击确定后将对设置中所有内容设置到默认选项并清除授权信息")
                        .setPositiveButton("确定") { _, _ ->
                            (findPreference(getString(R.string.key_evs_ws_url)) as? EditTextPreference)?.let { editTextPreference ->
                                editTextPreference.text = getString(R.string.default_ws_url)
                                editTextPreference.summary = editTextPreference.text
                            }
                            (findPreference(getString(R.string.key_auth_url)) as? EditTextPreference)?.let { editTextPreference ->
                                editTextPreference.text = getString(R.string.default_auth_url)
                                editTextPreference.summary = editTextPreference.text
                            }
                            (findPreference(getString(R.string.key_client_id)) as? EditTextPreference)?.let { editTextPreference ->
                                editTextPreference.text = getString(R.string.default_client_id)
                                editTextPreference.summary = editTextPreference.text
                            }
                            (findPreference(getString(R.string.key_device_id)) as? EditTextPreference)?.let { editTextPreference ->
                                editTextPreference.text = DeviceUtils.getDeviceId(it.context)
                                editTextPreference.summary = editTextPreference.text
                            }
                            (findPreference(getString(R.string.key_list_codec)) as? ListPreference)?.let { listPreference ->
                                listPreference.value = listPreference.entryValues[0].toString()
                            }

                            val clearAuth = Intent(context, EngineService::class.java)
                            clearAuth.action = EngineService.ACTION_CLEAR_AUTH
                            context?.startService(clearAuth)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                }
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == REQUEST_QR_CDOE) {
                if (resultCode == ScanQRCodeActivity.RESULT_SUCCEED) {
                    clientIdView?.let {
                        val editText = it.findViewById<EditText>(R.id.input_message)
                        data?.getStringExtra(ScanQRCodeActivity.EXTRA_RESULT)?.let { result ->
                            editText.setText(result)
                            editText.setSelection(result.length)
                        }
                    }
                }
            }
        }
    }

}