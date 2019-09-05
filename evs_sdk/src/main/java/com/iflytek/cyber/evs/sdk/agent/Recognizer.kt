package com.iflytek.cyber.evs.sdk.agent

import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.annotation.CallSuper
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.iflytek.cyber.evs.sdk.RequestManager
import com.iflytek.cyber.evs.sdk.focus.AudioFocusManager
import com.iflytek.cyber.evs.sdk.model.Constant
import com.iflytek.cyber.evs.sdk.socket.SocketManager

/**
 * 语音识别能力抽象类
 *
 * 在此类中需要实现录音的基本功能
 *
 * 在设备接入带麦克风外设时，需要
 */
abstract class Recognizer {
    val version = "1.0"

    companion object {
        const val NAME_TEXT_IN = "${Constant.NAMESPACE_RECOGNIZER}.text_in"
        const val NAME_AUDIO_IN = "${Constant.NAMESPACE_RECOGNIZER}.audio_in"
        const val NAME_EXPECT_REPLY = "${Constant.NAMESPACE_RECOGNIZER}.expect_reply"
        const val NAME_STOP_CAPTURE = "${Constant.NAMESPACE_RECOGNIZER}.stop_capture"
        const val NAME_INTERMEDIATE_TEXT = "${Constant.NAMESPACE_RECOGNIZER}.intermediate_text"

        internal const val KEY_QUERY = "query"
        internal const val KEY_TEXT = "text"
        internal const val KEY_PROFILE = "profile"
        internal const val KEY_FORMAT = "format"
        internal const val KEY_REPLY_KEY = "reply_key"
        internal const val KEY_ENABLE_VAD = "enable_vad"
        internal const val KEY_WAKE_UP = "iflyos_wake_up"
        internal const val KEY_BACKGROUND_RECOGNIZE = "background_recognize"

        /**
         * 获取 EVS 支持的 SampleRate
         */
        fun getSampleRateInHz() = 16000

        /**
         * 获取 EVS 支持的 AudioFormat
         */
        fun getAudioFormatEncoding() = AudioFormat.ENCODING_PCM_16BIT

        /**
         * 获取 EVS 支持的通道数
         */
        fun getAudioChannel() = AudioFormat.CHANNEL_IN_MONO
    }

    private var recorderThread: RecorderThread? = null

    @Suppress("MemberVisibilityCanBePrivate")
    var profile = Profile.CloseTalk
    @Suppress("MemberVisibilityCanBePrivate")
    var isSupportOpus = false
    @Suppress("MemberVisibilityCanBePrivate")
    var isLocalVad = false

    var isBackgroundRecognize = false

    private var callback: RecognizerCallback? = null

    /**
     * 读取录音数据。SDK 调用 [startRecording] 之后就会开始尝试调用此函数读取录音数据
     * @param byteArray 数组长度
     * @param length 一次读取的长度
     * @return 返回读取的长度
     */
    abstract fun readBytes(byteArray: ByteArray, length: Int): Int

    fun write(byteArray: ByteArray, length: Int) {
        if (isWrittenByYourself() && length > 0) {
            RequestManager.sendBinary(byteArray.copyOf(length))
        }
    }

    /**
     * 请求开始录音
     */
    abstract fun startRecording()

    /**
     * 请求结束录音
     */
    abstract fun stopRecording()

    /**
     * 识别文本实时返回结果
     * @param text 识别文本
     */
    @CallSuper
    open fun onIntermediateText(text: String) {
        callback?.onIntermediateText(text)
    }

    @Suppress("unused")
    fun setRecognizerCallback(callback: RecognizerCallback) {
        this.callback = callback
    }

    @Suppress("unused")
    fun removeRecognizerCallback() {
        this.callback = null
    }

    /**
     * 获取音频输入源。复写以返回自定义的输入源
     */
    open fun getAudioSource(): Int {
        return MediaRecorder.AudioSource.DEFAULT
    }

    /**
     * 销毁时调用此函数
     */
    open fun onDestroy() {}

