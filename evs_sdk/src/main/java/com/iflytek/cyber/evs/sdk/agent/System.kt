package com.iflytek.cyber.evs.sdk.agent

import com.alibaba.fastjson.JSONObject
import com.iflytek.cyber.evs.sdk.model.Constant

abstract class System() {
    val version = "1.0"

    companion object {
        const val NAME_PING = "${Constant.NAMESPACE_SYSTEM}.ping"
        const val NAME_ERROR = "${Constant.NAMESPACE_SYSTEM}.error"
        const val NAME_EXCEPTION = "${Constant.NAMESPACE_SYSTEM}.exception"
        const val NAME_STATE_SYNC = "${Constant.NAMESPACE_SYSTEM}.state_sync"
        const val NAME_REVOKE_AUTHORIZATION = "${Constant.NAMESPACE_SYSTEM}.revoke_authorization"

        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_TYPE = "type"
        const val KEY_CODE = "code"
        const val KEY_MESSAGE = "message"
    }

    abstract fun onPing(payload: JSONObject)

    abstract fun onError(payload: JSONObject)

    abstract fun revokeAuth()

    abstract fun sendStateSync()

    abstract fun sendException(type: String, code: String, message: String)
}