package com.iflytek.cyber.evs.sdk.socket

import android.os.Build
import java.nio.ByteBuffer

internal object SocketManager {
    private var webSocket: EvsWebSocket? = null

    init {
        webSocket = OkhttpWebSocket()
    }

    fun connect(serverUrl: String?, deviceId: String, token: String) {
        webSocket?.connect(serverUrl, deviceId, token)
    }

    fun send(message: String) {
        webSocket?.send(message)
    }

    fun send(message: ByteArray) {
        webSocket?.send(message)
    }

    fun send(message: ByteBuffer) {
        webSocket?.send(message)
    }

    fun disconnect() {
        webSocket?.disconnect()
    }

    fun addListener(listener: SocketListener) {
        webSocket?.addListener(listener)
    }

    fun removeListener(listener: SocketListener) {
        webSocket?.removeListener(listener)
    }

    interface SocketListener {
        fun onConnected()
        fun onDisconnected(code: Int, reason: String?, remote: Boolean)
        fun onMessage(message: String)
        fun onSend(message: Any)
    }

    abstract class EvsWebSocket {
        protected val onMessageListeners = HashSet<SocketListener>()

        abstract fun connect(serverUrl: String?, deviceId: String, token: String)
        abstract fun send(message: String)
        abstract fun send(message: ByteArray)
        abstract fun send(message: ByteBuffer)
        abstract fun disconnect()

        open fun addListener(listener: SocketListener) {
            synchronized(onMessageListeners) {
                onMessageListeners.add(listener)
            }
        }

        open fun removeListener(listener: SocketListener) {
            synchronized(onMessageListeners) {
                onMessageListeners.remove(listener)
            }
        }
    }
}