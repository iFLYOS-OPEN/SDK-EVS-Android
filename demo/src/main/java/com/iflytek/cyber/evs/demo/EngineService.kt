package com.iflytek.cyber.evs.demo

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.iflytek.cyber.evs.demo.utils.DeviceUtils
import com.iflytek.cyber.evs.sdk.EvsService
import com.iflytek.cyber.evs.sdk.agent.AudioPlayer
import com.iflytek.cyber.evs.sdk.agent.Recognizer
import com.iflytek.cyber.evs.sdk.auth.AuthDelegate

class EngineService : EvsService() {
    private val binder = EngineServiceBinder()

    companion object {
        private const val TAG = "EngineService"

        private const val ACTION_PREFIX = "com.iflytek.cyber.evs.demo.action"

        const val ACTION_EVS_CONNECTED = "$ACTION_PREFIX.EVS_CONNECTED"
        const val ACTION_EVS_DISCONNECTED = "$ACTION_PREFIX.EVS_DISCONNECTED"
        const val ACTION_EVS_ERROR = "$ACTION_PREFIX.EVS_ERROR"
        const val ACTION_EVS_CONNECT_FAILED = "$ACTION_PREFIX.EVS_CONNECT_FAILED"
        const val ACTION_EVS_SEND_FAILED = "$ACTION_PREFIX.EVS_SEND_FAILED"
        const val ACTION_CLEAR_AUTH = "$ACTION_PREFIX.CLEAR_AUTH"

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
        override fun onBackgroundRecognizeStateChanged(isBackgroundRecognize: Boolean) {
            recognizerCallback?.onBackgroundRecognizeStateChanged(isBackgroundRecognize)
        }

        override fun onRecognizeStarted(isExpectReply: Boolean) {
            recognizerCallback?.onRecognizeStarted(isExpectReply)
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
    private val onPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                getString(R.string.key_list_codec) -> {
                    val formatStr =
                        sharedPreferences.getString(key, "RAW")
                    val format = when (formatStr) {
                        "SPEEX_WB_QUALITY_9" -> {
                            Recognizer.AudioCodecFormat.SPEEX_WB_QUALITY_9
                        }
                        "OPUS" -> {
                            Recognizer.AudioCodecFormat.OPUS
                        }
                        else -> {
                            Recognizer.AudioCodecFormat.AUDIO_L16_RATE_16000_CHANNELS_1
                        }
                    }

                    getRecognizer().setCodecFormat(format)
                }
                getString(R.string.key_custom_context_enabled) -> {
                    val enabled = sharedPreferences.getBoolean(key, false)
                    if (enabled) {
                        val context = sharedPreferences.getString(
                            getString(R.string.key_custom_context),
                            null
                        )
                        setCustomIflyosContext(context)
                    } else {
                        setCustomIflyosContext(null)
                    }
                }
                getString(R.string.key_custom_context) -> {
                    val context = sharedPreferences.getString(key, null)
                    setCustomIflyosContext(context)
                }
                getString(R.string.key_recognize_profile) -> {
                    val profile =
                        sharedPreferences.getString(key, Recognizer.Profile.CloseTalk.value)
                    getRecognizer().profile = if (profile == Recognizer.Profile.FarField.value) {
                        Recognizer.Profile.FarField
                    } else {
                        Recognizer.Profile.CloseTalk
                    }
                }
            }
        }

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

        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        pref.registerOnSharedPreferenceChangeListener(onPreferenceChangeListener)

        if (pref.getBoolean(getString(R.string.key_custom_context_enabled), false)) {
            val customContext = pref.getString(getString(R.string.key_custom_context), null)
            setCustomIflyosContext(customContext)
        }

        val profile =
            pref.getString(
                getString(R.string.key_recognize_profile),
                Recognizer.Profile.CloseTalk.value
            )
        getRecognizer().profile = if (profile == Recognizer.Profile.FarField.value) {
            Recognizer.Profile.FarField
        } else {
            Recognizer.Profile.CloseTalk
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLEAR_AUTH -> {
                disconnect()

                getAudioPlayer().apply {
                    stop(AudioPlayer.TYPE_TTS)
                    stop(AudioPlayer.TYPE_RING)
                    stop(AudioPlayer.TYPE_PLAYBACK)
                }

                getAlarm()?.apply {
                    stop()
                }

                AuthDelegate.removeAuthResponseFromPref(this)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        getRecognizer().removeRecognizerCallback()

        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(onPreferenceChangeListener)
    }

    override fun onEvsConnected() {
        super.onEvsConnected()

        sendBroadcast(Intent(ACTION_EVS_CONNECTED))
    }

    override fun onEvsDisconnected(code: Int, message: String?, fromRemote: Boolean) {
        super.onEvsDisconnected(code, message, fromRemote)

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

    @SuppressLint("HardwareIds")
    fun connectEvs() {
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val serverUrl =
            pref.getString(getString(R.string.key_evs_ws_url), getString(R.string.default_ws_url))
        val deviceId = pref.getString(
            getString(R.string.key_device_id),
            DeviceUtils.getDeviceId(this)
        ) ?: "unexpected_device_id"

        connect(serverUrl, deviceId)
    }

    override fun onResponsesRaw(json: String) {
        super.onResponsesRaw(json)

        transmissionListener?.onResponsesRaw(json)
    }

    override fun onRequestRaw(obj: Any) {
        super.onRequestRaw(obj)

        transmissionListener?.onRequestRaw(obj)
    }

    override fun onConnectFailed(t: Throwable?) {
        super.onConnectFailed(t)

        val intent = Intent(ACTION_EVS_CONNECT_FAILED)
        intent.putExtra(EXTRA_MESSAGE, t?.message)
        sendBroadcast(intent)
    }

    override fun overrideRecognizer(): Recognizer {
        return EvsRecoginzerImpl(this)
    }

    override fun onSendFailed(code: Int, reason: String?) {
        super.onSendFailed(code, reason)

        val intent = Intent(ACTION_EVS_SEND_FAILED)
        intent.putExtra(EXTRA_CODE, code)
        intent.putExtra(EXTRA_MESSAGE, reason)
        sendBroadcast(intent)
    }

    override fun onError(code: Int, message: String) {
        super.onError(code, message)

        val intent = Intent(ACTION_EVS_ERROR)
        intent.putExtra(EXTRA_CODE, code)
        intent.putExtra(EXTRA_MESSAGE, message)
        sendBroadcast(intent)
    }

    override fun isResponseSoundEnabled(): Boolean {
        return !PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.key_disable_response_sound), false)
    }

    interface TransmissionListener {
        fun onResponsesRaw(json: String)
        fun onRequestRaw(obj: Any)
    }
}