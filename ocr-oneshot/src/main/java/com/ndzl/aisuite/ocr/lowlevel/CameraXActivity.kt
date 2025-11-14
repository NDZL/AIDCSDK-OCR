package com.ndzl.aisuite.ocr.lowlevel

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.zebra.ai.vision.detector.AIVisionSDK
import com.zebra.ai.vision.detector.AIVisionSDKLicenseException
import com.zebra.ai.vision.detector.InferencerOptions
import com.zebra.ai.vision.detector.InvalidInputException
import com.zebra.ai.vision.detector.TextOCR
import com.zebra.ai.vision.internal.utils.ImageConverter.rotateBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import zebra.BarcodeDetector.databinding.ActivityCameraXactivityBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.compareTo
import kotlin.system.measureTimeMillis

class CameraXActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityCameraXactivityBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var sharedPreferences: SharedPreferences

    private var imageCapture: ImageCapture? = null
    private var textOCR: TextOCR? = null

    private val executor = Executors.newFixedThreadPool(3)
    private val captureExecutor = Executors.newSingleThreadExecutor()

    var overalltime =0L

    val soundMachine = SoundMachine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "zebra/aisuite/1-shot Ocr by cxnt48"

        viewBinding = ActivityCameraXactivityBinding.inflate(layoutInflater)
        viewBinding.tvOCRout.text = ""
        setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        runBlocking(cameraExecutor.asCoroutineDispatcher()) {
            sharedPreferences = getSharedPreferences("SettingsPreferences", MODE_PRIVATE)
            loadSettings()
        }

       val btOCR =  viewBinding.btnOCR
        btOCR.setOnClickListener {
            soundMachine.playSound()
            overalltime = System.currentTimeMillis()
            captureStillForOCR()
        }
    }

    override fun onResume() {
        super.onResume()
        initializeTextOCR()
        bindCameraUseCases()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        cameraExecutor.shutdown()
        captureExecutor.shutdown()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyDown: $keyCode")
        if(keyCode==104)//PTT BUTTON ON A ZEBRA TC5x
            captureStillForOCR()
        return super.onKeyDown(keyCode, event)
    }

    private fun loadSettings() {
        val imageSize = sharedPreferences.getInt("IMAGESIZE", 2)
        when (imageSize) {
            0 -> {
                OverlayView.CAMERA_RESOLUTION_WIDTH = 480
                OverlayView.CAMERA_RESOLUTION_HEIGHT = 640
            }
            1 -> {
                OverlayView.CAMERA_RESOLUTION_WIDTH = 1080
                OverlayView.CAMERA_RESOLUTION_HEIGHT = 1920
            }
            2 -> {
                OverlayView.CAMERA_RESOLUTION_WIDTH = 1536
                OverlayView.CAMERA_RESOLUTION_HEIGHT = 2048
            }
            3 -> {
                OverlayView.CAMERA_RESOLUTION_WIDTH = 1920
                OverlayView.CAMERA_RESOLUTION_HEIGHT = 2560
            }
            4 -> {
                OverlayView.CAMERA_RESOLUTION_WIDTH = -1
                OverlayView.CAMERA_RESOLUTION_HEIGHT = -1
            }
        }

        val scene = sharedPreferences.getInt("SCENE", 0)
        when (scene) {
            0 -> OverlayView.CHOSEN_SCENE = CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO
            1 -> OverlayView.CHOSEN_SCENE = CameraMetadata.CONTROL_SCENE_MODE_ACTION
            2 -> OverlayView.CHOSEN_SCENE = CameraMetadata.CONTROL_SCENE_MODE_SPORTS
            3 -> OverlayView.CHOSEN_SCENE = CameraMetadata.CONTROL_SCENE_MODE_BARCODE
        }

        val zoom = sharedPreferences.getInt("ZOOM", 0)
        when (zoom) {
            0 -> OverlayView.ZOOM_RATIO = 1.0
            1 -> OverlayView.ZOOM_RATIO = 1.5
            2 -> OverlayView.ZOOM_RATIO = 2.0
            3 -> OverlayView.ZOOM_RATIO = 3.0
            4 -> OverlayView.ZOOM_RATIO = 5.0
        }

        val modeldim = sharedPreferences.getInt("MODELDIM", 1)
        when (modeldim) {
            0 -> OverlayView.MODEL_DIMS = 640
            1 -> OverlayView.MODEL_DIMS = 800
            2 -> OverlayView.MODEL_DIMS = 1600
        }


    }

    private fun initializeTextOCR() {
        AIVisionSDK.getInstance(applicationContext).init()

        Log.d(TAG, "AISuite SDK v${AIVisionSDK.getInstance(applicationContext).sdkVersion} initialized")

        val textOCRSettings = TextOCR.Settings("text-ocr-recognizer").apply {
            val rpo = arrayOf(
                InferencerOptions.DSP,
                InferencerOptions.CPU,
                InferencerOptions.GPU
            )

            detectionInferencerOptions.runtimeProcessorOrder = rpo
            recognitionInferencerOptions.runtimeProcessorOrder = rpo
            detectionInferencerOptions.defaultDims.apply {
                height = OverlayView.MODEL_DIMS
                width = OverlayView.MODEL_DIMS
            }
        }

        val startTime = System.currentTimeMillis()

        CoroutineScope(executor.asCoroutineDispatcher()).launch {
            try {
                val ocrInstance = TextOCR.getTextOCR(textOCRSettings, executor).await()
                textOCR = ocrInstance

                Log.d(TAG, "TextOCR model loaded in ${System.currentTimeMillis() - startTime}ms")
            } catch (e: AIVisionSDKLicenseException) {
                Log.e(TAG, "License error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "TextOCR creation failed: ${e.message}")
            }
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraUseCases() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider) }

            val imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)

            if (OverlayView.CAMERA_RESOLUTION_WIDTH > 0 && OverlayView.CAMERA_RESOLUTION_HEIGHT > 0) {
                imageCaptureBuilder.setTargetResolution(
                    android.util.Size(
                        OverlayView.CAMERA_RESOLUTION_WIDTH,
                        OverlayView.CAMERA_RESOLUTION_HEIGHT
                    )
                )
            }

            Camera2Interop.Extender(imageCaptureBuilder)
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_CAPTURE_INTENT,
                    CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE
                )
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_SCENE_MODE,
                    OverlayView.CHOSEN_SCENE
                )

            imageCapture = imageCaptureBuilder.build()

            try {
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
//                    preview,  //HIDDEON ON CUSTOMER'S REQUEST
                    imageCapture
                ).cameraControl.setZoomRatio(OverlayView.ZOOM_RATIO.toFloat())
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureStillForOCR() {
        val ic = imageCapture ?: run {
            Log.w(TAG, "ImageCapture not initialized")
            return
        }

        ic.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    doOCR(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Still capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun doOCR(imgproxy: ImageProxy) {
        if (textOCR == null) {
            Log.w(TAG, "TextOCR not initialized")
            imgproxy.close()
            return
        }

        runBlocking(cameraExecutor.asCoroutineDispatcher()) {
            try {
                var bitmap = rotateBitmapIfNeeded(imgproxy)
                val ocrsb = StringBuilder()
                Log.i(TAG, "Image to OCR> W${imgproxy.width} x H${imgproxy.height}")

                textOCR?.detectWords(bitmap, executor)?.thenAccept { words ->
                    words.forEach { word ->
                        Log.i(TAG, "OCR detected: ${word.decodes[0].content}")
                        ocrsb.append(word.decodes[0].content).append(" ")
                    }
                    runOnUiThread {
                        viewBinding.tvOCRout.text = ocrsb.toString()
                        overalltime = System.currentTimeMillis() - overalltime
                        Log.i(TAG, "OCR processed in $overalltime ms")
                    }
                    imgproxy.close()
                }?.exceptionally { throwable ->
                    Log.e(TAG, "OCR detection failed", throwable)
                    imgproxy.close()
                    null
                }
            } catch (e: InvalidInputException) {
                Log.e(TAG, "Invalid input for OCR", e)
                imgproxy.close()
            } catch (e: Exception) {
                Log.e(TAG, "OCR processing error", e)
                imgproxy.close()
            }
        }
    }

    private fun rotateBitmapIfNeeded(imageProxy: ImageProxy): Bitmap {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return rotateBitmap(imageProxy.toBitmap(), rotationDegrees)
    }

    companion object {
        private const val TAG = "OCRSample"
    }
}