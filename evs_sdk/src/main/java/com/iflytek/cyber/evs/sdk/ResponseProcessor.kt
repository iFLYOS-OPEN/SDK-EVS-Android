package com.iflytek.cyber.evs.sdk

import android.os.Handler
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import com.iflytek.cyber.evs.sdk.agent.*
import com.iflytek.cyber.evs.sdk.focus.AudioFocusManager
import com.iflytek.cyber.evs.sdk.model.Constant
import com.iflytek.cyber.evs.sdk.model.OsResponse
import com.iflytek.cyber.evs.sdk.model.OsResponseBody
import com.iflytek.cyber.evs.sdk.utils.Log
import okhttp3.Response
import java.util.*
import kotlin.collections.HashMap

internal object ResponseProcessor {
    private const val TAG = "ResponseProcessor"

    /**
     * 缓存的 PlayerInfo 最大数量
     */
    private const val MAX_PLAYER_INFO_CACHE_SIZE = 40

    private var alarm: Alarm? = null
    private var appAction: AppAction? = null
    private var audioPlayer: AudioPlayer? = null
    private var interceptor: Interceptor? = null
    private var playbackController: PlaybackController? = null
    private var recognizer: Recognizer? = null
    private var screen: Screen? = null
    private var speaker: Speaker? = null
    private var system: System? = null
    private var template: Template? = null
    private var videoPlayer: VideoPlayer? = null

    private val playerInfoMap = HashMap<String, JSONObject>() // resourceId -> json(payload)
    private val upcomingPlaybackResources = mutableListOf<JSONObject>()

    private var handler: Handler? = null

    private var currentRequestId = ""
    private val pendingExecuteResponses = HashMap<String, MutableList<OsResponse>>() // requestId -> responses
    private val nearlyFinishedSentMap = HashMap<String, Boolean>() // resourceId -> isSent
    private val needSetOffsetResources = HashMap<String, Long>() // resourceId -> offset

