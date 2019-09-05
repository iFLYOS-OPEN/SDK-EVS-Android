package com.iflytek.cyber.evs.demo

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.iflytek.cyber.evs.sdk.auth.AuthDelegate
import com.iflytek.cyber.evs.sdk.model.DeviceCodeResponse
import kotlinx.android.synthetic.main.activity_auth.*
import com.google.zxing.WriterException
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.EncodeHintType
import androidx.core.app.ActivityCompat
import com.google.android.material.snackbar.Snackbar
import java.util.*
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.iflytek.cyber.evs.demo.utils.PrefUtil
import com.iflytek.cyber.evs.sdk.model.AuthResponse


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

        custom_device_id.setOnCheckedChangeListener { _, isChecked ->
            device_id.isEnabled = isChecked
            if (!isChecked) {
                device_id.setText(getDefaultDeviceId())
            }
        }
        reset_client_id.setOnClickListener {
            client_id.setText(getClientId())
        }
        request.setOnClickListener {
            val deviceId = device_id.text.toString()
            val clientId = client_id.text.toString()

            var scopeData = if (clientId == "cc62fa00-a9dd-416b-9de6-9d14ba247276") {
                "user_ivs_all user_device_text_in"
            } else {
                "user_ivs_all"
            }

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

                    override fun onError(httpCode: Int?, errorBody: String?, throwable: Throwable?) {
                        runOnUiThread {
                            tv_response.visibility = View.VISIBLE
                            qr_code.visibility = View.INVISIBLE
                            open_browser.visibility = View.INVISIBLE

                            throwable?.let {
                                tv_response.text = it.message
                            } ?: run {
                                tv_response.text = "code: $httpCode\n body: $errorBody"
                            }
                        }
                    }
                },
                object : AuthDelegate.AuthResponseCallback {
                    override fun onAuthSuccess(authResponse: AuthResponse) {
                        PrefUtil.setToPref(this@AuthActivity, "device_id", deviceId)

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
                scopeData
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

        client_id.setText(getClientId())
        device_id.setText(getDefaultDeviceId())
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

            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints)

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

    private fun getClientId(): String {
        return BuildConfig.CLIENT_ID
    }

    @SuppressLint("HardwareIds")
    private fun getDefaultDeviceId() = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home)
            onBackPressed()
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        AuthDelegate.cancelPolling()
    }
}