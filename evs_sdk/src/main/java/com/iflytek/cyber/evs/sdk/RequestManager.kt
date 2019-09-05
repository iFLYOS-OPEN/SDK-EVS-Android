package com.iflytek.cyber.evs.sdk

import android.os.Handler
import com.alibaba.fastjson.JSONObject
import com.iflytek.cyber.evs.sdk.socket.RequestBuilder
import com.iflytek.cyber.evs.sdk.socket.SocketManager
import java.nio.ByteBuffer

internal object RequestManager {
    private var handler: Handler? = null

    fun initHandler(handler: Handler?) {
        this.handler = handler
    }

    fun sendRequest(name: String, payload: JSONObject) {
        handler?.post {
            val requestBody = RequestBuilder.buildRequestBody(name, payload)

            val requestId = requestBody.request.header.requestId
            if (requestId.startsWith(RequestBuilder.PREFIX_REQUEST))
                ResponseProcessor.updateCurrentRequestId(requestId)

            SocketManager.send(requestBody.toString())
        } ?: run {
            throw IllegalStateException("Must init handler for RequestManager at first.")
        }
    }

    fun sendBinary(byteArray: ByteArray) {
        handler?.post {
            SocketManager.send(byteArray)
        }
    }

    fun sendBinary(byteBuffer: ByteBuffer) {
        handler?.post {
            SocketManager.send(byteBuffer)
        }
    }
}