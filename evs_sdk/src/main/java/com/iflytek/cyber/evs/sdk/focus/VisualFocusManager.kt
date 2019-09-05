package com.iflytek.cyber.evs.sdk.focus

import com.iflytek.cyber.evs.sdk.utils.Log

object VisualFocusManager {
    private const val TAG = "VisualFocusManager"

    const val CHANNEL_OVERLAY = "OVERLAY"
    const val CHANNEL_OVERLAY_TEMPLATE = "OVERLAY_TEMPLATE"
    const val CHANNEL_LAUNCHER_APP = "LAUNCHER_APP"
    const val CHANNEL_VIDEO_APP = "VIDEO_APP"
    const val CHANNEL_EXTERNAL_APP = "EXTERNAL_APP"

    const val TYPE_RECORDING = "OverlayRecording"
    const val TYPE_TTS_TEXT = "OverlayTtsText"
    const val TYPE_STATIC_TEMPLATE = "StaticTemplate"
    const val TYPE_PLAYING_TEMPLATE = "PlayingTemplate"
    const val TYPE_LAUNCHER_PAGE = "LauncherPage"
    const val TYPE_VIDEO_PLAYER = "VideoPlayer"
    const val TYPE_EXTERNAL_VIDEO_APP = "ExternalVideoApp"
    const val TYPE_APP_ACTION = "AppAction"

    private val latestForegroundMap = HashMap<String, String>()     // <channel, type>
    private val statusMap = HashMap<String, FocusStatus>()          // <channel, status>
    private var visualFocusObserver: VisualFocusObserver? = null

    // 优前级从高到低排列
    internal val sortedChannels = arrayOf(
        CHANNEL_OVERLAY,
        CHANNEL_OVERLAY_TEMPLATE,
        CHANNEL_LAUNCHER_APP,
        CHANNEL_VIDEO_APP,
        CHANNEL_EXTERNAL_APP
    )

    internal fun setFocusObserver(observerAudio: VisualFocusObserver) {
        this.visualFocusObserver = observerAudio
    }

    internal fun removeFocusObserver() {
        this.visualFocusObserver = null
    }

    fun requestActive(activeChannel: String, type: String) {
        // 若请求的channel和type处于非前景状态
        if (statusMap[activeChannel] != FocusStatus.Foreground ||
            type != latestForegroundMap[activeChannel]
        ) {
            var findTarget = false
            for (i in 0 until sortedChannels.size) {
                val channel = sortedChannels[i]

                if (statusMap[channel] == FocusStatus.Foreground &&
                    channel != activeChannel
                ) {
                    if (channel == CHANNEL_OVERLAY || channel == CHANNEL_OVERLAY_TEMPLATE) {
                        if (latestForegroundMap[channel] == TYPE_STATIC_TEMPLATE) {
                            statusMap[channel] = FocusStatus.Background
                        } else {
                            statusMap[channel] = FocusStatus.Idle
                        }
                    } else {
                        if (latestForegroundMap[channel] == TYPE_LAUNCHER_PAGE) {
                            statusMap[channel] = FocusStatus.Idle
                        } else {
                            statusMap[channel] = FocusStatus.Background
                        }
                    }

                    onInternalFocusChanged(channel)

//                    if (findTarget) {
//                        // 当前前景通道优先级低，channel < activeChannel
//                        if (channel == CHANNEL_VIDEO_APP) {
//                            statusMap[channel] = FocusStatus.Background
//                            onInternalFocusChanged(channel)
//                        } else {
//                            // 没有比 VIDEO 优先级更低的通道
//                        }
//                    } else {
//                        // 当前前景通道优先级高，channel > activeChannel
//                        if (activeChannel == CHANNEL_VIDEO_APP) {
//                            if (channel == CHANNEL_OVERLAY_TEMPLATE) {
//                                statusMap[channel] = FocusStatus.Idle
//                                onInternalFocusChanged(channel)
//                            }
//                        } else {
//                            // 没有比 VIDEO 优先级更低的通道
//                        }
//                    }
                } else if (channel == activeChannel) {
                    findTarget = true
                    if (statusMap[activeChannel] != FocusStatus.Foreground) {
                        statusMap[activeChannel] = FocusStatus.Foreground
                    } else {
                        // 当前channel为前景，但非当前type活跃
                        if (latestForegroundMap[channel] != type) {
                            // 之前type对应的status变成idle
                            statusMap[channel] = FocusStatus.Idle
                            onInternalFocusChanged(channel)

                            statusMap[activeChannel] = FocusStatus.Foreground
                        }
                    }
                }
            }
            // 保证在可置为背景的前景通道通知完后，才通知想要激活的通道
            latestForegroundMap[activeChannel] = type
            onInternalFocusChanged(activeChannel)
        }
    }

    fun requestAbandon(abandonChannel: String, type: String) {
        when {
            latestForegroundMap[abandonChannel] != type ->
                Log.w(TAG, "Target type: $type is already abandoned, ignore this operation.")
            statusMap[abandonChannel] != FocusStatus.Idle -> {
                // 将状态置为丢失焦点
                statusMap[abandonChannel] = FocusStatus.Idle
                onInternalFocusChanged(abandonChannel)
                latestForegroundMap.remove(abandonChannel)
                for (i in 0 until sortedChannels.size) {
                    val channel = sortedChannels[i]
                    if (statusMap[channel] == FocusStatus.Background) {
                        // 将某个背景的 channel 置为前景
                        statusMap[channel] = FocusStatus.Foreground
                        onInternalFocusChanged(channel)
                        return
                    }
                }
            }
        }
    }

    private fun onInternalFocusChanged(channel: String) {
        try {
            visualFocusObserver?.onVisualFocusChanged(channel, latestForegroundMap[channel]!!, statusMap[channel]!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isManageableChannel(channel: String) = sortedChannels.contains(channel)

    interface VisualFocusObserver {
        fun onVisualFocusChanged(channel: String, type: String, status: FocusStatus)
    }
}