    private val audioPlayerListener = object : AudioPlayer.MediaStateChangedListener {
        override fun onStarted(player: AudioPlayer, type: String, resourceId: String) {
            handler?.post {
                val payload = JSONObject()
                payload[AudioPlayer.KEY_TYPE] = AudioPlayer.SYNC_TYPE_STARTED
                payload[AudioPlayer.KEY_RESOURCE_ID] = resourceId
                when (type) {
                    AudioPlayer.TYPE_PLAYBACK -> {
                        nearlyFinishedSentMap[resourceId] = false

                        payload[AudioPlayer.KEY_OFFSET] = player.getOffset(type)
                        RequestManager.sendRequest(AudioPlayer.NAME_PLAYBACK_PROGRESS_SYNC, payload)

                        if (needSetOffsetResources.containsKey(resourceId)) {
                            player.seekTo(type, needSetOffsetResources[resourceId] ?: 0)
                            needSetOffsetResources.remove(resourceId)
                        }

                        if (playerInfoMap.containsKey(resourceId)) {
                            playerInfoMap[resourceId]?.let { playerInfo ->
                                screen?.renderPlayerInfo(playerInfo.toString())
                            }
                        }

                        AudioFocusManager.requestActive(
                            AudioFocusManager.CHANNEL_CONTENT,
                            AudioFocusManager.TYPE_PLAYBACK
                        )
                    }
                    AudioPlayer.TYPE_RING -> {
                        RequestManager.sendRequest(AudioPlayer.NAME_RING_PROGRESS_SYNC, payload)

                        AudioFocusManager.requestActive(AudioFocusManager.CHANNEL_ALARM, AudioFocusManager.TYPE_RING)
                    }
                    AudioPlayer.TYPE_TTS -> {
                        RequestManager.sendRequest(AudioPlayer.NAME_TTS_PROGRESS_SYNC, payload)

                        AudioFocusManager.requestActive(AudioFocusManager.CHANNEL_OUTPUT, AudioFocusManager.TYPE_TTS)
                    }
                }
            }
        }

        override fun onResumed(player: AudioPlayer, type: String, resourceId: String) {
            handler?.post {
                val payload = JSONObject()
                payload[AudioPlayer.KEY_TYPE] = AudioPlayer.SYNC_TYPE_STARTED
                payload[AudioPlayer.KEY_RESOURCE_ID] = resourceId
                when (type) {
                    AudioPlayer.TYPE_PLAYBACK -> {
                        payload[AudioPlayer.KEY_OFFSET] = player.getOffset(type)
                        RequestManager.sendRequest(AudioPlayer.NAME_PLAYBACK_PROGRESS_SYNC, payload)

                        if (needSetOffsetResources.containsKey(resourceId)) {
                            player.seekTo(type, needSetOffsetResources[resourceId] ?: 0)
                            needSetOffsetResources.remove(resourceId)
                        }
                    }
                    AudioPlayer.TYPE_RING -> {
                        RequestManager.sendRequest(AudioPlayer.NAME_RING_PROGRESS_SYNC, payload)
                    }
                    AudioPlayer.TYPE_TTS -> {
                        RequestManager.sendRequest(AudioPlayer.NAME_TTS_PROGRESS_SYNC, payload)
                    }
                }
            }
        }

        override fun onPaused(player: AudioPlayer, type: String, resourceId: String) {
            handler?.post {
                val payload = JSONObject()
                payload[AudioPlayer.KEY_TYPE] = AudioPlayer.SYNC_TYPE_PAUSED
                payload[AudioPlayer.KEY_RESOURCE_ID] = resourceId
                payload[AudioPlayer.KEY_OFFSET] = player.getOffset(type)

                when (type) {
                    AudioPlayer.TYPE_PLAYBACK -> {
                        RequestManager.sendRequest(AudioPlayer.NAME_PLAYBACK_PROGRESS_SYNC, payload)
                    }
                }
            }
        }

        override fun onStopped(player: AudioPlayer, type: String, resourceId: String) {
            // nothing needs to do
        }

        override fun onCompleted(player: AudioPlayer, type: String, resourceId: String) {
            handler?.post {
                val payload = JSONObject()
                payload[AudioPlayer.KEY_TYPE] = AudioPlayer.SYNC_TYPE_FINISHED
                payload[AudioPlayer.KEY_RESOURCE_ID] = resourceId
                when (type) {
                    AudioPlayer.TYPE_PLAYBACK -> {
                        payload[AudioPlayer.KEY_OFFSET] = player.getOffset(type)
                        RequestManager.sendRequest(AudioPlayer.NAME_PLAYBACK_PROGRESS_SYNC, payload)

                        if (upcomingPlaybackResources.isNotEmpty()) {
                            val nextPayload = upcomingPlaybackResources[0]

                            val nextResourceId = nextPayload.getString(AudioPlayer.KEY_RESOURCE_ID)
                            val url = nextPayload.getString(AudioPlayer.KEY_URL)

                            player.play(type, nextResourceId, url)

                            upcomingPlaybackResources.removeAt(0)
                        }

                        AudioFocusManager.requestAbandon(
                            AudioFocusManager.CHANNEL_CONTENT,
                            AudioFocusManager.TYPE_PLAYBACK
                        )
                    }
                    AudioPlayer.TYPE_RING -> {
                        RequestManager.sendRequest(AudioPlayer.NAME_RING_PROGRESS_SYNC, payload)

                        AudioFocusManager.requestAbandon(AudioFocusManager.CHANNEL_ALARM, AudioFocusManager.TYPE_RING)
                    }
                    AudioPlayer.TYPE_TTS -> {
                        RequestManager.sendRequest(AudioPlayer.NAME_TTS_PROGRESS_SYNC, payload)

                        if (currentRequestId.isNotEmpty()) {
                            val responses: MutableList<OsResponse>?
                            synchronized(pendingExecuteResponses) {
                                responses = pendingExecuteResponses[currentRequestId]
                            }

                            var findTarget = false
                            responses?.map {
                                if (it.header.name.startsWith(Constant.NAMESPACE_AUDIO_PLAYER)) {
                                    val cachePayload = it.payload
                                    val cacheResourceId = cachePayload.getString(AudioPlayer.KEY_RESOURCE_ID)
                                    findTarget = (cacheResourceId == resourceId) || findTarget
                                }
                            }
                            if (findTarget) {
                                responses?.removeAt(0)
                                startExecuteResponses()
                            }
                        }

                        AudioFocusManager.requestAbandon(AudioFocusManager.CHANNEL_OUTPUT, AudioFocusManager.TYPE_TTS)
                    }
                }
            }
        }

        override fun onPositionUpdated(player: AudioPlayer, type: String, resourceId: String, position: Long) {
            if (type == AudioPlayer.TYPE_PLAYBACK && nearlyFinishedSentMap[resourceId] == false) {
                handler?.post {
                    val duration = player.getDuration(type)
//                    if (position > 5000) { // for test
                    if (position * 3 > duration) {
                        nearlyFinishedSentMap[resourceId] = true

                        val payload = JSONObject()
                        payload[AudioPlayer.KEY_RESOURCE_ID] = resourceId
                        payload[AudioPlayer.KEY_OFFSET] = position
                        payload[AudioPlayer.KEY_TYPE] = AudioPlayer.SYNC_TYPE_NEARLY_FINISHED
                        RequestManager.sendRequest(AudioPlayer.NAME_PLAYBACK_PROGRESS_SYNC, payload)
                    }
                }
            }
        }

        override fun onError(player: AudioPlayer, type: String, resourceId: String, errorCode: String) {
            if (type == AudioPlayer.TYPE_PLAYBACK) {
                val payload = JSONObject()
                payload[AudioPlayer.KEY_TYPE] = AudioPlayer.SYNC_TYPE_FAILED
                payload[AudioPlayer.KEY_RESOURCE_ID] = resourceId
                payload[AudioPlayer.KEY_ERROR_CODE] = errorCode
                RequestManager.sendRequest(AudioPlayer.NAME_PLAYBACK_PROGRESS_SYNC, payload)
            }
        }
    }

