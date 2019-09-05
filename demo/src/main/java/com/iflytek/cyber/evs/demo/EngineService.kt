package com.iflytek.cyber.evs.demo

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.iflytek.cyber.evs.sdk.EvsService
import com.iflytek.cyber.evs.sdk.agent.Recognizer

class EngineService : EvsService() {
    private val binder = EngineServiceBinder()

    companion object {
        private const val TAG = "EngineService"

        private const val ACTION_PREFIX = "com.iflytek.cyber.evs.demo.action"

        const val ACTION_EVS_CONNECTED = "$ACTION_PREFIX.EVS_CONNECTED"
        const val ACTION_EVS_DISCONNECTED = "$ACTION_PREFIX.EVS_DISCONNECTED"
        const val ACTION_EVS_ERROR = "$ACTION_PREFIX.EVS_ERROR"

        const val EXTRA_CODE = "code"
        const val EXTRA_MESSAGE = "message"
    }

    open inner class EngineServiceBinder : Binder() {
        fun getService(): EngineService {
            return this@EngineService
        }
    }

    private var transmissionListener: TransmissionListener? = null
    private val internalRecordCallback = object : Recognizer.RecognizerCallback {
        override fun onRecognizeStarted() {
            recognizerCallback?.onRecognizeStarted()
        }

        override fun onRecognizeStopped() {
            recognizerCallback?.onRecognizeStopped()
        }

        override fun onIntermediateText(text: String) {
            Log.d(TAG, "onIntermediateText($text)")
            recognizerCallback?.onIntermediateText(text)
        }
    }
    private var recognizerCallback: Recognizer.RecognizerCallback? = null

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun setTransmissionListener(listener: TransmissionListener?) {
        transmissionListener = listener
    }

    fun setRecognizerCallback(recognizerCallback: Recognizer.RecognizerCallback?) {
        this.recognizerCallback = recognizerCallback
    }

    override fun onCreate() {
        super.onCreate()

        getRecognizer().setRecognizerCallback(internalRecordCallback)
    }

    override fun onDestroy() {
        super.onDestroy()

        getRecognizer().removeRecognizerCallback()
    }

    override fun onEvsConnected() {
        super.onEvsConnected()

        sendBroadcast(Intent(ACTION_EVS_CONNECTED))
    }

    override fun onEvsDisconnected(code: Int, message: String?) {
        super.onEvsDisconnected(code, message)

        val intent = Intent(ACTION_EVS_DISCONNECTED)
        intent.putExtra(EXTRA_CODE, code)
        intent.putExtra(EXTRA_MESSAGE, message)
        sendBroadcast(intent)
    }

    fun sendAudioIn(replyKey: String? = null) {
        getRecognizer().sendAudioIn(replyKey)
    }

    fun sendTextIn(query: String, replyKey: String? = null) {
        getRecognizer().sendTextIn(query, replyKey)
    }

    fun sendTts(text: String) {
        getAudioPlayer().sendTtsText(text)
    }

    override fun onResponsesRaw(json: String) {
        super.onResponsesRaw(json)

        transmissionListener?.onResponsesRaw(json)
    }

    override fun onRequestRaw(obj: Any) {
        super.onRequestRaw(obj)

        transmissionListener?.onRequestRaw(obj)
    }

    override fun onError(code: Int, message: String) {
        super.onError(code, message)

        val intent = Intent(ACTION_EVS_ERROR)
        intent.putExtra(EXTRA_CODE, code)
        intent.putExtra(EXTRA_MESSAGE, message)
        sendBroadcast(intent)
    }

    interface TransmissionListener {
        fun onResponsesRaw(json: String)
        fun onRequestRaw(obj: Any)
    }
}