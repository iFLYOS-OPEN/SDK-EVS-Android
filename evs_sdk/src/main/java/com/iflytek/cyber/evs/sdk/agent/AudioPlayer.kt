package com.iflytek.cyber.evs.sdk.agent

import com.iflytek.cyber.evs.sdk.model.Constant
import com.iflytek.cyber.evs.sdk.utils.Log

abstract class AudioPlayer {
    val version
        get() = "1.1"

    companion object {
        const val NAME_PLAYBACK_PROGRESS_SYNC = "${Constant.NAMESPACE_AUDIO_PLAYER}.playback.progress_sync"
        const val NAME_RING_PROGRESS_SYNC = "${Constant.NAMESPACE_AUDIO_PLAYER}.ring.progress_sync"
        const val NAME_TTS_PROGRESS_SYNC = "${Constant.NAMESPACE_AUDIO_PLAYER}.tts.progress_sync"
        const val NAME_TTS_TEXT_IN = "${Constant.NAMESPACE_AUDIO_PLAYER}.tts.text_in"

        const val NAME_AUDIO_OUT = "${Constant.NAMESPACE_AUDIO_PLAYER}.audio_out"

        const val TYPE_PLAYBACK = "PLAYBACK"
        const val TYPE_RING = "RING"
        const val TYPE_TTS = "TTS"

        const val SYNC_TYPE_STARTED = "STARTED"
        const val SYNC_TYPE_FINISHED = "FINISHED"
        const val SYNC_TYPE_FAILED = "FAILED"
        const val SYNC_TYPE_NEARLY_FINISHED = "NEARLY_FINISHED"
        const val SYNC_TYPE_PAUSED = "PAUSED"
        const val SYNC_TYPE_STOPPED = "STOPPED"

        const val CONTROL_PLAY = "PLAY"
        const val CONTROL_PAUSE = "PAUSE"
        const val CONTROL_RESUME = "RESUME"

        const val KEY_TYPE = "type"
        const val KEY_URL = "url"
        const val KEY_CONTROL = "control"
        const val KEY_RESOURCE_ID = "resource_id"
        const val KEY_OFFSET = "offset"
        const val KEY_BEHAVIOR = "behavior"
        const val KEY_ERROR_CODE = "error_code"
        const val KEY_PLAYBACK = "playback"
        const val KEY_STATE = "state"
        const val KEY_TEXT = "text"
        const val KEY_METADATA = "metadata"

        const val BEHAVIOR_IMMEDIATELY = "IMMEDIATELY"
        const val BEHAVIOR_UPCOMING = "UPCOMING"
        const val BEHAVIOR_SERIAL = "SERIAL"
        const val BEHAVIOR_PARALLEL = "PARALLEL"

        const val PLAYBACK_STATE_IDLE = "IDLE"
        const val PLAYBACK_STATE_PLAYING = "PLAYING"
        const val PLAYBACK_STATE_PAUSED = "PAUSED"

        const val MEDIA_ERROR_UNKNOWN = "1001"                // 发生了未知错误
        const val MEDIA_ERROR_INVALID_REQUEST =
            "1002"        // 请求无效。可能的情况有：bad request, unauthorized, forbidden, not found等。
        const val MEDIA_ERROR_SERVICE_UNAVAILABLE = "1003"    // 设备端无法获取音频文件
        const val MEDIA_ERROR_INTERNAL_SERVER_ERROR = "1004"  // 服务端接收了请求但未能正确处理
        const val MEDIA_ERROR_INTERNAL_DEVICE_ERROR = "1005"  // 设备端内部错误

    }

    private val listeners = HashSet<MediaStateChangedListener>()

    var playbackResourceId: String? = null
        private set
    var playbackState = PLAYBACK_STATE_IDLE
        private set

    abstract fun play(type: String, resourceId: String, url: String): Boolean

    fun addListener(listener: MediaStateChangedListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: MediaStateChangedListener) {
        listeners.remove(listener)
    }

    abstract fun resume(type: String): Boolean

    abstract fun pause(type: String): Boolean

    abstract fun stop(type: String): Boolean

    abstract fun seekTo(type: String, offset: Long): Boolean

    abstract fun getOffset(type: String): Long

    abstract fun getDuration(type: String): Long

    abstract fun moveToForegroundIfAvailable(type: String): Boolean

    abstract fun moveToBackground(type: String): Boolean

    abstract fun sendTtsText(text: String)

    /**
     * 返回合成 TTS 的文本，并非纯文本，其中可能带有类似 `[di4]` 之类的标记符
     */
    open fun onTtsText(text: String) {}

    fun onStarted(type: String, resourceId: String) {
        if (type == TYPE_PLAYBACK) {
            playbackState = PLAYBACK_STATE_PLAYING
            playbackResourceId = resourceId

            Log.d("state_test", "onStarted")
        }
        listeners.map {
            try {
                it.onStarted(this, type, resourceId)
            } catch (_: Exception) {

            }
        }
    }

    fun onResumed(type: String, resourceId: String) {
        if (type == TYPE_PLAYBACK) {
            playbackState = PLAYBACK_STATE_PLAYING

            Log.d("state_test", "onResumed")
        }
        listeners.map {
            try {
                it.onResumed(this, type, resourceId)
            } catch (_: Exception) {

            }
        }
    }

    fun onPaused(type: String, resourceId: String) {
        if (type == TYPE_PLAYBACK) {
            playbackState = PLAYBACK_STATE_PAUSED

            Log.d("state_test", "onPaused")
        }
        listeners.map {
            try {
                it.onPaused(this, type, resourceId)
            } catch (_: Exception) {

            }
        }
    }

    fun onStopped(type: String, resourceId: String) {
        if (type == TYPE_PLAYBACK) {
            playbackState = PLAYBACK_STATE_PAUSED

            Log.d("state_test", "onStopped")
        }
        listeners.map {
            try {
                it.onStopped(this, type, resourceId)
            } catch (_: Exception) {

            }
        }
    }

    fun onCompleted(type: String, resourceId: String) {
        if (type == TYPE_PLAYBACK)
            playbackState = PLAYBACK_STATE_PAUSED
        listeners.map {
            try {
                it.onCompleted(this, type, resourceId)
            } catch (_: Exception) {

            }
        }
    }

    fun onPositionUpdated(type: String, resourceId: String, position: Long) {
        listeners.map {
            try {
                it.onPositionUpdated(this, type, resourceId, position)
            } catch (_: Exception) {

            }
        }
    }

    fun onError(type: String, resourceId: String, errorCode: String) {
        playbackState = PLAYBACK_STATE_PAUSED
        listeners.map {
            try {
                it.onError(this, type, resourceId, errorCode)
            } catch (_: Exception) {

            }
        }
    }

    fun onPressPlayOrPause() {

    }

    fun onPressPreious() {

    }

    fun onPressNext() {

    }

    interface MediaStateChangedListener {
        fun onStarted(player: AudioPlayer, type: String, resourceId: String)
        fun onResumed(player: AudioPlayer, type: String, resourceId: String)
        fun onPaused(player: AudioPlayer, type: String, resourceId: String)
        fun onStopped(player: AudioPlayer, type: String, resourceId: String)
        fun onCompleted(player: AudioPlayer, type: String, resourceId: String)
        fun onPositionUpdated(player: AudioPlayer, type: String, resourceId: String, position: Long)
        fun onError(player: AudioPlayer, type: String, resourceId: String, errorCode: String)
    }
}