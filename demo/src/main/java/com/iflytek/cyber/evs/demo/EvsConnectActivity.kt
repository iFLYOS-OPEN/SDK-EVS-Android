package com.iflytek.cyber.evs.demo

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
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
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.fastjson.JSON
import com.google.android.material.snackbar.Snackbar
import com.iflytek.cyber.evs.demo.utils.PrefUtil
import com.iflytek.cyber.evs.sdk.agent.AudioPlayer
import com.iflytek.cyber.evs.sdk.agent.PlaybackController
import com.iflytek.cyber.evs.sdk.agent.Recognizer
import com.iflytek.cyber.evs.sdk.model.OsResponse
import com.iflytek.cyber.evs.sdk.model.OsResponseBody
import kotlinx.android.synthetic.main.activity_evs_connect.*

class EvsConnectActivity : AppCompatActivity() {
    companion object {
        const val TAG = "EvsConnect"

        private const val REQUEST_PERMISSION = 10001
        private const val REQUEST_USAGE_STATS_PERMISSION = 10002

        private const val CONTROL_ACTION_PREVIOUS = "com.iflytek.cyber.evs.demo.control.ACTION_PREVIOUS"
        private const val CONTROL_ACTION_NEXT = "com.iflytek.cyber.evs.demo.control.ACTION_NEXT"
        private const val CONTROL_ACTION_EXIT = "com.iflytek.cyber.evs.demo.control.ACTION_EXIT"
        private const val CONTROL_ACTION_PAUSE = "com.iflytek.cyber.evs.demo.control.ACTION_PAUSE"
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }

    private var engineService: EngineService? = null
    private val adapter = ListAdapter()

    private var isTtsMode = false
    private var replyKey: String? = null

