package com.iflytek.cyber.evs.sdk.agent

import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.annotation.JSONField
import com.iflytek.cyber.evs.sdk.RequestManager
import com.iflytek.cyber.evs.sdk.focus.AudioFocusManager
import com.iflytek.cyber.evs.sdk.model.Constant

abstract class Alarm {
    val version = "1.1"

    companion object {
        const val NAME_SET_ALARM = "${Constant.NAMESPACE_ALARM}.set_alarm"
        const val NAME_DELETE_ALARM = "${Constant.NAMESPACE_ALARM}.delete_alarm"
        const val NAME_STATE_SYNC = "${Constant.NAMESPACE_ALARM}.state_sync"

        const val KEY_ALARM_ID = "alarm_id"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_URL = "url"
        const val KEY_ACTIVE = "active"
        const val KEY_LOCAL = "local"
        const val KEY_TYPE = "type"
    }

    private var listeners = HashSet<AlarmStateChangedListener>()

    abstract fun getLocalAlarms(): List<Item>

    abstract fun getActiveAlarmId(): String?

    abstract fun setAlarm(alarm: Item)

    abstract fun deleteAlarm(alarmId: String)

    abstract fun stop()

    /**
     * 表示服务已被销毁
     */
    abstract fun destroy()

    @Suppress("unused")
    fun addListener(listener: AlarmStateChangedListener) {
        listeners.add(listener)
    }

    @Suppress("unused")
    fun removeListener(listener: AlarmStateChangedListener) {
        listeners.remove(listener)
    }

    fun onAlarmStarted(alarmId: String) {
        val payload = JSONObject()
        payload[KEY_TYPE] = "STARTED"
        payload[KEY_ALARM_ID] = alarmId
        RequestManager.sendRequest(NAME_STATE_SYNC, payload)

        AudioFocusManager.requestActive(AudioFocusManager.CHANNEL_ALARM, AudioFocusManager.TYPE_ALARM)

        onAlarmStateChanged(alarmId, AlarmState.Started)
    }

    fun onAlarmStopped(alarmId: String) {
        val payload = JSONObject()
        payload[KEY_TYPE] = "STOPPED"
        payload[KEY_ALARM_ID] = alarmId
        RequestManager.sendRequest(NAME_STATE_SYNC, payload)

        AudioFocusManager.requestAbandon(AudioFocusManager.CHANNEL_ALARM, AudioFocusManager.TYPE_ALARM)

        onAlarmStateChanged(alarmId, AlarmState.Started)
    }

    open fun onAlarmStateChanged(alarmId: String, state: AlarmState) {
        listeners.map {
            try {
                it.onAlarmStateChanged(alarmId, state)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    data class Item(
        @JSONField(name = KEY_ALARM_ID) val alarmId: String,
        val timestamp: Long,
        val url: String
    )

    interface AlarmStateChangedListener {
        fun onAlarmStateChanged(alarmId: String, state: AlarmState)
    }

    enum class AlarmState {
        Started,
        Stopped,
    }
}