    /**
     * 初始化各个端能力
     * @param alarm 本地闹钟端能力，传空则表示不处理本地闹钟
     * @param audioPlayer 音频播放端能力，包含远端闹钟音频播放
     * @param recognizer 录音识别端能力
     * @param screen 模板信息渲染端能力，在带屏设备上使用
     * @param speaker 扬声器控制端能力
     */
    fun init(
        alarm: Alarm?,
        appAction: AppAction?,
        audioPlayer: AudioPlayer,
        interceptor: Interceptor?,
        playbackController: PlaybackController?,
        recognizer: Recognizer,
        screen: Screen?,
        speaker: Speaker,
        system: System,
        template: Template?,
        videoPlayer: VideoPlayer?
    ) {
        this.audioPlayer?.removeListener(audioPlayerListener)

        this.alarm = alarm
        this.appAction = appAction
        this.audioPlayer = audioPlayer
        this.interceptor = interceptor
        this.playbackController = playbackController
        this.recognizer = recognizer
        this.screen = screen
        this.speaker = speaker
        this.system = system
        this.template = template
        this.videoPlayer = videoPlayer

        this.audioPlayer?.addListener(audioPlayerListener)
    }

    fun updateCurrentRequestId(requestId: String) {
        currentRequestId = requestId
    }

    fun initHandler(handler: Handler) {
        this.handler = handler
    }

