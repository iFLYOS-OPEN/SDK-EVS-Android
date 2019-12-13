package com.iflytek.cyber.evs.demo

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Build
import androidx.preference.PreferenceManager
import com.iflytek.cyber.evs.sdk.agent.Recognizer

class EvsRecoginzerImpl(private val context: Context) : Recognizer() {

    private var recorder: AudioRecord? = null
    private val pref = PreferenceManager.getDefaultSharedPreferences(context)

    init {

    }

    private fun createAudioRecord() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(getAudioFormatEncoding())
                    .setSampleRate(getSampleRateInHz())
                    .setChannelMask(getAudioChannel())
                    .build()
            )
            .setAudioSource(getAudioSource())
            .setBufferSizeInBytes(
                AudioRecord.getMinBufferSize(
                    getSampleRateInHz(),
                    getAudioChannel(),
                    getAudioFormatEncoding()
                )
            )
            .build()
    } else {
        AudioRecord(
            getAudioSource(),
            getSampleRateInHz(),
            getAudioChannel(),
            getAudioFormatEncoding(),
            AudioRecord.getMinBufferSize(
                getSampleRateInHz(),
                getAudioChannel(),
                getAudioFormatEncoding()
            )
        )
    }

    override fun isSupportBackgroundRecognize(): Boolean {
        return pref.getBoolean(context.getString(R.string.key_background_recognize), false)
    }

    override fun readBytes(byteArray: ByteArray, length: Int): Int {
        if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return recorder?.read(byteArray, 0, length) ?: -1
        }
        return -1
    }

    override fun startRecording() {
        if (recorder == null) {
            recorder = createAudioRecord()
        }
        recorder?.startRecording()
    }

    override fun stopRecording() {
        recorder?.stop()
        recorder = null
    }

    override fun isRecording(): Boolean {
        return recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            recorder?.release()
        } catch (e: Exception) {

        }
    }
}