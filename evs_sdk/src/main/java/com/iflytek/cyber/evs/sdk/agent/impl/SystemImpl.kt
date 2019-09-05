package com.iflytek.cyber.evs.sdk.agent.impl

import android.content.Context
import com.alibaba.fastjson.JSONObject
import com.iflytek.cyber.evs.sdk.EvsService
import com.iflytek.cyber.evs.sdk.RequestManager
import com.iflytek.cyber.evs.sdk.agent.System
import com.iflytek.cyber.evs.sdk.auth.AuthDelegate

class SystemImpl(private val context: Context) : System() {

    override fun onPing(payload: JSONObject) {

    }

    override fun onError(payload: JSONObject) {
        val code = payload.getIntValue(KEY_CODE)
        val message = payload.getString(KEY_MESSAGE)

        (context as EvsService).onError(code, message)
    }

    override fun sendException(type: String, code: String, message: String) {
        val payload = JSONObject()
        payload[KEY_TYPE] = type
        payload[KEY_CODE] = code
        payload[KEY_MESSAGE] = message

        RequestManager.sendRequest(NAME_EXCEPTION, payload)
    }

    override fun sendStateSync() {
        RequestManager.sendRequest(NAME_STATE_SYNC, JSONObject())
    }

    override fun revokeAuth() {
        AuthDelegate.removeAuthResponseFromPref(context)
        (context as EvsService).disconnect()
    }

}