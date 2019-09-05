package com.iflytek.cyber.evs.sdk.auth

import android.content.Context
import android.content.SharedPreferences
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.iflytek.cyber.evs.sdk.model.AuthResponse
import com.iflytek.cyber.evs.sdk.model.DeviceCodeResponse
import com.iflytek.cyber.evs.sdk.utils.Log
import okhttp3.*

object AuthDelegate {
    private const val TAG = "AuthDelegate"

    private const val PREF_NAME = "com.iflytek.cyber.evs.sdk.auth.pref"
    const val PREF_KEY = "token"

    private var AUTH_URL = "https://auth.iflyos.cn"
    private var AUTH_URL_DEVICE_CODE = "$AUTH_URL/oauth/ivs/device_code"
    private var AUTH_URL_TOKEN = "$AUTH_URL/oauth/ivs/token"

    private const val GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code"
    private const val GRANT_TYPE_REFRESH = "refresh_token"

    private const val KEY_USER_IVS_ALL = "user_ivs_all"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_CODE = "device_code"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_GRANT_TYPE = "grant_type"
    private const val KEY_ERROR = "error"

    private const val ERROR_AUTHORIZATION_PENDING = "authorization_pending"
    private const val ERROR_EXPIRED_TOKEN = "expired_token"
    private const val ERROR_ACCESS_DENIED = "access_denied"

    private var httpClient: OkHttpClient? = null

    private val requestCache = HashSet<Thread>()

    fun setAuthUrl(url: String?) {
        if (!url.isNullOrEmpty()) {
            AUTH_URL = url
            AUTH_URL_DEVICE_CODE = "$AUTH_URL/oauth/ivs/device_code"
            AUTH_URL_TOKEN = "$AUTH_URL/oauth/ivs/token"
        }
    }

    fun getAuthResponseFromPref(context: Context): AuthResponse? {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (pref.contains(PREF_KEY)) {
            pref.getString(PREF_KEY, null)?.let { json ->
                return JSON.parseObject(json, AuthResponse::class.java)
            }
        }
        return null
    }

    fun removeAuthResponseFromPref(context: Context) {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (pref.contains(PREF_KEY)) {
            pref.edit().remove(PREF_KEY).commit()
        }
    }

