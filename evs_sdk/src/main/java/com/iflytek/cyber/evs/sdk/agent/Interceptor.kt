package com.iflytek.cyber.evs.sdk.agent

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.iflytek.cyber.evs.sdk.RequestManager
import com.iflytek.cyber.evs.sdk.model.Constant

abstract class Interceptor {
    val version = "1.0"
    var contextJson = JSONObject()
        private set

    companion object {
        const val NAME_CUSTOM = "${Constant.NAMESPACE_INTERCEPTOR}.custom"
        const val NAME_AIUI = "${Constant.NAMESPACE_INTERCEPTOR}.aiui"
    }

    abstract fun onResponse(payload: String)

    /**
     * 更新 Context
     * @param json 要同步的 Context，请注意 json 中若有 version 字段则会被覆盖
     */
    fun updateContext(json: String) {
        val context = JSON.parseObject(json)
        context["version"] = version
        contextJson = context
    }

    /**
     * 请求发送 Interceptor 请求，对应 IVS 中的 Custom 事件
     * @param payload 请求的 payload
     */
    fun sendRequest(payload: String) {
        RequestManager.sendRequest(NAME_CUSTOM, JSON.parseObject(payload))
    }

}