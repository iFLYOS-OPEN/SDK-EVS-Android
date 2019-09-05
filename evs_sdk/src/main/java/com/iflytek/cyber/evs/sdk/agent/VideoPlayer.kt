package com.iflytek.cyber.evs.sdk.agent

import com.iflytek.cyber.evs.sdk.model.Constant

abstract class VideoPlayer {
    val version = "1.0"

    companion object {
        const val NAME_PROGRESS_SYNC = "${Constant.NAMESPACE_VIDEO_PLAYER}.progress_sync"
        const val NAME_VIDEO_OUT = "${Constant.NAMESPACE_VIDEO_PLAYER}.video_out"

        const val TYPE_PLAYBACK = "PLAYBACK"
        const val TYPE_RING = "RING"
        const val TYPE_TTS = "TTS"

        const val SYNC_TYPE_STARTED = "STARTED"
        const val SYNC_TYPE_FINISHED = "FINISHED"
        const val SYNC_TYPE_FAILED = "FAILED"
        const val SYNC_TYPE_NEARLY_FINISHED = "NEARLY_FINISHED"

        const val STATE_IDLE = "IDLE"
        const val STATE_RUNNING = "RUNNING"
        const val STATE_PAUSED = "PAUSED"

        const val KEY_URL = "url"
        const val KEY_STATE = "state"
        const val KEY_RESOURCE_ID = "resource_id"
        const val KEY_OFFSET = "offset"
        const val KEY_CONTROL = "control"
        const val KEY_BEHAVIOR = "behavior"
        const val KEY_FAILURE_CODE = "failure_code"

        const val CONTROL_PLAY = "PLAY"
        const val CONTROL_PAUSE = "PAUSE"
        const val CONTROL_RESUME = "RESUME"
        const val CONTROL_EXIT = "EXIT"

        const val BEHAVIOR_IMMEDIATELY = "IMMEDIATELY"
        const val BEHAVIOR_UPCOMING = "UPCOMING"

        const val MEDIA_ERROR_UNKNOWN = "1001"                // 发生了未知错误
        const val MEDIA_ERROR_INVALID_REQUEST =
            "1002"        // 请求无效。可能的情况有：bad request, unauthorized, forbidden, not found等。
        const val MEDIA_ERROR_SERVICE_UNAVAILABLE = "1003"    // 设备端无法获取音频文件
        const val MEDIA_ERROR_INTERNAL_SERVER_ERROR = "1004"  // 服务端接收了请求但未能正确处理
        const val MEDIA_ERROR_INTERNAL_DEVICE_ERROR = "1005"  // 设备端内部错误
    }

    var state = STATE_IDLE
        private set
    var resourceId: String? = null
        private set

    private val listeners = HashSet<VideoStateChangedListener>()

    fun addListener(listener: VideoStateChangedListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: VideoStateChangedListener) {
        listeners.remove(listener)
    }

    abstract fun getOffset(): Long

    abstract fun getDuration(): Long

    abstract fun play(resourceId: String, url: String): Boolean

    abstract fun seekTo(offset: Long): Boolean

    abstract fun pause(): Boolean

    abstract fun resume(): Boolean

    abstract fun stop(): Boolean

    fun onStarted(resourceId: String) {
        listeners.map {
            try {
                it.onStarted(this, resourceId)
            } catch (_: Exception) {

            }
        }
    }

    fun onResumed(resourceId: String) {
        listeners.map {
            try {
                it.onResumed(this, resourceId)
            } catch (_: Exception) {

            }
        }
    }

    fun onPaused(resourceId: String) {
        listeners.map {
            try {
                it.onPaused(this, resourceId)
            } catch (_: Exception) {

            }
        }
    }

    fun onStopped(resourceId: String) {
        listeners.map {
            try {
                it.onStopped(this, resourceId)
            } catch (_: Exception) {

            }
        }
    }

    fun onCompleted(resourceId: String) {
        listeners.map {
            try {
                it.onCompleted(this, resourceId)
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

    fun onPositionUpdated(resourceId: String, position: Long) {
        listeners.map {
            try {
                it.onPositionUpdated(this, resourceId, position)
            } catch (_: Exception) {

            }
        }
    }

    fun onError(resourceId: String, errorCode: String) {

    }

    interface VideoStateChangedListener {
        fun onStarted(player: VideoPlayer, resourceId: String)
        fun onResumed(player: VideoPlayer, resourceId: String)
        fun onPaused(player: VideoPlayer, resourceId: String)
        fun onStopped(player: VideoPlayer, resourceId: String)
        fun onCompleted(player: VideoPlayer, resourceId: String)
        fun onPositionUpdated(player: VideoPlayer, resourceId: String, position: Long)
        fun onError(player: VideoPlayer, resourceId: String, errorCode: String)
    }
}