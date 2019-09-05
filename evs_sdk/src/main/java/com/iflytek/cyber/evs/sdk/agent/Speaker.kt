package com.iflytek.cyber.evs.sdk.agent

abstract class Speaker {
    val version = "1.0"

    companion object {
        const val NAME_SET_VOLUME = "speaker.set_volume"

        const val KEY_TYPE = "type"
        const val KEY_VOLUME = "volume"
    }

    /**
     * 返回音量类型
     * @return 应返回 absolute 或 percent，默认为 percent。（暂时 type 仅为 percent）
     */
    fun getType() = "percent"

    /**
     * 请求获取当前的音量
     * @return 若 type 为 percent，应返回 [0.100] 区间的音量。（暂时 type 仅为 percent）
     */
    abstract fun getCurrentVolume(): Int

    /**
     * 设置音量
     * @param volume 当 type 为 percent 时，volume 取值为 [0,100]。（暂时 type 仅为 percent）
     * @return 设置成功则返回 true，否则返回 false
     */
    abstract fun setVolume(volume: Int): Boolean
}