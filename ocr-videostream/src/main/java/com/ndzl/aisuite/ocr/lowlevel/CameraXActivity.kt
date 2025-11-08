package com.ndzl.aisuite.ocr.lowlevel


import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.StrictMode
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.ToggleButton
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.zebra.ai.vision.detector.AIVisionSDK
import com.zebra.ai.vision.detector.AIVisionSDKLicenseException
import com.zebra.ai.vision.detector.InferencerOptions
import com.zebra.ai.vision.detector.InvalidInputException
import com.zebra.ai.vision.detector.TextOCR
import com.zebra.ai.vision.internal.utils.ImageConverter.rotateBitmap
import kotlinx.coroutines.CoroutineScope


//import com.zebra.ai.vision.AIVisionSDK
//
//import com.zebra.ai.vision.BBox
//import com.zebra.ai.vision.BarcodeDecoder
//import com.zebra.ai.vision.InferencerOptions;
//import com.zebra.ai.vision.Localizer

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import zebra.BarcodeDetector.BuildConfig


import zebra.BarcodeDetector.databinding.ActivityCameraXactivityBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.scale


class CameraXActivity : AppCompatActivity() {
    private var mustLogToServer: Boolean = false
    private lateinit var viewBinding: ActivityCameraXactivityBinding

    private var mustSave: Boolean = false
    private var mustLoop: Boolean = false

    private var imageCapture: ImageCapture? = null

   // private var imageAnalyzer: ImageAnalysis? = null

    private lateinit var cameraExecutor: ExecutorService

    val executor = Executors.newFixedThreadPool(3);
    private lateinit var sharedPreferences: SharedPreferences

    val soundMachine = SoundMachine()

    private val TAG = "OCRSample"
    private var textOCR: TextOCR? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "zebra/aisuite/ ocr by cxnt48"

        //A14 Strict mode test
        enableStrictMode()


        viewBinding = ActivityCameraXactivityBinding.inflate(layoutInflater)

//        val vparams = viewBinding.viewFinder.layoutParams
//        vparams.height = resources.displayMetrics.heightPixels / 3
//        viewBinding.viewFinder.layoutParams = vparams

        viewBinding.tvOCRout.text = ""

        setContentView(viewBinding.root)

        runBlocking(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
            sharedPreferences = getSharedPreferences("SettingsPreferences", MODE_PRIVATE)
            loadSettings()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        val toggle: ToggleButton = viewBinding.toggleFlipperMode
        toggle.setOnClickListener { view: View ->
            if (toggle.isChecked) {
                isL2Rtext = true
            } else {
                isL2Rtext = false
            }
        }

        viewBinding.lowerPanel.post {
            val panelHeight = (viewBinding.viewFinder.height * 2) / 3
            viewBinding.lowerPanel.layoutParams = viewBinding.lowerPanel.layoutParams.apply { height = panelHeight }
            viewBinding.lowerPanel.requestLayout()
        }


    }



    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()

        if(cameraExecutor!=null)
            cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()

        val timebegin = System.currentTimeMillis()

        periodJobOnCanvas(150) //refresh overlayView canvas every 0.5s

        viewBinding.tvOCRout.visibility = View.VISIBLE
        viewBinding.overlayView.visibility = View.VISIBLE

