package com.iflytek.cyber.evs.demo

import android.content.Intent
import android.graphics.ImageFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.MenuItem
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.impl.ImageAnalysisConfig
import androidx.camera.core.impl.PreviewConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.iflytek.cyber.evs.demo.databinding.ActivityScanQrcodeBinding
import java.util.concurrent.Executor

class ScanQRCodeActivity : AppCompatActivity() {
    companion object {
        const val RESULT_SUCCEED = 1
        const val RESULT_CANCEL = 0

        const val EXTRA_RESULT = "result"
    }

    private var getResult = false
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var binding: ActivityScanQrcodeBinding

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        var preview: Preview = Preview.Builder()
            .build()

        var cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.setSurfaceProvider(binding.previewView.surfaceProvider)

        binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        try {
            val analysis = ImageAnalysis.Builder()
                // enable the following line if RGBA output is needed.
                // .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
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
            var camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                analysis,
                preview
            )
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScanQrcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }


        setResult(RESULT_CANCEL)
    }

    override fun onResume() {
        super.onResume()

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
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

        override fun analyze(image: ImageProxy) {
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