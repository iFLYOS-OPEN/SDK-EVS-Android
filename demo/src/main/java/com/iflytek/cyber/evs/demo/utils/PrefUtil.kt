package com.iflytek.cyber.evs.demo.utils

import android.content.Context


object PrefUtil {
    private val PREF_NAME = "com.iflytek.cyber.evs.demo.pref"

    fun setToPref(context : Context,
                  key : String,
                  value : String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().run {
            putString(key, value)
            apply()
        }
    }

    fun getFromPref(context: Context,
                    key : String) : String {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (pref.contains(key)) {
            return pref.getString(key, null)
        }
        return ""
    }
}