    fun setAuthResponseToPref(context: Context, authResponse: AuthResponse) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().run {
            putString(PREF_KEY, JSON.toJSONString(authResponse))
            apply()
        }
    }

    fun registerTokenChangedListener(context: Context,
             listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterTokenChangedListener(context: Context,
                                     listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    fun requestDeviceCode(
        context: Context,
        clientId: String,
        deviceId: String,
        responseCallback: ResponseCallback<DeviceCodeResponse>,
        authResponseCallback: AuthResponseCallback? = null,
        customScopeData: String = KEY_USER_IVS_ALL
    ) {
        cancelPolling()

        if (httpClient == null) {
            httpClient = createHttpClient()
        }
        httpClient?.let { httpClient ->
            Thread {
                try {
                    val scopeData = JSONObject()
                    val userIvsAll = JSONObject()
                    userIvsAll[KEY_DEVICE_ID] = deviceId
                    scopeData[KEY_USER_IVS_ALL] = userIvsAll

                    val requestBody =
                        "client_id=$clientId&scope=$customScopeData&scope_data=${scopeData.toJSONString()}"

                    Log.d(TAG, requestBody)

                    val request = Request.Builder()
                        .url(AUTH_URL_DEVICE_CODE)
                        .post(
                            RequestBody.create(
                                MediaType.get("application/x-www-form-urlencoded"),
                                requestBody
                            )
                        )
                        .build()
                    val response = httpClient.newCall(request).execute()

                    if (response.isSuccessful) {
                        response.body()?.string()?.let { body ->
                            val deviceCodeResponse = JSON.parseObject(body, DeviceCodeResponse::class.java)
                            responseCallback.onResponse(deviceCodeResponse)

                            // 开始轮询 token
                            val newThread = PollingTokenThread(
                                context,
                                httpClient,
                                clientId,
                                deviceCodeResponse,
                                authResponseCallback
                            )
                            requestCache.add(newThread)
                            newThread.start()
                        }
                    } else {
                        responseCallback.onError(response.code(), response.body()?.string(), null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    responseCallback.onError(null, null, e)
                }
            }.start()
        } ?: run {
            throw IllegalStateException("Cannot create an OkHttp Client.")
        }
    }

    fun cancelPolling() {
        Log.d(TAG, "cancelPolling")

        requestCache.map {
            try {
                it.interrupt()
            } catch (_: Exception) {

            }
        }
        requestCache.clear()
    }

    fun refreshAccessToken(
            context: Context,
            refreshToken : String,
            authResponseCallback: AuthResponseCallback) {
        if (httpClient == null) {
            httpClient = createHttpClient()
        }

        httpClient?.let { httpClient->
            Thread {
                try {
                    val requestBody = JSONObject()
                    requestBody[KEY_GRANT_TYPE] = GRANT_TYPE_REFRESH
                    requestBody[GRANT_TYPE_REFRESH] = refreshToken

                    val request = Request.Builder()
                        .url(AUTH_URL_TOKEN)
                        .post(
                            RequestBody.create(
                                MediaType.get("application/json"),
                                requestBody.toJSONString()
                            )
                        )
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val httpCode = response.code()
                    val body = response.body()?.string()

                    Log.d(TAG, "code: $httpCode, body: $body")

                    if (response.isSuccessful) {
                        val authResponse = JSON.parseObject(body, AuthResponse::class.java)
                        setAuthResponseToPref(context, authResponse)

                        authResponseCallback?.onAuthSuccess(authResponse)
                    } else {
                        authResponseCallback?.onAuthFailed(
                            null,
                            IllegalStateException("Server return $httpCode while requesting")
                        )
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "polling exception.")

                    e.printStackTrace()
                    authResponseCallback?.onAuthFailed(null, e)
                }
            }.start()
        }
    }

    private class PollingTokenThread(
        val context: Context,
        val httpClient: OkHttpClient,
        val clientId: String,
        val deviceCodeResponse: DeviceCodeResponse,
        val authResponseCallback: AuthResponseCallback? = null
    ) : Thread() {
        override fun run() {
            val current = System.currentTimeMillis() / 1000
            val interval = deviceCodeResponse.interval
            val expiresIn = deviceCodeResponse.expiresIn

            while (System.currentTimeMillis() / 1000 - current < expiresIn) {
                try {
                    val requestBody = JSONObject()
                    requestBody[KEY_CLIENT_ID] = clientId
                    requestBody[KEY_GRANT_TYPE] = GRANT_TYPE_DEVICE_CODE
                    requestBody[KEY_DEVICE_CODE] = deviceCodeResponse.deviceCode

                    val request = Request.Builder()
                        .url(AUTH_URL_TOKEN)
                        .post(
                            RequestBody.create(
                                MediaType.get("application/json"),
                                requestBody.toJSONString()
                            )
                        )
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val httpCode = response.code()
                    val body = response.body()?.string()

                    Log.d(TAG, "code: $httpCode, body: $body")

                    if (response.isSuccessful) {
                        val authResponse = JSON.parseObject(body, AuthResponse::class.java)
                        setAuthResponseToPref(context, authResponse)

                        authResponseCallback?.onAuthSuccess(authResponse)
                        return
                    } else {
                        if (httpCode in 400 until 500) {
                            val json = JSON.parseObject(body)
                            if (json?.containsKey(KEY_ERROR) == true) {
                                when (val error = json.getString(KEY_ERROR)) {
                                    ERROR_AUTHORIZATION_PENDING -> {
                                        sleep(interval * 1000L)
                                    }
                                    else -> {
                                        // 可能是 expired_token, 或 access_denied
                                        // 以上两种情况都不需要再轮询
                                        authResponseCallback?.onAuthFailed(error, null)
                                        return
                                    }
                                }
                            } else {
                                sleep(interval * 1000L)
                            }
                        } else {
                            authResponseCallback?.onAuthFailed(
                                null,
                                IllegalStateException("Server return $httpCode while requesting")
                            )
                            return
                        }
                    }
                } catch (e : InterruptedException) {
                    // interrupted, no need to callback
                    e.printStackTrace()
                    return
                } catch (e : Exception) {
                    e.printStackTrace()
                    authResponseCallback?.onAuthFailed(null, e)
                    return
                }
            }
        }
    }

    interface ResponseCallback<T> {
        fun onResponse(response: T)
        fun onError(httpCode: Int?, errorBody: String?, throwable: Throwable?)
    }

    interface AuthResponseCallback {
        fun onAuthSuccess(authResponse: AuthResponse)
        fun onAuthFailed(errorBody: String?, throwable: Throwable?)
    }
}