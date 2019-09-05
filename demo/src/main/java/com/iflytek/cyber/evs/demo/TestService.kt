package com.iflytek.cyber.evs.demo

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.iflytek.cyber.evs.sdk.utils.AppUtil
import java.lang.Exception

class TestService: Service() {
    override fun onCreate() {
        super.onCreate()

        object : Thread() {
            override fun run() {
                super.run()

                while (true) {
                    val curTime = System.currentTimeMillis()
                    val foreApp = AppUtil.getForegroundApp(this@TestService)
                    val spent = System.currentTimeMillis() - curTime

                    if (foreApp != null) {
                        Log.d("foreApp", "spent=${spent}ms, pkg=${foreApp.pkgName}, activity=${foreApp.curActivity}")
                    } else {
                        Log.d("foreApp", "null")
                    }

                    try {
                        sleep(1000)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}