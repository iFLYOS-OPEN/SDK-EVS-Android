package com.iflytek.cyber.evs.sdk

import android.app.Service
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import com.alibaba.fastjson.JSONObject
import com.iflytek.cyber.evs.sdk.agent.*
import com.iflytek.cyber.evs.sdk.agent.impl.*
import com.iflytek.cyber.evs.sdk.auth.AuthDelegate
import com.iflytek.cyber.evs.sdk.focus.*
import com.iflytek.cyber.evs.sdk.model.*
import com.iflytek.cyber.evs.sdk.socket.RequestBuilder
import com.iflytek.cyber.evs.sdk.socket.SocketManager
import com.iflytek.cyber.evs.sdk.utils.AppUtil
import com.iflytek.cyber.evs.sdk.utils.Log
import java.lang.Exception
import java.lang.IllegalArgumentException

abstract class EvsService : Service() {
    companion object {
        private const val TAG = "EvsService"

        private const val ACTION_PREFIX = "com.iflytek.cyber.evs.sdk.action"
        const val ACTION_CONNECT = "$ACTION_PREFIX.CONNECT"
        const val ACTION_DISCONNECT = "$ACTION_PREFIX.DISCONNECT"

        const val EXTRA_DEVICE_ID = "device_id"
    }

    private lateinit var audioPlayer: AudioPlayer
    private var alarm: Alarm? = null
    private var appAction: AppAction? = null
    private var interceptor: Interceptor? = null
    private var playbackController: PlaybackController? = null
    private lateinit var recognizer: Recognizer
    private var screen: Screen? = null
    private lateinit var speaker: Speaker
    private lateinit var system: System
    private var template: Template? = null
    private var videoPlayer: VideoPlayer? = null

    private var externalAudioFocusChannels: List<AudioFocusChannel> = emptyList()
    private var externalVisualFocusChannels: List<VisualFocusChannel> = emptyList()

    private val handler = Handler()

    private var handlerThread: HandlerThread? = null
    private var requestHandler: Handler? = null

    var isEvsConnected = false
        private set