            initializeTextOCR()
            Log.i(TAG, "ocr Models initialised in " + (System.currentTimeMillis() - timebegin) + "ms")
            captureVideo()




    }


    private fun loadSettings() {
        val imageSize = sharedPreferences.getInt("IMAGESIZE", 1)
        when(imageSize){
            0 -> {
                OverlayView.Companion.CAMERA_RESOLUTION_WIDTH = 480
                OverlayView.Companion.CAMERA_RESOLUTION_HEIGHT = 640
            }
            1 -> {
                OverlayView.Companion.CAMERA_RESOLUTION_WIDTH = 1080
                OverlayView.Companion.CAMERA_RESOLUTION_HEIGHT = 1920
            }
            2 -> {
                OverlayView.Companion.CAMERA_RESOLUTION_WIDTH = 1536
                OverlayView.Companion.CAMERA_RESOLUTION_HEIGHT = 2048
            }
            3 -> {
                OverlayView.Companion.CAMERA_RESOLUTION_WIDTH = 1920
                OverlayView.Companion.CAMERA_RESOLUTION_HEIGHT = 2560
            }
            4 -> {
                OverlayView.Companion.CAMERA_RESOLUTION_WIDTH = -1
                OverlayView.Companion.CAMERA_RESOLUTION_HEIGHT = -1
            }
        }

        val scene = sharedPreferences.getInt("SCENE", 0)
        when(scene) {
            0 -> {
                OverlayView.Companion.CHOSEN_SCENE = CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO
            }
            1 -> {
                OverlayView.Companion.CHOSEN_SCENE = CameraMetadata.CONTROL_SCENE_MODE_ACTION
            }
            2 -> {
                OverlayView.Companion.CHOSEN_SCENE = CameraMetadata.CONTROL_SCENE_MODE_SPORTS
            }
            3 -> {
                OverlayView.Companion.CHOSEN_SCENE = CameraMetadata.CONTROL_SCENE_MODE_BARCODE
            }
        }



        val zoom = sharedPreferences.getInt("ZOOM", 0)
        when(zoom)  { //not yet implemented
            0 -> {
                OverlayView.Companion.ZOOM_RATIO = 1.0
            }
            1 -> {
                OverlayView.Companion.ZOOM_RATIO = 1.5
            }
            2 -> {
                OverlayView.Companion.ZOOM_RATIO = 2.0
            }
            3 -> {
                OverlayView.Companion.ZOOM_RATIO = 3.0
            }
            4 -> {
                OverlayView.Companion.ZOOM_RATIO = 5.0
            }
        }

    }

    fun deleteCLQOlderHalf(){
        val halfSize = viewBinding.overlayView.clq.size / 2
        for (i in 0 until halfSize) {
            viewBinding.overlayView.clq.poll()
        }
    }

    private fun periodJobOnCanvas(timeInterval: Long) {
        val handler = Handler()
        val runnable = object : Runnable {
            override fun run() {

                viewBinding.overlayView?.invalidate()

                //viewBinding.overlayView?.clq?.clear()
                deleteCLQOlderHalf()

                handler.postDelayed(this, timeInterval)

            }
        }
        handler.postDelayed(runnable, timeInterval)
    }

    private fun initializeTextOCR() {
        AIVisionSDK.getInstance(applicationContext).init()

        println("AISuite SDK v${AIVisionSDK.getInstance(applicationContext).sdkVersion} just init")
        println("AISuite SDK's supported Barcode decoders " + AIVisionSDK.getInstance(applicationContext).getModelArchiveInfo("text-ocr-recognizer"))  //returned data does not currently match supported Symbologies


        val textOCRSettings = TextOCR.Settings("text-ocr-recognizer").apply {
            val rpo = arrayOf(
                InferencerOptions.DSP,
                InferencerOptions.CPU,
                InferencerOptions.GPU
            )

            detectionInferencerOptions.runtimeProcessorOrder = rpo
            recognitionInferencerOptions.runtimeProcessorOrder = rpo
            detectionInferencerOptions.defaultDims.apply {
                height = 640
                width = 640
            }
        }

        val startTime = System.currentTimeMillis()

        CoroutineScope(executor.asCoroutineDispatcher()).launch {
            try {
                val ocrInstance = TextOCR.getTextOCR(textOCRSettings, executor).await()
                textOCR = ocrInstance

                Log.d(TAG, "TextOCR() obj creation / model loading time = ${System.currentTimeMillis() - startTime} milli sec")
            }
            catch (e: AIVisionSDKLicenseException) {
                Log.e(TAG, "AIVisionSDKLicenseException: TextOCR object creation failed, ${e.message}")
            }
            catch (e: Exception) {
                Log.e(TAG, "Fatal error: TextOCR creation failed - ${e.message}")
            }
        }
    }


    fun rotateBitmapIfNeeded(imageProxy: ImageProxy): Bitmap {
        val rotationDegrees = imageProxy.getImageInfo().getRotationDegrees()
        return rotateBitmap(imageProxy.toBitmap(), rotationDegrees)
    }

    fun bitmapBlankUpperAndLowerThirds(src: Bitmap): Bitmap {
        val height = src.height
        val width = src.width
        val thirdHeight = height / 3

        val result = src.copy(src.config!!, true)

        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.FILL
        }

        // Blank upper third
        //canvas.drawRect(0f, 0f, width.toFloat(), thirdHeight.toFloat(), paint)

        // Blank lower third
        canvas.drawRect(0f, (1 * thirdHeight).toFloat(), width.toFloat(), height.toFloat(), paint)

        return result
    }

    val horizontalFlip  = { src:Bitmap ->
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private val fpsQueue = ArrayDeque<Long>(1)

    private  fun doOCR(imgproxy: ImageProxy) {
        if (textOCR == null) {
            // textOCR is initialized asynchronously. If it's not ready, close image and wait.
            imgproxy.close()
            return
        }
        val start= System.currentTimeMillis()
       lateinit var bitmap: Bitmap
       runBlocking(cameraExecutor.asCoroutineDispatcher()) {
       bitmap  = rotateBitmapIfNeeded( imgproxy)
       if(!isL2Rtext)
              bitmap = horizontalFlip(bitmap)
       }
       bitmap = bitmapBlankUpperAndLowerThirds(bitmap)

       Log.d("CameraXActivity", "Bitmap size: ${bitmap.width}x${bitmap.height}")

        try {
            //val bitmap = CommonUtils.rotateBitmapIfNeeded(image)
            textOCR?.detectWords(bitmap, executor)?.thenAccept { words ->
                words.forEach {
                    Log.i(TAG, "#OCR Word: ${it.decodes[0].content.toString()}")
                    val bev = BCEvent(
                        (it.bbox.x[0])!!.toFloat(),
                        (it.bbox.y[0])!!.toFloat(),
                        viewBinding.overlayView.paintRed,
                        it.decodes[0].content.toString(),
                        System.currentTimeMillis()
                    )
                    viewBinding.overlayView.clq.push(bev)
                }
                imgproxy.close()
            }?.exceptionally { ex ->
                imgproxy.close()
                null
            }
        } catch (e: InvalidInputException) {

            imgproxy.close()
        }

        viewBinding.overlayView.invalidate()
    }



    private fun setOutputtextInMainThread( txt: String){
        runOnUiThread {
            viewBinding.tvOCRout.text = txt
        }
    }
    private fun appendOutputtextInMainThread( txt: String){
        runOnUiThread {
            viewBinding.tvOCRout.text = txt + "" + viewBinding.tvOCRout.text
        }
    }




    //load png
    private fun loadBitmapFromAsset(): ByteArray {
        val inputStream = assets.open("technologies.png")
        val buffer = ByteArray(inputStream.available())
        inputStream.read(buffer)
        inputStream.close()
        return buffer
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun captureVideo() {

        var framesSkipCounter = 1

        val resolutionSelector = ResolutionSelector.Builder()
            // Prefer 16:9 aspect ratio, fall back to whatever is available
            //.setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)

            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)

            .setAllowedResolutionMode(PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            .build()

        val imageAnalyzerToBuild = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

        if(OverlayView.Companion.CAMERA_RESOLUTION_WIDTH >-1)
            imageAnalyzerToBuild.setTargetResolution(Size(
                OverlayView.Companion.CAMERA_RESOLUTION_WIDTH,
                OverlayView.Companion.CAMERA_RESOLUTION_HEIGHT
            ))
        else
            imageAnalyzerToBuild.setResolutionSelector(resolutionSelector)

//            .setTargetRotation(Surface.ROTATION_90) // not meant to rotate images!

        val camera2Extender = Camera2Interop.Extender(imageAnalyzerToBuild)
        camera2Extender
            .setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE,
                OverlayView.Companion.CHOSEN_SCENE
            )
            //.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(55, 65) )
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
//            .setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, LENS_//OPTICAL_STABILIZATION_MODE_ON )
//            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 1000000000L / 240) // 1/60s



        //  .setCaptureRequestOption(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_MOTION_TRACKING) //not applicable to video stream

/*

            .setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE, 4.0f //diopters (1/meters) 0.0f to infinity
            )
*/

        val imageAnalyzerBuiltUseCase = imageAnalyzerToBuild.build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->



                        doOCR(imageProxy)


                }
            }

        val previewUseCase = Preview.Builder()
            //.setTargetFrameRate(framerate)
            // .setDefaultResolution(Size(1280, 720))
            .build()
            .also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, previewUseCase, imageAnalyzerBuiltUseCase)
                    .cameraControl
                        .setZoomRatio( OverlayView.Companion.ZOOM_RATIO.toFloat() )

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }




    private fun requestPermissions() {}


    private fun getDeviceDetails() :String {
        val _android_id = "A_ID=" + Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceDetails = "${Build.MANUFACTURER}\n" +
                "${Build.MODEL}\n" +
                "${Build.DISPLAY}\n" +
                "${BuildConfig.APPLICATION_ID}-" +
                "${BuildConfig.VERSION_NAME}," +
                "${_android_id}\n"
        return deviceDetails
    }

    companion object {
        private const val TAG = "ocrCameraXActivity"
        public var isL2Rtext  =  true
    }

    fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork() // or .detectAll() for all violations
                .penaltyLog() // Log violations to Logcat
                // .penaltyDeath() // Crash the app on violations (use with caution)
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()// .detectActivityLeaks() // For detecting Activity leaks
                .penaltyLog()
                .build()
        )
    }




}