    /**
     * 请求取消语音识别
     */
    @Suppress("MemberVisibilityCanBePrivate", "unused")
    fun requestCancel() {
        stopRecording()

        recorderThread?.let {
            SocketManager.send("__CANCEL__")
            it.stopNow()
        }
        recorderThread = null

        stopRecording()

        AudioFocusManager.requestAbandon(AudioFocusManager.CHANNEL_INPUT, AudioFocusManager.TYPE_RECOGNIZE)
    }

    open fun isWrittenByYourself() = false

    /**
     * 请求结束语音识别
     */
    @Suppress("MemberVisibilityCanBePrivate", "unused")
    fun requestEnd() {
        stopRecording()

        recorderThread?.let {
            SocketManager.send("__END__")
            it.stopNow()
        }
        recorderThread = null

        stopRecording()

        if (!isBackgroundRecognize) {
            Handler(Looper.getMainLooper()).post {
                AudioFocusManager.requestAbandon(AudioFocusManager.CHANNEL_INPUT, AudioFocusManager.TYPE_RECOGNIZE)
            }
        }

        isBackgroundRecognize = false
    }

    private fun generatePayload(replyKey: String? = null): JSONObject {
        val payload = JSONObject()
        if (profile == Profile.CloseTalk) {
            payload[KEY_PROFILE] = "CLOSE_TALK"
        } else {
            payload[KEY_PROFILE] = "FAR_FIELD"
        }
        if (isSupportOpus) {
            payload[KEY_FORMAT] = "AUDIO_OPUS_RATE_16000_CHANNELS_1"
        } else {
            payload[KEY_FORMAT] = "AUDIO_L16_RATE_16000_CHANNELS_1"
        }
        if (!replyKey.isNullOrEmpty()) {
            payload[KEY_REPLY_KEY] = replyKey
        }
        payload[KEY_ENABLE_VAD] = !isLocalVad

        return payload
    }

    fun expectReply(replyKey: String) {
        val payload = generatePayload(replyKey)

        RequestManager.sendRequest(NAME_AUDIO_IN, payload)

        recorderThread?.stopNow()

        recorderThread = RecorderThread(this)
        recorderThread?.start()

        startRecording()

        if (!isBackgroundRecognize) {
            AudioFocusManager.requestActive(AudioFocusManager.CHANNEL_INPUT, AudioFocusManager.TYPE_RECOGNIZE)
        }
    }

    fun sendAudioIn(replyKey: String?, wakeUpData: String? = null) {
        val payload = generatePayload()

        if (!replyKey.isNullOrEmpty()) {
            payload[KEY_REPLY_KEY] = replyKey
        }

        wakeUpData?.let {
            val json = JSON.parseObject(it)
            payload[KEY_WAKE_UP] = json
        }

        RequestManager.sendRequest(NAME_AUDIO_IN, payload)

        recorderThread?.stopNow()

        recorderThread = RecorderThread(this)
        recorderThread?.start()

        startRecording()

        AudioFocusManager.requestActive(AudioFocusManager.CHANNEL_INPUT, AudioFocusManager.TYPE_RECOGNIZE)
    }

    fun sendTextIn(query: String, replyKey: String? = null) {
        if (query.isNotEmpty()) {
            val payload = JSONObject()
            payload[KEY_QUERY] = query

            if (!replyKey.isNullOrEmpty()) {
                payload[KEY_REPLY_KEY] = replyKey
            }

            RequestManager.sendRequest(NAME_TEXT_IN, payload)
        }
    }

    fun stopCapture() {
        requestEnd()
    }

    private class RecorderThread(val recognizer: Recognizer) : Thread() {
        private var needProcess = true

        init {
            recognizer.callback?.onRecognizeStarted()
        }

        override fun run() {
            super.run()

            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            val array = ByteArray(1024)
            while (needProcess) {
                try {
                    if (!recognizer.isWrittenByYourself()) {
                        val readSize = recognizer.readBytes(array, array.size)

                        if (readSize > 0) {
                            RequestManager.sendBinary(array.copyOf(readSize))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun stopNow() {
            needProcess = false

            recognizer.callback?.onRecognizeStopped()

            interrupt()
        }
    }

    interface RecognizerCallback {
        fun onRecognizeStarted()
        fun onRecognizeStopped()
        fun onIntermediateText(text: String)
    }

    enum class Profile {
        CloseTalk,
        FarField
    }
}