    private val transmissionListener = object : EngineService.TransmissionListener {
        override fun onResponsesRaw(json: String) {
            // 获取response里面的replyKey
            val responseBody = JSON.parseObject(json, OsResponseBody::class.javaObjectType)
            val key = getReplyKey(responseBody.responses)
            if (!key.isNullOrEmpty()) {
                replyKey = key
            }

            adapter.list.add(SimpleModel(1, json))
            runOnUiThread {
                adapter.notifyDataSetChanged()
            }

            recycler_view.smoothScrollToPosition(adapter.itemCount - 1)
        }

        override fun onRequestRaw(obj: Any) {
            if (obj is String) {
                adapter.list.add(SimpleModel(0, obj))
                runOnUiThread {
                    adapter.notifyDataSetChanged()
                }

                recycler_view.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private val recognizerCallback = object : Recognizer.RecognizerCallback {
        override fun onRecognizeStarted() {
            runOnUiThread {
                recording_layout.isVisible = true

                iat_text.text = ""
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
                engineService?.getAudioPlayer()?.addListener(mediaStateListener)
                initEvsUi()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            engineService?.setTransmissionListener(null)
            engineService?.setRecognizerCallback(null)
            engineService?.getAudioPlayer()?.removeListener(mediaStateListener)
            engineService = null
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                EngineService.ACTION_EVS_DISCONNECTED -> {
                    initEvsUi()

                    engineService?.let {
                        var audioPlayer = it.getAudioPlayer()
                        audioPlayer.pause(AudioPlayer.TYPE_PLAYBACK)
                        audioPlayer.pause(AudioPlayer.TYPE_RING)
                        audioPlayer.pause(AudioPlayer.TYPE_TTS)
                        cancelControlBar()
                    }

                    val code = intent.getIntExtra(EngineService.EXTRA_CODE, 0)
                    val message = intent.getStringExtra(EngineService.EXTRA_MESSAGE)

                    if (code == 1000) {
                        // 1000为正常断开连接
                    } else {
                        Toast.makeText(this@EvsConnectActivity, "error: $code, message: $message",
                            Toast.LENGTH_LONG).show()
                    }
                }
                EngineService.ACTION_EVS_CONNECTED -> {
                    initEvsUi()
                }
                VOLUME_CHANGED_ACTION -> {
                    engineService?.let {
                        it.getSystem().sendStateSync()
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initEvsUi() {
        evs_connect_layout.isVisible = engineService?.isEvsConnected != true

        engineService?.getAuthResponse()?.let {
            connect_to_evs.isEnabled = true
            token.text = "Token: ${it.accessToken}"
        } ?: run {
            connect_to_evs.isEnabled = false
            token.text = "请先授权后连接"
        }
    }

    private fun getReplyKey(responses: List<OsResponse>): String? {
        responses.map {
            val replyKey = it.payload.getString("reply_key")
            if (!replyKey.isNullOrEmpty()) {
                return replyKey
            }
        }

        return null
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
                    val deviceId = PrefUtil.getFromPref(this@EvsConnectActivity, "device_id")
                    if (engineService?.getAppAction() != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            if (hasUsageStatsPermission()) {
                                engineService?.connect(deviceId)
                            } else {
                                Toast.makeText(this@EvsConnectActivity, "请为demo开启使用情况访问权限",
                                    Toast.LENGTH_SHORT).show()
                                startActivityForResult(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                                    REQUEST_USAGE_STATS_PERMISSION)
                            }
                        } else {
                            engineService?.connect(deviceId)
                        }
                    } else {
                        engineService?.connect(deviceId)
                    }
                } ?: run {
                    // need auth at first
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_PERMISSION
                )
            }
        }
        audio_in.setOnClickListener {
            if (hasPermission()) {
                if (replyKey.isNullOrEmpty()) {
                    engineService?.sendAudioIn()
                } else {
                    engineService?.sendAudioIn(replyKey)
                    replyKey = null
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_PERMISSION
                )
            }
        }
        send_text_in.setOnClickListener {
            val text = text_in.text.toString()
            if (text.isNotEmpty()) {
                if (isTtsMode) {
                    engineService?.sendTts(text)
                } else {
                    if (replyKey.isNullOrEmpty()) {
                        engineService?.sendTextIn(text)
                    } else {
                        engineService?.sendTextIn(text, replyKey)
                        replyKey = null
                    }
                }
            } else {
                Snackbar.make(container, "文本不能为空", Snackbar.LENGTH_SHORT).show()
            }
        }
        text_in.setOnLongClickListener {
            if (!isTtsMode) {
                isTtsMode = true
                send_text_in.setText("合成")
            } else {
                isTtsMode = false
                send_text_in.setText("识别")
            }
            false
        }

        text_in.setHint("长按切换识别/合成")

        recycler_view.adapter = adapter
        recycler_view.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        recycler_view.itemAnimator = DefaultItemAnimator()

        val intentFilter = IntentFilter()
        intentFilter.addAction(EngineService.ACTION_EVS_CONNECTED)
        intentFilter.addAction(EngineService.ACTION_EVS_DISCONNECTED)
        intentFilter.addAction(VOLUME_CHANGED_ACTION)
        registerReceiver(broadcastReceiver, intentFilter)
        registerControlReceiver()

        val intent = Intent(this, EngineService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onBackPressed() {
        if (recording_layout.isVisible) {
            engineService?.getRecognizer()?.stopCapture()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(broadcastReceiver)
        unregisterReceiver(controlReceiver)
        unbindService(serviceConnection)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(),
            packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_USAGE_STATS_PERMISSION) {
            if (hasPermission()) {
                val deviceId = PrefUtil.getFromPref(this@EvsConnectActivity, "device_id")
                engineService?.connect(deviceId)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSION) {
            for (i in 0 until permissions.size) {
                if (permissions[i] == Manifest.permission.RECORD_AUDIO) {
                    if (grantResults[i] == PermissionChecker.PERMISSION_GRANTED) {
                        val deviceId = PrefUtil.getFromPref(this@EvsConnectActivity, "device_id")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            if (hasUsageStatsPermission()) {
                                engineService?.connect(deviceId)
                            } else {
                                Toast.makeText(this@EvsConnectActivity, "请为demo开启使用情况访问权限",
                                    Toast.LENGTH_SHORT).show()
                                startActivityForResult(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                                    REQUEST_USAGE_STATS_PERMISSION)
                            }
                        } else {
                            engineService?.connect(deviceId)
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
        when {
            item.itemId == android.R.id.home -> onBackPressed()
            item.itemId == R.id.mi_clear_log -> {
                adapter.list.clear()
                adapter.notifyDataSetChanged()
            }
            item.itemId == R.id.mi_disconnect -> {
                engineService?.disconnect()
            }
//            item.itemId == R.id.mi_settings -> {
//                var intent = Intent(this, SettingsActivity::class.java)
//                startActivity(intent)
//            }
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
                } else {
                    holder.itemView.setBackgroundColor(
                        ActivityCompat.getColor(
                            holder.itemView.context, R.color.colorDown
                        )
                    )
                    holder.iconView.setImageResource(R.drawable.ic_cloud_download_black_24dp)
                }
                holder.jsonView.text = model.json
            }
        }
    }

    private class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.icon)
        val jsonView: TextView = itemView.findViewById(R.id.json)
    }

    data class SimpleModel(val flag: Int, val json: String)

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                when (intent.action) {
                    CONTROL_ACTION_PREVIOUS -> {
                        engineService?.let {
                            it.getPlaybackController()?.sendCommand(PlaybackController.Command.Previous)
                        }
                    }
                    CONTROL_ACTION_NEXT -> {
                        engineService?.let {
                            it.getPlaybackController()?.sendCommand(PlaybackController.Command.Next)
                        }
                    }
                    CONTROL_ACTION_PAUSE -> {
                        engineService?.let {
                            it.getPlaybackController()?.sendCommand(
                                if (isPaused) PlaybackController.Command.Resume else PlaybackController.Command.Pause)
                        }
                    }
                    CONTROL_ACTION_EXIT -> {
                        engineService?.let {
                            it.getPlaybackController()?.sendCommand(PlaybackController.Command.Exit)
                        }
                    }
                    else -> {

                    }
                }
            }
        }
    }

    private fun registerControlReceiver() {
        val filter = IntentFilter()
        filter.addAction(CONTROL_ACTION_PREVIOUS)
        filter.addAction(CONTROL_ACTION_NEXT)
        filter.addAction(CONTROL_ACTION_EXIT)
        filter.addAction(CONTROL_ACTION_PAUSE)

        registerReceiver(controlReceiver, filter)
    }

    private var contentViews: RemoteViews? = null
    private var notification: Notification? = null
    private var notificationId = 1
    private var isPaused: Boolean = false

    fun cancelControlBar() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)
    }

    fun notifyControlBar() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = Notification.Builder(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("1", "abc", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)

            builder.setChannelId("1")
        }

        if (contentViews == null) {
            contentViews = RemoteViews(packageName, R.layout.control_bar)
            contentViews?.let {
                it.setOnClickPendingIntent(
                    R.id.btn_previous,
                    PendingIntent.getBroadcast(
                        this, 1, Intent(CONTROL_ACTION_PREVIOUS),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                it.setOnClickPendingIntent(
                    R.id.btn_next,
                    PendingIntent.getBroadcast(
                        this, 1, Intent(CONTROL_ACTION_NEXT),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                it.setOnClickPendingIntent(
                    R.id.btn_exit,
                    PendingIntent.getBroadcast(
                        this, 1, Intent(CONTROL_ACTION_EXIT),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                it.setOnClickPendingIntent(
                    R.id.btn_pause,
                    PendingIntent.getBroadcast(
                        this, 1, Intent(CONTROL_ACTION_PAUSE),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
        }

        contentViews?.setTextViewText(R.id.btn_pause, "PAUSE")

        if (notification == null) {
            notification = builder.setContent(contentViews)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(false)
                .build()

            notification?.let {
                it.defaults = Notification.DEFAULT_ALL
                it.flags = Notification.FLAG_NO_CLEAR or it.flags
            }
        }

        nm.notify(notificationId, notification)
    }

    private val mediaStateListener = object : AudioPlayer.MediaStateChangedListener {
        override fun onStarted(player: AudioPlayer, type: String, resourceId: String) {
            if (type == AudioPlayer.TYPE_PLAYBACK) {
                Log.d(TAG, "onStarted, $type, $resourceId")

                isPaused = false
                notifyControlBar()
            }
        }

        override fun onResumed(player: AudioPlayer, type: String, resourceId: String) {
            if (type == AudioPlayer.TYPE_PLAYBACK) {
                isPaused = false
                contentViews?.setTextViewText(R.id.btn_pause, "PAUSE")

                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(notificationId, notification)
            }
        }

        override fun onPaused(player: AudioPlayer, type: String, resourceId: String) {
            if (type == AudioPlayer.TYPE_PLAYBACK) {
                isPaused = true
                contentViews?.setTextViewText(R.id.btn_pause, "RESUME")

                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(notificationId, notification)
            }
        }

        override fun onStopped(player: AudioPlayer, type: String, resourceId: String) {
            if (type == AudioPlayer.TYPE_PLAYBACK) {
                Log.d(TAG, "onStopped, $type, $resourceId")

                isPaused = false
                cancelControlBar()
            }
        }

        override fun onCompleted(player: AudioPlayer, type: String, resourceId: String) {
            if (type == AudioPlayer.TYPE_PLAYBACK) {
                Log.d(TAG, "onCompleted, $type, $resourceId")
            }
        }

        override fun onError(player: AudioPlayer, type: String, resourceId: String, errorCode: String) {

        }

        override fun onPositionUpdated(player: AudioPlayer, type: String, resourceId: String, position: Long) {

        }
    }
}