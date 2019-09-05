package com.iflytek.cyber.evs.sdk.socket

import android.util.Log
import com.iflytek.cyber.evs.sdk.model.Constant
import okhttp3.*
import okio.ByteString
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

/**
 * WebSocket realized by okhttp3.
 */
internal class OkhttpWebSocket : SocketManager.EvsWebSocket() {
    private val TAG = "OkhttpWebSocket"

    private var socket: WebSocket? = null

    companion object {
        const val NORMAL_CLOSE_CODE = 1000
        const val CLOSE_NO_STATUS_CODE = 1005

        const val MESSAGE_NORMAL_CLOSE = "Normal close"
        const val MESSAGE_REMOTE_CLOSE = "Remote close"
    }

    override fun connect(serverUrl: String?, deviceId: String, token: String) {
        val url = Constant.getWebSocketUrl(serverUrl, deviceId, token)
        Log.d(TAG, "connect evs with url {$url}")

        socket?.cancel()
        socket = null

        var request = Request.Builder().url(url).build()
        listener = InnerListener()

        if (url.startsWith("wss")) {
            OkHttpClient.Builder()
                .sslSocketFactory(createSSLSocketFactory())
                .connectTimeout(3, TimeUnit.SECONDS).build()
                .newWebSocket(request, listener)
        } else {
            OkHttpClient.Builder()
                .socketFactory(SocketFactory.getDefault())
                .connectTimeout(3, TimeUnit.SECONDS).build()
                .newWebSocket(request, listener)
        }
    }

    private fun createSSLSocketFactory(): SSLSocketFactory? {
        val context = SSLContext.getInstance("TLSv1.2")
        context.init(null, null, SecureRandom())

        return context.socketFactory
    }

    override fun send(message: String) {
        if (socket != null) {
            Log.d(TAG, "socket send: $message")

            socket?.send(message)
            synchronized(onMessageListeners) {
                onMessageListeners.map {
                    Thread {
                        try {
                            it.onSend(message)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
        } else {
            Log.e(TAG, "send $message failed, socket is null.")
        }
    }

    override fun send(message: ByteArray) {
        if (socket != null) {
            socket?.send(ByteString.of(message, 0, message.size))
            synchronized(onMessageListeners) {
                onMessageListeners.map {
                    Thread {
                        try {
                            it.onSend(message)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
        } else {
            Log.e(TAG, "send failed, socket is null.")
        }
    }

    override fun send(message: ByteBuffer) {
        if (socket != null) {
            socket?.send(ByteString.of(message))
            synchronized(onMessageListeners) {
                onMessageListeners.map {
                    Thread {
                        try {
                            it.onSend(message)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
        } else {
            Log.e(TAG, "send failed, socket is null.")
        }
    }

    override fun disconnect() {
        socket?.close(NORMAL_CLOSE_CODE, MESSAGE_NORMAL_CLOSE)
        socket = null
    }

    private var listener: WebSocketListener? = null

    inner class InnerListener : WebSocketListener() {
        private fun notifyClosed(code: Int, reason: String, remote: Boolean) {
            synchronized(onMessageListeners) {
                onMessageListeners.map {
                    Thread {
                        try {
                            it.onDisconnected(code, reason, remote)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)

            Log.d(TAG, "onOpen: {code: ${response?.code()}, message: ${response?.message()}}")

            socket = webSocket

            synchronized(onMessageListeners) {
                onMessageListeners.map {
                    Thread {
                        try {
                            it.onConnected()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)

            Log.d(TAG, "onMessage: $text")

            text ?: return
            synchronized(onMessageListeners) {
                onMessageListeners.map {
                    Thread {
                        try {
                            it.onMessage(text)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)

            disconnect()

            if (CLOSE_NO_STATUS_CODE == code) {
                Log.d(TAG, "onClosing: {code: $code, reason: $MESSAGE_REMOTE_CLOSE}")

                notifyClosed(code, MESSAGE_REMOTE_CLOSE, true)
            } else {
                Log.d(TAG, "onClosing: {code: $code, reason: $reason}")

                notifyClosed(code, reason, false)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)

            Log.w(TAG, "onFailure, code=${response?.code()}, reason=${t.message}")

            t?.printStackTrace()
            synchronized(onMessageListeners) {
                onMessageListeners.map {
                    Thread {
                        try {
                            val code = response?.code() ?: -1
                            it.onDisconnected(code, t.message, response != null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
        }
    }
}