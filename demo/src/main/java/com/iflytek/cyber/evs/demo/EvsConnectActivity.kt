package com.iflytek.cyber.evs.demo

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.*
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process.myUid
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.iflytek.cyber.evs.demo.databinding.ActivityEvsConnectBinding
import com.iflytek.cyber.evs.demo.utils.PrefUtil
import com.iflytek.cyber.evs.sdk.EvsError
import com.iflytek.cyber.evs.sdk.agent.AudioPlayer
import com.iflytek.cyber.evs.sdk.agent.PlaybackController
import com.iflytek.cyber.evs.sdk.agent.Recognizer
import com.iflytek.cyber.evs.sdk.agent.Recognizer.Profile.CloseTalk
import com.iflytek.cyber.evs.sdk.agent.Recognizer.Profile.FarField
import com.iflytek.cyber.evs.sdk.auth.AuthDelegate
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class EvsConnectActivity : AppCompatActivity() {
    companion object {
        const val TAG = "EvsConnect"

        private const val REQUEST_PERMISSION = 10001
        private const val REQUEST_USAGE_STATS_PERMISSION = 10002

        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }

    private var engineService: EngineService? = null
    private val adapter = ListAdapter()

    private lateinit var binding: ActivityEvsConnectBinding

    private var isTtsMode = false

    private val transmissionListener = object : EngineService.TransmissionListener {
        override fun onResponsesRaw(json: String) {
            // 获取response里面的replyKey
            Thread {
                val jsonObject = JSONObject(json)

                val responses = jsonObject.getJSONArray("iflyos_responses")

                val shortcutBuilder = StringBuilder()

                for (index in 0 until responses.length()) {
                    val response = responses.getJSONObject(index)
                    val header = response.getJSONObject("header")
                    if (index != 0)
                        shortcutBuilder.append(", ")
                    shortcutBuilder.append(header.getString("name"))
                }

                adapter.list.add(
                    SimpleModel(
                        1,
                        shortcutBuilder.toString(),
                        json,
                        System.currentTimeMillis()
                    )
                )
                runOnUiThread {
                    adapter.notifyDataSetChanged()

                    binding.recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                }
            }.start()
        }

        override fun onRequestRaw(obj: Any) {
            Thread {
                if (obj is String) {
                    val simpleModel = try {
                        val jsonObject = JSONObject(obj.toString())
                        val iflyosRequest = jsonObject.getJSONObject("iflyos_request")
                        val header = iflyosRequest.getJSONObject("header")
                        val headerName = header.getString("name")
                        SimpleModel(0, headerName, obj, System.currentTimeMillis())
                    } catch (t: Throwable) {
                        SimpleModel(0, null, obj, System.currentTimeMillis())
                    }
                    adapter.list.add(simpleModel)
                    runOnUiThread {
                        adapter.notifyDataSetChanged()

                        binding.recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                    }
                }
            }.start()
        }
    }

    private val recognizerCallback = object : Recognizer.RecognizerCallback {
        override fun onBackgroundRecognizeStateChanged(isBackgroundRecognize: Boolean) {
            // 背景录音
        }

        override fun onRecognizeStarted(isExpectReply: Boolean) {
            runOnUiThread {
                binding.recordingLayout.isVisible = true

                binding.iatText.text = "识别中"
            }
        }

        override fun onRecognizeStopped() {
            runOnUiThread {
                binding.recordingLayout.isVisible = false
            }
        }

        override fun onIntermediateText(text: String) {
            runOnUiThread {
                binding.iatText.text = text
            }
        }

    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is EngineService.EngineServiceBinder) {
                engineService = service.getService()
                engineService?.setTransmissionListener(transmissionListener)
                engineService?.setRecognizerCallback(recognizerCallback)
                initEvsUi()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            engineService?.setTransmissionListener(null)
            engineService?.setRecognizerCallback(null)
            engineService = null
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                EngineService.ACTION_EVS_DISCONNECTED -> {
                    initEvsUi()

                    engineService?.let {
                        val audioPlayer = it.getAudioPlayer()
                        audioPlayer.pause(AudioPlayer.TYPE_PLAYBACK)
                        audioPlayer.pause(AudioPlayer.TYPE_RING)
                        audioPlayer.pause(AudioPlayer.TYPE_TTS)
                    }

                    val code = intent.getIntExtra(EngineService.EXTRA_CODE, 0)
                    val message = intent.getStringExtra(EngineService.EXTRA_MESSAGE)

                    if (code == 1000) {
                        // 1000为正常断开连接
                        Log.d(TAG, "EVS Disconnected normally.")
                    } else {
                        Toast.makeText(
                            this@EvsConnectActivity, "error: $code, message: $message",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                EngineService.ACTION_EVS_CONNECTED -> {
                    initEvsUi()
                }
                EngineService.ACTION_EVS_CONNECT_FAILED -> {
                    val message = intent.getStringExtra(EngineService.EXTRA_MESSAGE)

                    Toast.makeText(
                        this@EvsConnectActivity, "connect failed, message: $message",
                        Toast.LENGTH_LONG
                    ).show()
                }
                EngineService.ACTION_EVS_SEND_FAILED -> {

                }
                EngineService.ACTION_EVS_ERROR -> {
                    val code = intent.getIntExtra(EngineService.EXTRA_CODE, 0)
                    val message = intent.getStringExtra(EngineService.EXTRA_MESSAGE)

                    Toast.makeText(
                        this@EvsConnectActivity, "error: $code, message: $message",
                        Toast.LENGTH_LONG
                    ).show()

                    val recognizer = engineService?.getRecognizer()
                    if (recognizer != null && recognizer.isRecording()) {
                        engineService?.getRecognizer()?.stopCapture()
                    }

                    if (code == EvsError.Code.ERROR_AUTH_FAILED) {
                        engineService?.disconnect()
                    }
                }
                VOLUME_CHANGED_ACTION -> {
                    engineService?.getSystem()?.sendStateSync()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initEvsUi() {
        binding.evsConnectLayout.isVisible = engineService?.isEvsConnected != true

        binding.disconnectCover.isVisible = engineService?.isEvsConnected != true

        engineService?.getAuthResponse()?.let {
            binding.connectToEvs.isEnabled = true
            binding.token.text = "Token: ${it.accessToken}"
        } ?: run {
            binding.connectToEvs.isEnabled = false
            binding.token.text = "请先授权后连接"
        }

        binding.wsUrl.text = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(getString(R.string.key_evs_ws_url), getString(R.string.default_ws_url))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEvsConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.run {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        binding.connectToEvs.setOnClickListener {
            if (hasPermission()) {
                Log.d(TAG, "has normal permission")
                engineService?.getAuthResponse()?.let {
                    if (engineService?.getAppAction() != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            if (hasUsageStatsPermission()) {
                                engineService?.connectEvs()
                            } else {
                                Toast.makeText(
                                    this@EvsConnectActivity, "请为demo开启使用情况访问权限",
                                    Toast.LENGTH_SHORT
                                ).show()
                                startActivityForResult(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                                    REQUEST_USAGE_STATS_PERMISSION
                                )
                            }
                        } else {
                            engineService?.connectEvs()
                        }
                    } else {
                        engineService?.connectEvs()
                    }
                } ?: run {
                    // need auth at first
                }
            } else {
                Log.d(TAG, "Not has normal permission")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_SETTINGS),
                    REQUEST_PERMISSION
                )
            }
        }
        binding.audioIn.setOnClickListener {
            if (hasPermission()) {
                var trans_mode = isTtsMode
                engineService?.sendAudioIn(trans_mode)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_SETTINGS),
                    REQUEST_PERMISSION
                )
            }
        }
        binding.sendTextIn.setOnClickListener {
            val text = binding.textIn.text.toString()
            if (text.isNotEmpty()) {
                if (isTtsMode) {
                    engineService?.sendTts(text)
                } else {
                    engineService?.sendTextIn(text)
                }
            } else {
                Snackbar.make(binding.container, "文本不能为空", Snackbar.LENGTH_SHORT).show()
            }
        }
        binding.textIn.setOnLongClickListener {
            if (!isTtsMode) {
                isTtsMode = true
                binding.sendTextIn.text = "翻译"
            } else {
                isTtsMode = false
                binding.sendTextIn.text = "识别"
            }
            false
        }

        binding.textIn.hint = "长按切换识别/翻译"

        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(
                this,
                LinearLayoutManager.VERTICAL
            )
        )
        binding.recyclerView.itemAnimator = DefaultItemAnimator()

        val intentFilter = IntentFilter()
        intentFilter.addAction(EngineService.ACTION_EVS_CONNECTED)
        intentFilter.addAction(EngineService.ACTION_EVS_DISCONNECTED)
        intentFilter.addAction(VOLUME_CHANGED_ACTION)
        registerReceiver(broadcastReceiver, intentFilter)

        val intent = Intent(this, EngineService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        binding.btnResume.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getPlaybackController()
                    ?.sendCommand(PlaybackController.Command.Resume)
            }
        }
        binding.btnPause.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getPlaybackController()
                    ?.sendCommand(PlaybackController.Command.Pause)
            }
        }
        binding.btnPrevious.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getPlaybackController()
                    ?.sendCommand(PlaybackController.Command.Previous)
            }
        }
        binding.btnNext.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getPlaybackController()
                    ?.sendCommand(PlaybackController.Command.Next)
            }
        }
        binding.btnExit.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getPlaybackController()
                    ?.sendCommand(PlaybackController.Command.Exit)
            }
        }
        binding.sendStateSync.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getSystem()?.sendStateSync()
            }
        }
        binding.customContext.setOnCheckedChangeListener { _, isChecked ->
            binding.btnCustomContext.isEnabled = isChecked

            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            pref.edit().putBoolean(getString(R.string.key_custom_context_enabled), isChecked)
                .apply()
        }
        binding.customContext.isChecked = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.key_custom_context_enabled), false)
        binding.btnCustomContext.setOnClickListener {
            val currentContext = engineService?.getCurrentIflyosContext()
            val json = JSONObject(currentContext)

            val editContext = Intent(this, EditCustomContextActivity::class.java)
            editContext.putExtra("default_context", json.toString(4))
            startActivity(editContext)
        }
        binding.backgroundRecognize.setOnCheckedChangeListener { _, isChecked ->
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            pref.edit().putBoolean(getString(R.string.key_background_recognize), isChecked)
                .apply()
        }
        binding.backgroundRecognize.isChecked = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.key_background_recognize), false)
        binding.cancel.setOnClickListener {
            engineService?.getRecognizer()?.requestCancel()
        }
        val profile = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(
                getString(R.string.key_recognize_profile),
                CloseTalk.value
            )
        if (profile == FarField.value) {
            binding.profileRadio.check(R.id.radio_far_field)
        } else {
            binding.profileRadio.check(R.id.radio_close_talk)
        }
        binding.profileRadio.setOnCheckedChangeListener { _, checkedId ->
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            val value = if (checkedId == R.id.radio_far_field) {
                FarField.value
            } else {
                CloseTalk.value
            }
            pref.edit().putString(getString(R.string.key_recognize_profile), value).apply()
        }
        binding.disableResponseSound.isChecked = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.key_disable_response_sound), false)
        binding.disableResponseSound.setOnCheckedChangeListener { _, _ ->
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            pref.edit().putBoolean(
                getString(R.string.key_disable_response_sound),
                binding.disableResponseSound.isChecked
            ).apply()
        }

        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.key_custom_location), false)
            .let { isCustomLocationEnabled ->
                binding.customLocation.isChecked = isCustomLocationEnabled

                binding.locationContainer.setBackgroundColor(
                    if (isCustomLocationEnabled) {
                        Color.TRANSPARENT
                    } else {
                        Color.parseColor("#33000000")
                    }
                )

                binding.latitude.isEnabled = isCustomLocationEnabled
                binding.longitude.isEnabled = isCustomLocationEnabled
                binding.applyCustomLocation.isEnabled = isCustomLocationEnabled
            }
        initCustomLocationValue()
        binding.customLocation.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(getString(R.string.key_custom_location), isChecked)
                .apply()

            binding.locationContainer.setBackgroundColor(
                if (isChecked) {
                    Color.TRANSPARENT
                } else {
                    Color.parseColor("#33000000")
                }
            )

            if (binding.latitude.hasFocus()) {
                binding.latitude.clearFocus()
            }

            binding.latitude.isEnabled = isChecked
            binding.longitude.isEnabled = isChecked
            binding.applyCustomLocation.isEnabled = isChecked
        }
        binding.applyCustomLocation.setOnClickListener {
            var setSucceed: Boolean
            try {
                setSucceed =
                    if (binding.latitude.text.isNullOrEmpty() || binding.longitude.text.isNullOrEmpty()) {
                        false
                    } else {
                        val latitudeText = binding.latitude.text.toString().toFloat()
                        val longitudeText = binding.longitude.text.toString().toFloat()

                        if (latitudeText in -90f..90f && longitudeText in -180f..180f) {
                            PreferenceManager.getDefaultSharedPreferences(this)
                                .edit()
                                .putFloat(getString(R.string.key_latitude), latitudeText)
                                .putFloat(getString(R.string.key_longitude), longitudeText)
                                .apply()

                            true
                        } else {
                            false
                        }
                    }
            } catch (t: Throwable) {
                setSucceed = false
            }
            if (!setSucceed) {
                Toast.makeText(this, "经纬度格式不符合要求", Toast.LENGTH_SHORT).show()
            } else {
                initCustomLocationValue()
            }
        }
        binding.clearCustomLocation.setOnClickListener {
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .remove(getString(R.string.key_latitude))
                .remove(getString(R.string.key_longitude))
                .apply()

            initCustomLocationValue()
        }

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.recordingLayout.isVisible -> {
                        engineService?.getRecognizer()?.requestCancel()
                    }
                    binding.drawer.isDrawerOpen(GravityCompat.END) -> {
                        binding.drawer.closeDrawer(GravityCompat.END)
                    }
                    else -> {
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(broadcastReceiver)
        unbindService(serviceConnection)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                "android:get_usage_stats",
                android.os.Process.myUid(), packageName
            )
        } else {
            appOps.checkOpNoThrow(
                "android:get_usage_stats",
                android.os.Process.myUid(), packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @SuppressLint("SetTextI18n")
    private fun initCustomLocationValue() {
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val latitude = pref.getFloat(getString(R.string.key_latitude), Float.MIN_VALUE)
        val longitude = pref.getFloat(getString(R.string.key_longitude), Float.MIN_VALUE)
        if (latitude != Float.MIN_VALUE && longitude != Float.MIN_VALUE) {
            binding.currentLocation.text = "当前自定义经纬度: $longitude, $latitude"
        } else {
            binding.currentLocation.text = "当前自定义经纬度: 无"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_USAGE_STATS_PERMISSION) {
            if (hasPermission()) {
                engineService?.connectEvs()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_SETTINGS),
                    REQUEST_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_PERMISSION) {
            for (i in permissions.indices) {
                if (permissions[i] == Manifest.permission.RECORD_AUDIO) {
                    if (grantResults[i] == PermissionChecker.PERMISSION_GRANTED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            if (hasUsageStatsPermission()) {
                                engineService?.connectEvs()
                            } else {
                                Toast.makeText(
                                    this@EvsConnectActivity, "请为demo开启使用情况访问权限",
                                    Toast.LENGTH_SHORT
                                ).show()
                                startActivityForResult(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                                    REQUEST_USAGE_STATS_PERMISSION
                                )
                            }
                        } else {
                            engineService?.connectEvs()
                        }
                    }
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (adapter.itemCount > 0)
            binding.recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
            R.id.mi_clear_log -> {
                adapter.list.clear()
                adapter.notifyDataSetChanged()
            }
            R.id.mi_disconnect -> {
                engineService?.disconnect()
            }
            R.id.mi_current_auth_info -> {
                val builder = AlertDialog.Builder(this)
                    .setPositiveButton(android.R.string.yes, null)
                    .setTitle(R.string.current_auth_info)
                val response = AuthDelegate.getAuthResponseFromPref(this)
                val message = if (response == null) {
                    "无"
                } else {
                    builder.setNegativeButton(R.string.copy_access_token) { _, _ ->
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(
                            ClipData.newPlainText(response.accessToken, response.accessToken)
                        )
                    }
                    getString(
                        R.string.auth_params_summary,
                        PrefUtil.getFromPref(baseContext, "auth_params"),
                        response.toJSONString()
                    )
                }
                builder.setMessage(message)
                    .show()
            }
            R.id.mi_start_evaluating -> {
                if (engineService?.isEvsConnected != true) {
                    Snackbar.make(binding.container, "请先连接 EVS", Snackbar.LENGTH_SHORT).show()
                    return true
                }

                val view = View.inflate(this, R.layout.layout_evaluate_info, null)
                val spinnerLanguage = view.findViewById<Spinner>(R.id.spinner_language)
                val languageValues = arrayOf(
                    Recognizer.LANGUAGE_EN_US, Recognizer.LANGUAGE_ZH_CN
                )
                spinnerLanguage.adapter = ArrayAdapter<String>(
                    this, android.R.layout.simple_list_item_1, android.R.id.text1, languageValues
                )
                val spinnerCategory = view.findViewById<Spinner>(R.id.spinner_category)
                val categoryValues = arrayOf(
                    Recognizer.EVALUATE_CATEGORY_READ_CHAPTER,
                    Recognizer.EVALUATE_CATEGORY_READ_SENTENCE,
                    Recognizer.EVALUATE_CATEGORY_READ_WORD,
                    Recognizer.EVALUATE_CATEGORY_READ_SYLLABLE
                )
                val categories = arrayOf(
                    "篇章朗读 - 仅英文可用",
                    "句子朗读 - 中英文可用",
                    "词语朗读 - 中英文可用",
                    "单字朗读 - 仅中文可用"
                )
                spinnerCategory.adapter = ArrayAdapter<String>(
                    this, android.R.layout.simple_list_item_1, android.R.id.text1, categories
                )
                spinnerCategory.onItemSelectedListener = object : OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {

                    }

                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        val categoryValue = categoryValues[position]
                        if (categoryValue == Recognizer.EVALUATE_CATEGORY_READ_CHAPTER) {
                            spinnerLanguage.isEnabled = false
                            spinnerLanguage.setSelection(0)
                        } else if (categoryValue == Recognizer.EVALUATE_CATEGORY_READ_SYLLABLE) {
                            spinnerLanguage.isEnabled = false
                            spinnerLanguage.setSelection(1)
                        } else {
                            if (!spinnerLanguage.isEnabled) {
                                spinnerLanguage.isEnabled = true
                            }
                        }
                    }
                }
                val etText = view.findViewById<EditText>(R.id.et_text)

                AlertDialog.Builder(this)
                    .setTitle(R.string.start_evaluating)
                    .setView(view)
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        val language = languageValues[spinnerLanguage.selectedItemPosition]
                        val category = categoryValues[spinnerCategory.selectedItemPosition]
                        val text = etText.text.toString()
                        engineService?.getRecognizer()?.sendEvaluate(language, category, text)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_evs_connect, menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun hasPermission(): Boolean {
        Log.d(TAG, "check self permission")
        return PermissionChecker.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PermissionChecker.PERMISSION_GRANTED
    }

    private class ListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        val list = mutableListOf<SimpleModel>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val holder = ItemViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_json, parent, false)
            )
            holder.itemView.setOnClickListener(object : View.OnClickListener {
                var lastClickTime = 0L
                override fun onClick(v: View) {
                    if (SystemClock.elapsedRealtime() - lastClickTime < 1000) {
                        return
                    }
                    lastClickTime = SystemClock.elapsedRealtime()

                    val position = holder.adapterPosition
                    val model = list[position]

                    val intent = Intent(v.context, JsonViewerActivity::class.java)
                    intent.putExtra("json", model.json)
                    v.context.startActivity(intent)
                }
            })
            return holder
        }

        override fun getItemCount(): Int {
            return list.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is ItemViewHolder) {
                val model = list[position]
                if (model.flag == 0) {
                    holder.itemView.setBackgroundColor(
                        ActivityCompat.getColor(
                            holder.itemView.context, R.color.colorUp
                        )
                    )
                    holder.iconView.setImageResource(R.drawable.ic_cloud_upload_black_24dp)
                    holder.typeView.text = "请求"
                } else {
                    holder.itemView.setBackgroundColor(
                        ActivityCompat.getColor(
                            holder.itemView.context, R.color.colorDown
                        )
                    )
                    holder.iconView.setImageResource(R.drawable.ic_cloud_download_black_24dp)
                    holder.typeView.text = "响应"
                }
                holder.jsonView.text = model.json
                holder.shortCutView.text = model.shortcut
                holder.shortCutView.isVisible = !model.shortcut.isNullOrEmpty()
                holder.timeView.text = SimpleDateFormat("hh:mm:ss.SSS", Locale.getDefault()).format(
                    Date().apply { time = model.timestamp }
                )
            }
        }
    }

    private class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.icon)
        val shortCutView: TextView = itemView.findViewById(R.id.shortcut)
        val jsonView: TextView = itemView.findViewById(R.id.json)
        val timeView: TextView = itemView.findViewById(R.id.time)
        val typeView: TextView = itemView.findViewById(R.id.type)
    }

    data class SimpleModel(
        val flag: Int,
        val shortcut: String?,
        val json: String,
        val timestamp: Long
    )
}