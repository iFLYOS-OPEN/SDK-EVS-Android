package com.iflytek.cyber.evs.demo

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.iflytek.cyber.evs.demo.utils.DeviceUtils
import com.iflytek.cyber.evs.demo.utils.PrefUtil
import com.iflytek.cyber.evs.sdk.auth.AuthDelegate
import com.iflytek.cyber.evs.sdk.model.AuthResponse
import com.iflytek.cyber.evs.sdk.model.DeviceCodeResponse
import kotlinx.android.synthetic.main.activity_auth.*
import org.json.JSONObject
import java.util.*


class AuthActivity : AppCompatActivity() {
    private var authUrl: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        supportActionBar?.run {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val deviceId =
            pref.getString(getString(R.string.key_device_id), DeviceUtils.getDeviceId(this))
                ?: "INVALID_DEVICE_ID"
        val clientId =
            pref.getString(getString(R.string.key_client_id), getString(R.string.default_client_id))
                ?: "INVALID_CLIENT_ID"
        val authBaseUrl =
            pref.getString(getString(R.string.key_auth_url), getString(R.string.default_auth_url))
                ?: "INVALID_AUTH_URL"

        AuthDelegate.setAuthUrl(authBaseUrl)

        auth_summary.text = getString(R.string.auth_summary, clientId, deviceId)

        scope_data.setText(AuthDelegate.SCOPE_DATA_DEFAULT)

        custom_scope_data.setOnCheckedChangeListener { _, isChecked ->
            scope_data.isEnabled = isChecked
        }

        if (AuthDelegate.getAuthResponseFromPref(this) == null) {
            PrefUtil.setToPref(this, "auth_params", null)
        }

        request.setOnClickListener {
            val customScopeData =
                if (custom_scope_data.isChecked)
                    scope_data.text.toString()
                else
                    AuthDelegate.SCOPE_DATA_DEFAULT

            // ifdef 这部分代码复制于 AuthDelegate，仅做 UI 展示用
            val scopeData = JSONObject()
            val userIvsAll = JSONObject()
            userIvsAll.put("device_id", deviceId)
            scopeData.put("user_ivs_all", userIvsAll)

            val requestBody =
                "client_id=$clientId&scope=$customScopeData&scope_data=$scopeData"

            request_params.text = getString(R.string.request_params_summary, requestBody)
            // endif

            AuthDelegate.requestDeviceCode(
                this,
                clientId,
                deviceId,
                object : AuthDelegate.ResponseCallback<DeviceCodeResponse> {
                    override fun onResponse(response: DeviceCodeResponse) {
                        runOnUiThread {
                            tv_response.visibility = View.VISIBLE
                            tv_response.text = response.toString()
                            qr_code.visibility = View.VISIBLE
                        }

                        authUrl = "${response.verificationUri}?user_code=${response.userCode}"
                        createQRBitmap(authUrl!!, qr_code.width, qr_code.height)?.let { bitmap ->
                            runOnUiThread {
                                qr_code.setImageBitmap(bitmap)
                                open_browser.visibility = View.VISIBLE
                            }
                        }
                    }

                    @SuppressLint("SetTextI18n")
                    override fun onError(
                        httpCode: Int?,
                        errorBody: String?,
                        throwable: Throwable?
                    ) {
                        runOnUiThread {
                            tv_response.visibility = View.VISIBLE
                            qr_code.visibility = View.INVISIBLE
                            open_browser.visibility = View.INVISIBLE

                            throwable?.let {
                                tv_response.text = "$it\n${it.message}"
                            } ?: run {
                                tv_response.text = "code: $httpCode\n body: $errorBody"
                            }
                        }
                    }
                },
                object : AuthDelegate.AuthResponseCallback {
                    override fun onAuthSuccess(authResponse: AuthResponse) {
                        PrefUtil.setToPref(baseContext, "auth_params", "$authBaseUrl\n$requestBody")

                        val message = authResponse.toString()
                        runOnUiThread {
                            AlertDialog.Builder(this@AuthActivity)
                                .setTitle("授权成功")
                                .setMessage(message)
                                .setNegativeButton("确定", null)
                                .setPositiveButton("关闭页面") { _, _ -> finish() }
                                .show()
                        }
                    }

                    override fun onAuthFailed(errorBody: String?, throwable: Throwable?) {
                        val message = throwable?.message ?: errorBody
                        runOnUiThread {
                            AlertDialog.Builder(this@AuthActivity)
                                .setTitle("请求失败")
                                .setMessage(message)
                                .setNegativeButton("确定", null)
                                .show()
                        }
                    }

                },
                customScopeData
            )
        }
        open_browser.setOnClickListener {
            authUrl?.let { url ->
                val uri = Uri.parse(url)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(Intent.createChooser(intent, "在浏览器中打开授权页面"))
            } ?: run {
                Snackbar.make(window.decorView, "请先请求 device code", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun createQRBitmap(content: String, width: Int, height: Int): Bitmap? {
        try {
            val colorBlack = ActivityCompat.getColor(this, android.R.color.black)
            val coloWhite = ActivityCompat.getColor(this, android.R.color.white)

            // 设置二维码相关配置,生成BitMatrix(位矩阵)对象
            val hints = Hashtable<EncodeHintType, String>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8" // 字符转码格式设置
            hints[EncodeHintType.ERROR_CORRECTION] = "H" // 容错级别设置
            hints[EncodeHintType.MARGIN] = "2" // 空白边距设置

            val bitMatrix =
                QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints)

            // 创建像素数组,并根据BitMatrix(位矩阵)对象为数组元素赋颜色值
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * width + x] = colorBlack // 黑色色块像素设置
                    } else {
                        pixels[y * width + x] = coloWhite // 白色色块像素设置
                    }
                }
            }

            // 创建Bitmap对象,根据像素数组设置Bitmap每个像素点的颜色值,之后返回Bitmap对象
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return bitmap
        } catch (e: WriterException) {
            e.printStackTrace()
        }
        return null

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_auth, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home)
            onBackPressed()
        else if (item.itemId == R.id.current_auth_info) {
            val builder = AlertDialog.Builder(this)
                .setTitle(R.string.current_auth_info)
                .setPositiveButton(android.R.string.yes, null)
            val response = AuthDelegate.getAuthResponseFromPref(this)
            val message = if (response == null) {
                "无"
            } else {
                builder.setNegativeButton(R.string.copy_access_token) { _, _ ->
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip =
                        ClipData.newPlainText(response.accessToken, response.accessToken)
                }
                getString(
                    R.string.auth_params_summary,
                    PrefUtil.getFromPref(baseContext, "auth_params"),
                    response.toJSONString()
                )
            }
            builder.setMessage(message)
            builder.show()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        AuthDelegate.cancelPolling()
    }
}