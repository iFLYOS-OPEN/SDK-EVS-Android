package com.iflytek.cyber.evs.sdk.agent

import com.iflytek.cyber.evs.sdk.model.Constant

abstract class Screen {

    val version = "1.0"

    companion object {
        const val NAME_SET_BRIGHTNESS = "${Constant.NAMESPACE_SCREEN}.set_brightness"
        const val NAME_TEMPLATE_OUT = "${Constant.NAMESPACE_SCREEN}.template_out"

        const val TYPE_PLAYER_INFO = "player_info_template"
        const val TYPE_BODY_1 = "body_template_1"
        const val TYPE_BODY_2 = "body_template_2"
        const val TYPE_BODY_3 = "body_template_3"
        const val TYPE_LIST_1 = "list_template_1"
        const val TYPE_OPTION_2 = "option_template_2"
        const val TYPE_OPTION_3 = "option_template_3"
        const val TYPE_WEATHER = "weather_template"

        const val KEY_TYPE = "type"
    }


    /**
     * 请求渲染模板
     */
    abstract fun renderTemplate(payload: String)

    /**
     * 请求取消渲染模板
     */
    abstract fun clearTemplate(payload: String)

    /**
     * 通知播放信息已更新，但未必是已经需要渲染。一般情况下只有当 [renderPlayerInfo] 调用时需要开发者在 UI 上渲染播放信息
     */
    abstract fun notifyPlayerInfoUpdated(resourceId: String, payload: String)

    /**
     * 请求渲染播放信息模板
     */
    abstract fun renderPlayerInfo(payload: String)

    /**
     * 请求清除播放信息
     */
    abstract fun clearPlayerInfo()

    /**
     * 模板是否常驻
     *
     * @return true 代表常驻，SDK 不会调用 [clearTemplate]
     */
    fun isTemplatePermanent() = false

    /**
     * 播放信息是否常驻
     *
     * @return true 代表常驻，SDK 不会调用 [clearPlayerInfo]
     */
    fun isPlayerInfoPermanent() = false
}