    private val socketListener = object : SocketManager.SocketListener {
        override fun onSend(message: Any) {
            onRequestRaw(message)
        }

        override fun onConnected() {
            isEvsConnected = true
            onEvsConnected()

            RequestManager.sendRequest(System.NAME_STATE_SYNC, JSONObject())
        }

        override fun onDisconnected(code: Int, reason: String?, remote: Boolean) {
            isEvsConnected = false
            recognizer.stopCapture()

            onEvsDisconnected(code, reason)
        }

        override fun onMessage(message: String) {
            ResponseProcessor.putResponses(message)

            Thread {
                try {
                    onResponsesRaw(message)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }

    }

    private val audioFocusObserver = object : AudioFocusManager.AudioFocusObserver {
        override fun onAudioFocusChanged(channel: String, type: String, status: FocusStatus) {
            Log.d(TAG, "onAudioFocusChanged($channel,$type,$status)")
            var isConsumed = true
            when (channel) {
                AudioFocusManager.CHANNEL_ALARM -> {
                    when (type) {
                        AudioFocusManager.TYPE_RING -> when (status) {
                            FocusStatus.Idle -> audioPlayer.stop(AudioPlayer.TYPE_RING)
                            FocusStatus.Background -> audioPlayer.moveToBackground(AudioPlayer.TYPE_RING)
                            else -> audioPlayer.moveToForegroundIfAvailable(AudioPlayer.TYPE_RING)
                        }
                        AudioFocusManager.TYPE_ALARM -> {
                            alarm?.let { alarm ->
                                when (status) {
                                    FocusStatus.Background, FocusStatus.Idle ->
                                        alarm.stop()
                                    else -> {
                                        // ignore
                                    }
                                }
                            }
                        }
                        else -> isConsumed = false
                    }
                }
                AudioFocusManager.CHANNEL_CONTENT -> {
                    if (type == AudioFocusManager.TYPE_PLAYBACK) {
                        when (status) {
                            FocusStatus.Background -> audioPlayer.moveToBackground(AudioPlayer.TYPE_PLAYBACK)
                            FocusStatus.Idle -> audioPlayer.stop(AudioPlayer.TYPE_PLAYBACK)
                            else -> audioPlayer.moveToForegroundIfAvailable(AudioPlayer.TYPE_PLAYBACK)
                        }
                    } else {
                        isConsumed = false
                    }
                }
                AudioFocusManager.CHANNEL_DIAL -> {
                    isConsumed = false
                }
                AudioFocusManager.CHANNEL_INPUT -> {
                    if (type == AudioFocusManager.TYPE_RECOGNIZE ||
                            type == AudioFocusManager.TYPE_RECOGNIZE_V
                    ) {
                        // 有全双工实现后，此处逻辑需要更改
                        if (status == FocusStatus.Idle) {
                            if (recognizer.isBackgroundRecognize) {

                            } else {
                                // 直接取消录音，不需要结果了
                                recognizer.requestCancel()
                            }
                        } else {
                            // ignore
                        }
                    } else {
                        isConsumed = false
                    }
                }
                AudioFocusManager.CHANNEL_OUTPUT -> {
                    if (type == AudioFocusManager.TYPE_TTS) {
                        if (status == FocusStatus.Idle) {
                            audioPlayer.stop(AudioPlayer.TYPE_TTS)
                        }
                    } else {
                        isConsumed = false
                    }
                }
                else -> {
                    isConsumed = false
                }
            }

            if (!isConsumed) {
                externalAudioFocusChannels.map { audioFocusChannel ->
                    if (audioFocusChannel.getChannelName() == channel
                            && audioFocusChannel.getExternalType() == type
                    ) {
                        audioFocusChannel.onFocusChanged(status)
                    }
                }
            }

        }
    }
    private val visualFocusObserver = object : VisualFocusManager.VisualFocusObserver {
        override fun onVisualFocusChanged(channel: String, type: String, status: FocusStatus) {

        }
    }

    open fun onEvsConnected() {

    }

    open fun onEvsDisconnected(code: Int, message: String?) {

    }

    override fun onCreate() {
        super.onCreate()

        audioPlayer = AudioPlayerImpl(this)
        alarm = overrideAlarm()
        appAction = overrideAppAction()
        interceptor = overrideInterceptor()
        playbackController = overridePlaybackController()
        recognizer = overrideRecognizer()
        screen = overrideScreen()
        speaker = overrideSpeaker()
        system = overrideSystem()
        template = overrideTemplate()
        videoPlayer = overrideVideoPlayer()

        ResponseProcessor.init(
                alarm,
                appAction,
                audioPlayer,
                interceptor,
                playbackController,
                recognizer,
                screen,
                speaker,
                system,
                template,
                videoPlayer
        )
        RequestBuilder.init(
                alarm,
                appAction,
                audioPlayer,
                interceptor,
                playbackController,
                recognizer,
                screen,
                speaker,
                system,
                template,
                videoPlayer
        )

        ResponseProcessor.initHandler(handler)

        handlerThread = HandlerThread("request-handler")
        handlerThread?.start()
        requestHandler = Handler(handlerThread?.looper)
        requestHandler?.post {
            AppUtil.getForegroundApp(this@EvsService)
        }

        RequestManager.initHandler(requestHandler)

        AudioFocusManager.setFocusObserver(audioFocusObserver)
        VisualFocusManager.setFocusObserver(visualFocusObserver)

        SocketManager.addListener(socketListener)

        // check external audio focus channels available
        getExternalAudioFocusChannels().let { channels ->
            val audioFocusChannelTypes = HashSet<String>()
            channels.map { audioFocusChannel ->
                val channel = audioFocusChannel.getChannelName()
                val type = audioFocusChannel.getType()
                if (!AudioFocusManager.isManageableChannel(channel)) {
                    throw IllegalArgumentException(
                            "Illegal audio focus channel name {$channel}, channel name must be one of ${
                            AudioFocusManager.sortedChannels.contentToString()
                            }"
                    )
                } else {
                    if (audioFocusChannelTypes.contains(type)) {
                        throw IllegalArgumentException(
                                "Illegal audio focus channel type {$type} is duplicate."
                        )
                    } else {
                        audioFocusChannel.setupManager(AudioFocusManager)
                    }
                }
            }
            externalAudioFocusChannels = channels
        }

        // check external visual focus channels available
        getExternalVisualFocusChannels().let { channels ->

            val visualFocusChannelTypes = HashSet<String>()
            channels.map { visualFocusChannel ->
                val channel = visualFocusChannel.getChannelName()
                val type = visualFocusChannel.getType()
                if (!VisualFocusManager.isManageableChannel(channel)) {
                    throw IllegalArgumentException(
                            "Illegal visual focus channel name {$channel}, channel name must be one of ${
                            VisualFocusManager.sortedChannels.contentToString()
                            }"
                    )
                } else {
                    if (visualFocusChannelTypes.contains(type)) {
                        throw IllegalArgumentException(
                                "Illegal visual focus channel type {$type} is duplicate."
                        )
                    } else {
                        visualFocusChannel.setupManager(VisualFocusManager)
                    }
                }
            }
            externalVisualFocusChannels = channels
        }

        AuthDelegate.registerTokenChangedListener(this, tokenChangeListener)
    }

