package com.iflytek.cyber.evs.sdk.agent

import com.alibaba.fastjson.JSONObject
import com.iflytek.cyber.evs.sdk.RequestManager

/**
 * 播放控制端能力
 *
 * 仅当设备无法自行控制时才需要使用该能力
 */
abstract class PlaybackController {
    val version = "1.0"

    companion object {
        const val NAME_CONTROL_COMMAND = "playback_controller.control_command"

        const val KEY_TYPE = "type"
    }

    fun sendCommand(command: Command) {
        val payload = JSONObject()
        when (command) {
            Command.Exit -> payload[KEY_TYPE] = "EXIT"
            Command.Next -> payload[KEY_TYPE] = "NEXT"
            Command.Pause -> payload[KEY_TYPE] = "PAUSE"
            Command.Previous -> payload[KEY_TYPE] = "PREVIOUS"
            Command.Resume -> payload[KEY_TYPE] = "RESUME"
        }
        RequestManager.sendRequest(NAME_CONTROL_COMMAND, payload)
    }

    enum class Command {
        Exit,
        Next,
        Pause,
        Previous,
        Resume,
    }
}