    private fun startExecuteResponses() {
        val responses: MutableList<OsResponse>?
        synchronized(pendingExecuteResponses) {
            responses = pendingExecuteResponses[currentRequestId]
        }

        var markAsExecuteFinished = true

        if (responses?.isNotEmpty() == true) {
            val first = responses[0]
            val name = first.header.name
            val payload = first.payload

            when {
                name.startsWith(Constant.NAMESPACE_AUDIO_PLAYER) -> {
                    audioPlayer?.let { audioPlayer ->
                        if (name == AudioPlayer.NAME_AUDIO_OUT) {
                            val resourceId = payload.getString(AudioPlayer.KEY_RESOURCE_ID)
                            when (val type = payload.getString(AudioPlayer.KEY_TYPE)) {
                                AudioPlayer.TYPE_PLAYBACK -> {
                                    when (payload.getString(AudioPlayer.KEY_CONTROL)) {
                                        AudioPlayer.CONTROL_PLAY -> {
                                            val url = payload.getString(AudioPlayer.KEY_URL)
                                            when (payload.getString(AudioPlayer.KEY_BEHAVIOR)) {
                                                AudioPlayer.BEHAVIOR_IMMEDIATELY -> {
                                                    upcomingPlaybackResources.clear()
                                                    val offset = payload.getLongValue(AudioPlayer.KEY_OFFSET)
                                                    needSetOffsetResources[resourceId] = offset
                                                    audioPlayer.play(type, resourceId, url)
                                                }
                                                AudioPlayer.BEHAVIOR_UPCOMING -> {
                                                    upcomingPlaybackResources.add(payload)
                                                }
                                                else -> {

                                                }
                                            }
                                        }
                                        AudioPlayer.CONTROL_PAUSE -> ResponseProcessor.audioPlayer?.pause(type)
                                        AudioPlayer.CONTROL_RESUME -> ResponseProcessor.audioPlayer?.resume(type)
                                        else -> {
                                            // ignore
                                        }
                                    }
                                }
                                AudioPlayer.TYPE_TTS -> {
                                    markAsExecuteFinished = false
                                    val url = payload.getString(AudioPlayer.KEY_URL)
                                    audioPlayer.play(type, resourceId, url)

                                    val metadata = payload.getJSONObject(AudioPlayer.KEY_METADATA)
                                    val text = metadata.getString(AudioPlayer.KEY_TEXT)
                                    if (!text.isNullOrEmpty()) {
                                        audioPlayer.onTtsText(text)
                                    } else {
                                        // ignore
                                    }

                                    when (payload.getString(AudioPlayer.KEY_BEHAVIOR)) {
                                        AudioPlayer.BEHAVIOR_SERIAL -> {

                                        }
                                        AudioPlayer.BEHAVIOR_PARALLEL -> {
                                            markAsExecuteFinished = true
                                        }
                                        else -> {

                                        }
                                    }
                                }
                                AudioPlayer.TYPE_RING -> {
                                    val url = payload.getString(AudioPlayer.KEY_URL)
                                    audioPlayer.play(type, resourceId, url)
                                }
                                else -> {
                                    // ignore
                                }
                            }
                        } else {

                        }
                    }
                }
                name.startsWith(Constant.NAMESPACE_RECOGNIZER) -> {
                    recognizer?.let {
                        when (name) {
                            Recognizer.NAME_EXPECT_REPLY -> {
                                val replyKey = payload.getString(Recognizer.KEY_REPLY_KEY)
                                val backgroundRecognize = payload.getBoolean(Recognizer.KEY_BACKGROUND_RECOGNIZE)

                                it.isBackgroundRecognize = backgroundRecognize
                                it.expectReply(replyKey)
                            }
                            Recognizer.NAME_INTERMEDIATE_TEXT -> {
                                val text = payload.getString(Recognizer.KEY_TEXT)
                                it.onIntermediateText(text)
                            }
                            Recognizer.NAME_STOP_CAPTURE -> {
                                it.stopCapture()
                            }
                            else -> {

                            }
                        }
                    }
                }
                else -> {
                }
            }

            if (markAsExecuteFinished) {
                if (responses.isNotEmpty()) {
                    responses.removeAt(0)
                }

                startExecuteResponses()
            }
        } else {
            synchronized(pendingExecuteResponses) {
                pendingExecuteResponses.remove(currentRequestId)
                currentRequestId = ""
            }
        }
    }

