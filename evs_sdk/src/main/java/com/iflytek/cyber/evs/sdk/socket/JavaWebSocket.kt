package com.iflytek.cyber.evs.sdk.socket

import android.util.Log
import com.iflytek.cyber.evs.sdk.model.Constant
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer

/**
 * WebSocket realized by org.java_websocket.
 */
internal class JavaWebSocket : SocketManager.EvsWebSocket() {
    private val TAG = "JavaWebSocket"

    private var socket: SocketClient? = null

    override fun connect(serverUrl: String?, deviceId: String, token: String) {
        val url = Constant.getWebSocketUrl(serverUrl, deviceId, token)
        Log.d(TAG, "connect evs with url {$url}")

        socket?.close()

        socket = SocketClient(URI(url))
        socket?.connect()
        socket?.connectionLostTimeout = 0
    }

    override fun send(message: String) {
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
    }

    override fun send(message: ByteArray) {
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
    }

    override fun send(message: ByteBuffer) {
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
    }

    override fun disconnect() {
        socket?.close()
        socket = null
    }

    inner class SocketClient(serverUri: URI) : WebSocketClient(serverUri) {

        override fun onOpen(handshakedata: ServerHandshake?) {
            Log.d(TAG, "onOpen: {code: ${handshakedata?.httpStatus}, message: ${handshakedata?.httpStatusMessage}}")

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

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            Log.d(TAG, "onClose: {code: $code, reason: $reason, remote: $remote}")

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

        override fun onMessage(message: String?) {
            Log.d(TAG, "onMessage: $message")

            message ?: return
            synchronized(onMessageListeners) {
                onMessageListeners.map {
                    Thread {
                        try {
                            it.onMessage(message)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
        }

        override fun onError(ex: Exception?) {
            ex?.printStackTrace()
        }

    }
}