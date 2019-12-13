package com.iflytek.cyber.evs.demo

import android.content.Intent
import android.graphics.ImageFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.MenuItem
import android.view.TextureView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.android.synthetic.main.activity_scan_qrcode.*
import java.util.concurrent.Executor

class ScanQRCodeActivity : AppCompatActivity() {
    companion object {
        const val RESULT_SUCCEED = 1
        const val RESULT_CANCEL = 0

        const val EXTRA_RESULT = "result"
    }

    private var getResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_scan_qrcode)

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        try {
            val textureView: TextureView = findViewById(R.id.texture_view)
            val previewConfig = PreviewConfig.Builder().apply {
                setTargetAspectRatio(AspectRatio.RATIO_4_3)
            }.build()
            val analysisConfig = ImageAnalysisConfig.Builder().apply {
                setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            }.build()

            val preview = AutoFitPreviewBuilder.build(previewConfig, textureView)
            val analysis = ImageAnalysis(analysisConfig)

            val analyzerThread = HandlerThread("BarcodeAnalyzer").apply { start() }
            val analyzerHandler = Handler(analyzerThread.looper)
            analysis.setAnalyzer(Executor {
                analyzerHandler.post(it)
            }, QRCodeAnalyzer { result ->
                if (!getResult) {
                    getResult = true

                    val resultIntent = Intent()
                    resultIntent.putExtra(EXTRA_RESULT, result.text)
                    setResult(RESULT_SUCCEED, resultIntent)

                    finish()
                }
            })

            CameraX.bindToLifecycle(this, preview, analysis)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        setResult(RESULT_CANCEL)
    }

    override fun onResume() {
        super.onResume()

    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }


    class QRCodeAnalyzer(private val resultCallback: ((Result) -> Unit)? = null) :
        ImageAnalysis.Analyzer {
        private val reader: MultiFormatReader = MultiFormatReader()

        init {
            val map = mapOf<DecodeHintType, Collection<BarcodeFormat>>(
                Pair(DecodeHintType.POSSIBLE_FORMATS, arrayListOf(BarcodeFormat.QR_CODE))
            )
            reader.setHints(map)
        }

        override fun analyze(image: ImageProxy, rotationDegrees: Int) {
            if (ImageFormat.YUV_420_888 != image.format) {
                Log.e("BarcodeAnalyzer", "expect YUV_420_888, now = ${image.format}")
                return
            }
            val buffer = image.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            val height = image.height
            val width = image.width
            buffer.get(data)

            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)

            val bitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decode(bitmap)
                resultCallback?.invoke(result)
            } catch (e: Exception) {
                if (e is NotFoundException) {
                    Log.v("BarcodeAnalyzer", "Cannot recognize code.")
                } else {
                    e.printStackTrace()
                }
            }

        }
    }
}