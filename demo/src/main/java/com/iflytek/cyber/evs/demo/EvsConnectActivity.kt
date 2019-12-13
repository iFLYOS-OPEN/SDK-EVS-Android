package com.iflytek.cyber.evs.demo

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.*
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
import com.iflytek.cyber.evs.demo.utils.PrefUtil
import com.iflytek.cyber.evs.sdk.EvsError
import com.iflytek.cyber.evs.sdk.agent.AudioPlayer
import com.iflytek.cyber.evs.sdk.agent.PlaybackController
import com.iflytek.cyber.evs.sdk.agent.Recognizer
import com.iflytek.cyber.evs.sdk.agent.Recognizer.Profile.CloseTalk
import com.iflytek.cyber.evs.sdk.agent.Recognizer.Profile.FarField
import com.iflytek.cyber.evs.sdk.auth.AuthDelegate
import kotlinx.android.synthetic.main.activity_evs_connect.*
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

                    recycler_view.smoothScrollToPosition(adapter.itemCount - 1)
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

                        recycler_view.smoothScrollToPosition(adapter.itemCount - 1)
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
                recording_layout.isVisible = true

                iat_text.text = "识别中"
            }
        }

        override fun onRecognizeStopped() {
            runOnUiThread {
                recording_layout.isVisible = false
            }
        }

        override fun onIntermediateText(text: String) {
            runOnUiThread {
                iat_text.text = text
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
        evs_connect_layout.isVisible = engineService?.isEvsConnected != true

        disconnect_cover.isVisible = engineService?.isEvsConnected != true

        engineService?.getAuthResponse()?.let {
            connect_to_evs.isEnabled = true
            token.text = "Token: ${it.accessToken}"
        } ?: run {
            connect_to_evs.isEnabled = false
            token.text = "请先授权后连接"
        }

        ws_url.text = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(getString(R.string.key_evs_ws_url), getString(R.string.default_ws_url))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_evs_connect)

        supportActionBar?.run {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        connect_to_evs.setOnClickListener {
            if (hasPermission()) {
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
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_SETTINGS),
                    REQUEST_PERMISSION
                )
            }
        }
        audio_in.setOnClickListener {
            if (hasPermission()) {
                engineService?.sendAudioIn()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_SETTINGS),
                    REQUEST_PERMISSION
                )
            }
        }
        send_text_in.setOnClickListener {
            val text = text_in.text.toString()
            if (text.isNotEmpty()) {
                if (isTtsMode) {
                    engineService?.sendTts(text)
                } else {
                    engineService?.sendTextIn(text)
                }
            } else {
                Snackbar.make(container, "文本不能为空", Snackbar.LENGTH_SHORT).show()
            }
        }
        text_in.setOnLongClickListener {
            if (!isTtsMode) {
                isTtsMode = true
                send_text_in.text = "合成"
            } else {
                isTtsMode = false
                send_text_in.text = "识别"
            }
            false
        }

        text_in.hint = "长按切换识别/合成"

        recycler_view.adapter = adapter
        recycler_view.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        recycler_view.itemAnimator = DefaultItemAnimator()

        val intentFilter = IntentFilter()
        intentFilter.addAction(EngineService.ACTION_EVS_CONNECTED)
        intentFilter.addAction(EngineService.ACTION_EVS_DISCONNECTED)
        intentFilter.addAction(VOLUME_CHANGED_ACTION)
        registerReceiver(broadcastReceiver, intentFilter)

        val intent = Intent(this, EngineService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        btn_resume.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getPlaybackController()
                    ?.sendCommand(PlaybackController.Command.Resume)
            }
        }
        btn_pause.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getPlaybackController()
                    ?.sendCommand(PlaybackController.Command.Pause)
            }
        }
        btn_previous.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getPlaybackController()
                    ?.sendCommand(PlaybackController.Command.Previous)
            }
        }
        btn_next.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getPlaybackController()
                    ?.sendCommand(PlaybackController.Command.Next)
            }
        }
        btn_exit.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getPlaybackController()
                    ?.sendCommand(PlaybackController.Command.Exit)
            }
        }
        send_state_sync.setOnClickListener {
            if (engineService?.isEvsConnected == true) {
                engineService?.getSystem()?.sendStateSync()
            }
        }
        custom_context.setOnCheckedChangeListener { _, isChecked ->
            btn_custom_context.isEnabled = isChecked

            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            pref.edit().putBoolean(getString(R.string.key_custom_context_enabled), isChecked)
                .apply()
        }
        custom_context.isChecked = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.key_custom_context_enabled), false)
        btn_custom_context.setOnClickListener {
            val currentContext = engineService?.getCurrentIflyosContext()
            val json = JSONObject(currentContext)

            val editContext = Intent(this, EditCustomContextActivity::class.java)
            editContext.putExtra("default_context", json.toString(4))
            startActivity(editContext)
        }
        background_recognize.setOnCheckedChangeListener { _, isChecked ->
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            pref.edit().putBoolean(getString(R.string.key_background_recognize), isChecked)
                .apply()
        }
        background_recognize.isChecked = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.key_background_recognize), false)
        cancel.setOnClickListener {
            engineService?.getRecognizer()?.requestCancel()
        }
        val profile = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(
                getString(R.string.key_recognize_profile),
                CloseTalk.value
            )
        if (profile == FarField.value) {
            profile_radio.check(R.id.radio_far_field)
        } else {
            profile_radio.check(R.id.radio_close_talk)
        }
        profile_radio.setOnCheckedChangeListener { _, checkedId ->
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            val value = if (checkedId == R.id.radio_far_field) {
                FarField.value
            } else {
                CloseTalk.value
            }
            pref.edit().putString(getString(R.string.key_recognize_profile), value).apply()
        }
        disable_response_sound.isChecked = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.key_disable_response_sound), false)
        disable_response_sound.setOnCheckedChangeListener { _, _ ->
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            pref.edit().putBoolean(
                getString(R.string.key_disable_response_sound),
                disable_response_sound.isChecked
            ).apply()
        }
    }

    override fun onBackPressed() {
        when {
            recording_layout.isVisible -> {
                engineService?.getRecognizer()?.requestCancel()
            }
            drawer.isDrawerOpen(GravityCompat.END) -> {
                drawer.closeDrawer(GravityCompat.END)
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(broadcastReceiver)
        unbindService(serviceConnection)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            "android:get_usage_stats", android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
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
            recycler_view.smoothScrollToPosition(adapter.itemCount - 1)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
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
                        cm.primaryClip =
                            ClipData.newPlainText(response.accessToken, response.accessToken)
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
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_evs_connect, menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun hasPermission(): Boolean {
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