    private val tokenChangeListener = SharedPreferences.OnSharedPreferenceChangeListener {
        _: SharedPreferences?, key: String? ->

        if (key == AuthDelegate.PREF_KEY) {
            disconnect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        getRecognizer().stopCapture()
        getRecognizer().onDestroy()

        SocketManager.removeListener(socketListener)
        SocketManager.disconnect()

        AudioFocusManager.removeFocusObserver()
        VisualFocusManager.removeFocusObserver()

        ResponseProcessor.destroy()

        handlerThread?.quit()
        handlerThread = null

        AuthDelegate.unregisterTokenChangedListener(this, tokenChangeListener)
    }

    fun connect(deviceId: String) {
        connect("", deviceId)
    }

    fun connect(serverUrl: String?, deviceId: String) {
        getAuthResponse()?.let {
            val token = it.accessToken
            if (deviceId.isEmpty() || token.isEmpty()) {
                Log.e(TAG, "Illegal params while requesting connection. {deviceId: $deviceId, token: $token}")
            } else {
                val current = java.lang.System.currentTimeMillis() / 1000
                if (current - it.createdAt >= it.expiresIn) {
                    Log.w(TAG, "Access token {$token} is expired, " +
                            "try to refresh. {refreshToken: ${it.refreshToken}}")

                    AuthDelegate.refreshAccessToken(this, it.refreshToken,
                            object : AuthDelegate.AuthResponseCallback {
                                override fun onAuthFailed(errorBody: String?, throwable: Throwable?) {
                                    val message = throwable?.message ?: errorBody
                                    Log.e(TAG, "Fail to refresh token, $message")

                                    onEvsDisconnected(-1, "Refresh access token failed.")
                                }

                                override fun onAuthSuccess(authResponse: AuthResponse) {
                                    Log.d(TAG, "Refresh access token success.")

                                    RequestBuilder.setDeviceAuthInfo(deviceId, authResponse.accessToken)
                                    SocketManager.connect(serverUrl, deviceId, authResponse.accessToken)
                                }
                            })
                } else {
                    RequestBuilder.setDeviceAuthInfo(deviceId, token)
                    SocketManager.connect(serverUrl, deviceId, token)
                }
            }
        } ?: run {
            Log.w(TAG, "Auth response is null. Ignore WebSocket Connection Action.")
        }

    }

    fun disconnect() {
        SocketManager.disconnect()
    }

    @Suppress("unused")
    fun getAppAction() = appAction

    @Suppress("unused")
    fun getAlarm() = alarm

    @Suppress("unused")
    fun getAudioPlayer() = audioPlayer

    @Suppress("unused")
    fun getInterceptor() = interceptor

    @Suppress("unused")
    fun getPlaybackController() = playbackController

    @Suppress("unused")
    fun getRecognizer() = recognizer

    @Suppress("unused")
    fun getSpeaker() = speaker

    @Suppress("unused")
    fun getSystem() = system

    @Suppress("unused")
    fun getTemplate() = template

    @Suppress("unused")
    fun getVideoPlayer() = videoPlayer

    open fun overrideAppAction(): AppAction? {
        return AppActionImpl(this)
    }

    open fun overrideRecognizer(): Recognizer {
        return RecognizerImpl()
    }

    open fun overrideInterceptor(): Interceptor? {
        return null
    }

    open fun overrideSpeaker(): Speaker {
        return SpeakerImpl(this)
    }

    /**
     * 是否复写闹钟端能力
     * @return 返回闹钟能力实例。返回 null 则将使用云端闹钟能力，默认返回了基于 AlarmManager 的内置闹钟能力
     */
    open fun overrideAlarm(): Alarm? {
        return AlarmImpl(this)
    }

    /**
     * 是否复写 Screen 端能力
     * @return 返回 Screen 实例则代表设备支持此能力。默认返回 null 代表设备不支持此能力
     */
    open fun overrideScreen(): Screen? {
        return null
    }

    open fun overrideSystem(): System {
        return SystemImpl(this)
    }

    open fun overrideTemplate(): Template? {
        return null
    }

    open fun overrideVideoPlayer(): VideoPlayer? {
        return null
    }

    open fun overridePlaybackController(): PlaybackController? {
        return PlaybackControllerImpl()
    }

    /**
     * WebSocket 实际接收的数据
     * @param json 返回的响应 json
     */
    open fun onResponsesRaw(json: String) {

    }

    fun getAuthResponse(): AuthResponse? {
        return AuthDelegate.getAuthResponseFromPref(this)
    }

    /**
     * WebSocket 实际发送的数据
     * @param obj 参数可能为 [String], [ByteArray] 中的一个
     */
    open fun onRequestRaw(obj: Any) {

    }

    /**
     * 出错回调
     * @param code 错误码
     * @param message 提示消息
     */
    open fun onError(code: Int, message: String) {

    }

    open fun getExternalAudioFocusChannels(): List<AudioFocusChannel> {
        return emptyList()
    }

    open fun getExternalVisualFocusChannels(): List<VisualFocusChannel> {
        return emptyList()
    }

    open fun getVersion(): String {
        return "1.3"
    }
}