    fun putResponses(json: String) {
        try {
            val responseBody = OsResponseBody.fromJSONObject(JSON.parseObject(json))
            val responses = responseBody.responses

            if (responses.isEmpty()) {
                // 如果没有响应则直接忽略以提高性能
                return
            }

            val osMeta = responseBody.meta
            val requestId = osMeta.requestId

            // 处理不需要阻塞音频焦点的指令
            var needExecuteCount = 0
            responses.map { response ->
                val name = response.header.name
                val payload = response.payload
                when {
                    name.startsWith(Constant.NAMESPACE_ALARM) -> {
                        // 响闹钟需要音频焦点，但是这个响应中只有设置闹钟和删除闹钟，故立即处理
                        alarm?.let { alarm ->
                            when (name) {
                                Alarm.NAME_SET_ALARM -> {
                                    val alarmId = payload.getString(Alarm.KEY_ALARM_ID)
                                    val timestamp = payload.getLongValue(Alarm.KEY_TIMESTAMP)
                                    val url = payload.getString(Alarm.KEY_URL)
                                    val alarmItem = Alarm.Item(alarmId, timestamp, url)
                                    alarm.setAlarm(alarmItem)
                                }
                                Alarm.NAME_DELETE_ALARM -> {
                                    val alarmId = payload.getString(Alarm.KEY_ALARM_ID)
                                    alarm.deleteAlarm(alarmId)
                                }
                                else -> {
                                    // ignore
                                }
                            }
                        }
                    }
                    name.startsWith(Constant.NAMESPACE_APP_ACTION) -> {
                        appAction?.let {
                            when (name) {
                                AppAction.NAME_CHECK -> {
                                    val checkResult: JSONObject = it.check(payload)
                                    RequestManager.sendRequest(AppAction.NAME_CHECK_RESULT, checkResult)
                                }
                                AppAction.NAME_EXECUTE -> {
                                    val result = JSONObject()
                                    if (it.execute(payload, result)) {
                                        RequestManager.sendRequest(AppAction.NAME_EXECUTE_SUCCEED, result)
                                    } else {
                                        RequestManager.sendRequest(AppAction.NAME_EXECUTE_FAILED, result)
                                    }
                                }
                                else -> {

                                }
                            }
                        }
                    }
                    name.startsWith(Constant.NAMESPACE_INTERCEPTOR) -> {
                        // 拦截器返回的 Custom 指令在 EVS 中转换为 interceptor 响应
                        interceptor?.onResponse(response.payload.toString())
                    }
                    name.startsWith(Constant.NAMESPACE_SCREEN) -> {
                        screen?.let {
                            when (name) {
                                Screen.NAME_TEMPLATE_OUT -> {
                                    when (payload.getString(Screen.KEY_TYPE)) {
                                        Screen.TYPE_PLAYER_INFO -> {
                                            val resourceId = payload.getString(AudioPlayer.KEY_RESOURCE_ID)
                                            it.notifyPlayerInfoUpdated(resourceId, payload.toString())
                                            if (playerInfoMap.containsKey(resourceId)) {
                                                playerInfoMap[resourceId] = payload
                                            } else {
                                                if (playerInfoMap.size > MAX_PLAYER_INFO_CACHE_SIZE) {
                                                    playerInfoMap.clear()
                                                }
                                                playerInfoMap[resourceId] = payload
                                            }
                                        }
                                        else -> {
                                            it.renderTemplate(payload.toString())
                                        }
                                    }
                                }
                            }
                        }
                    }
                    name.startsWith(Constant.NAMESPACE_TEMPLATE) -> {
                        template?.let {
                            when (name) {
                                Template.NAME_TEMPLATE_OUT -> {
                                    when (payload.getString(Template.KEY_TYPE)) {
                                        Template.TYPE_PLAYER_INFO -> {
                                            val resourceId = payload.getString(AudioPlayer.KEY_RESOURCE_ID)
                                            it.notifyPlayerInfoUpdated(resourceId, payload.toString())
                                            if (playerInfoMap.containsKey(resourceId)) {
                                                playerInfoMap[resourceId] = payload
                                            } else {
                                                if (playerInfoMap.size > MAX_PLAYER_INFO_CACHE_SIZE) {
                                                    playerInfoMap.clear()
                                                }
                                                playerInfoMap[resourceId] = payload
                                            }
                                        }
                                        else -> {
                                            it.renderTemplate(payload.toString())
                                        }
                                    }
                                }
                            }
                        }
                    }
                    name.startsWith(Constant.NAMESPACE_SPEAKER) -> {
                        speaker?.let {
                            when (name) {
                                Speaker.NAME_SET_VOLUME -> {
                                    val volume = payload.getIntValue(Speaker.KEY_VOLUME)
//                                    val type = payload.getString(Speaker.KEY_TYPE) 暂时未使用
                                    it.setVolume(volume)
                                    RequestManager.sendRequest(System.NAME_STATE_SYNC, JSONObject())
                                }
                                else -> {
                                    // ignore
                                }
                            }
                        }
                    }
                    name.startsWith(Constant.NAMESPACE_SYSTEM) -> {
                        system?.let {
                            when (name) {
                                System.NAME_PING -> {
                                    it.onPing(payload)
                                }
                                System.NAME_ERROR -> {
                                    it.onError(payload)
                                }
                                System.NAME_REVOKE_AUTHORIZATION -> {
                                    it.revokeAuth()
                                }
                            }
                        }
                    }
                    else -> {
                        // ignore
                        needExecuteCount++
                    }
                }
            }

            if (needExecuteCount > 0) {
                synchronized(pendingExecuteResponses) {
                    // 暂存所有待执行的响应
                    if (!requestId.isNullOrEmpty()) {
                        if (currentRequestId != requestId) {
                            currentRequestId = requestId
                        }
                        pendingExecuteResponses[requestId] = responses.toMutableList()
                    } else {
                        // 若不存在 requestId 则模拟一个
                        val hackRequestId = UUID.randomUUID().toString()
                        currentRequestId = hackRequestId
                        pendingExecuteResponses[hackRequestId] = responses.toMutableList()
                    }

                    // 删除原来未处理的responses，避免内存泄露
                    for (key in pendingExecuteResponses.keys) {
                        if (key != currentRequestId) {
                            pendingExecuteResponses.remove(key)
                        }
                    }
                }

                startExecuteResponses()
            }
        } catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }
    }

    fun destroy() {
        audioPlayer?.removeListener(audioPlayerListener)
    }
}