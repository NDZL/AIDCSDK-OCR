package zebra.ExampleOCR

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture

import androidx.core.content.ContextCompat
import com.ndzl.zphlib.CallerLog
import com.zebra.ai.vision.AIVisionSDK
import com.zebra.ai.vision.InferencerOptions
import com.zebra.ai.vision.TextOCR
import com.zebra.ai.vision.TextParagraph
import com.zebra.ai.vision.Word
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import zebra.ExampleOCR.databinding.ActivityCameraXactivityBinding
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlin.random.Random

// https://developer.android.com/codelabs/camerax-getting-started#1

//this code posted to https://github.com/NDZL/VISIONSDK-OCR as of Nov.2024

data class DetectedObjectResult(
    val confidence: Float,
    val label: String,
    val centerCoordinate: Pair<Int, Int>
)

typealias LumaListener = (luma: Double) -> Unit
class CameraXActivity : AppCompatActivity() {

    private var useDetectParagraphsAPI: Boolean = false
    private var mustLogToServer: Boolean = false
    private lateinit var viewBinding: ActivityCameraXactivityBinding

    private var mustSave: Boolean = false
    private var mustLoop: Boolean = false

    private var imageCaptureUsecase: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private lateinit var cameraExecutor: ExecutorService

    private var textOCR: TextOCR? = null
    private var textOCR_ONE: TextOCR? = null
    private var textOCR_TWO: TextOCR? = null

    private val executor: Executor = Executors.newFixedThreadPool(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //A14 Strict mode test
        enableStrictMode()

        viewBinding = ActivityCameraXactivityBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()  //was in oncreate originally

        getSupportedCameraResolutions()



        //Thread {
            initialiseModels()
       // }


        startRepeatingTask(1000)

    }

    private fun checkIfSavedPicturesToProcessAreAvailable() {
        //looks for and processes saved images in /storage/emulated/0/Android/data/com.ndzl.cv.ocr/cache/BATCH_*.jpg

        if (mustLogToServer){ logToServerDeviceDetails() }

        val dir = File(externalCacheDir!!.absolutePath)
        if (dir.exists()) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile && file.name.startsWith("BATCH_") ){
                        val batch_processing_bitmap = BitmapFactory.decodeFile(file.absolutePath)

                        if(useDetectParagraphsAPI)
                            doOCRasPARAGRAPHSthenProcessResults(file.name,"BATCH§", batch_processing_bitmap)
                        else
                            doOCRthenProcessResults(file.name,"BATCH", batch_processing_bitmap)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(!cameraExecutor.isShutdown)
            cameraExecutor.shutdown()
    }

    override fun onStop() {
        super.onStop()
        if(!cameraExecutor.isShutdown)
            cameraExecutor.shutdown()
    }

    override fun onDestroy() {
        textOCR?.dispose()
        super.onDestroy()
        if(!cameraExecutor.isShutdown)
            cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()


        startCamera() //manually grant permissions if needed.
//        // Request camera permissions
//        if (allPermissionsGranted()) {
//            startCamera()
//        } else {
//            requestPermissions()
//        }

        viewBinding.imageCaptureButton.setOnClickListener { takePhotoAndDoOCR() }
        viewBinding.batchProcessButton.setOnClickListener { checkIfSavedPicturesToProcessAreAvailable() }
        viewBinding.testButton.setOnClickListener { doConcurrencyTest() }

    }


    public fun boolSave(view: View){
        mustSave = !mustSave
    }
    public fun boolLoop(view: View){
        mustLoop = !mustLoop
    }

    public fun boolLog(view: View){
        mustLogToServer = !mustLogToServer
    }

    public fun boolDetectParagraphs(view: View){
        useDetectParagraphsAPI = !useDetectParagraphsAPI
    }

    private fun processAsset() {
        val ba = loadBitmapFromAsset()
    }

/*

    private fun startRepeatingJob(timeInterval: Long): Job {
        return CoroutineScope(Dispatchers.Default).launch {
            while (NonCancellable.isActive) {

                Log.d("OCR", "Repeating job -= Take photo and do OCR")
                takePhotoAndDoOCR()

                delay(timeInterval)
            }
        }
    }
*/

    //adding a timer to repeat a task at a certain interval without Coruotines
    private fun startRepeatingTask(timeInterval: Long) {
        val handler = android.os.Handler()
        val runnable = object : Runnable {
            override fun run() {
                if(mustLoop && !cameraExecutor.isShutdown ) {
                    Log.d("OCR", "startRepeatingTask / takePhotoAndDoOCR")
                    takePhotoAndDoOCR()
                }
                handler.postDelayed(this, timeInterval)

            }
        }
        handler.postDelayed(runnable, timeInterval)
    }


    private val thresholdsList: List<Double>? = null



    private fun initialiseModels() {
        runBlocking(cameraExecutor.asCoroutineDispatcher()) {



            AIVisionSDK.getInstance(applicationContext).init()

            println("AISuite SDK v${AIVisionSDK.getInstance(applicationContext).sdkVersion} just init")
            val startTime = System.currentTimeMillis()

            try {

                val textOCRSettings = TextOCR.Settings()

                val rpo = IntArray(1)
                rpo[0] = InferencerOptions.DSP

                textOCRSettings.decodingTotalProbThreshold = 0.9f


                textOCRSettings.detectionInferencerOptions.runtimeProcessorOrder = rpo
                textOCRSettings.recognitionInferencerOptions.runtimeProcessorOrder = rpo


                val dimensionValue = 800 //OCR MODEL ONLY SUPPORTS SQUARE VALUES?
                textOCRSettings.detectionInferencerOptions.defaultDims.width = dimensionValue
                textOCRSettings.detectionInferencerOptions.defaultDims.height = dimensionValue

                textOCRSettings.tiling.enable = false


                TextOCR.getTextOCR(textOCRSettings, executor).thenAccept { OCRInstance: TextOCR ->
                    textOCR = OCRInstance
                    Log.i(
                        "Profiling",
                        "TextOCR() obj creation / model loading time = " + (System.currentTimeMillis() -startTime) + " milli sec"
                    )
                }.exceptionally { e: Throwable ->
                    Log.e(TAG, "Fatal error: TextOCR creation failed - " + e.message)
                    null
                }

//                //for concurrency tests
//                TextOCR.getTextOCR(textOCRSettings,  Executors.newSingleThreadExecutor()  ).thenAccept { OCRInstance: TextOCR ->
//                    textOCR_ONE = OCRInstance
//                    Log.i(
//                        "Profiling",
//                        "textOCR_ONE obj creation / model loading time = " + (System.currentTimeMillis() -startTime) + " milli sec"
//                    )
//                }.exceptionally { e: Throwable ->
//                    Log.e(TAG, "Fatal error: textOCR_ONE creation failed - " + e.message)
//                    null
//                }
//
//                TextOCR.getTextOCR(textOCRSettings,  Executors.newSingleThreadExecutor()  ).thenAccept { OCRInstance: TextOCR ->
//                    textOCR_TWO = OCRInstance
//                    Log.i(
//                        "Profiling",
//                        "textOCR_TWO obj creation / model loading time = " + (System.currentTimeMillis()-startTime ) + " milli sec"
//                    )
//                }.exceptionally { e: Throwable ->
//                    Log.e(TAG, "Fatal error: textOCR_TWO creation failed - " + e.message)
//                    null
//                }






            } catch (e: IOException) {
                Log.e(TAG, "Fatal error: load failed - " + e.message)
                this@CameraXActivity.finish()
            }
            Log.i("Profiling", "OCR initialiseModels() took ${System.currentTimeMillis() - startTime} ms")
        }  //endof runBlocking

    }

    fun getSDKVersion(): String {
        return AIVisionSDK.getInstance(this).getSDKVersion();
    }

    private fun copyFromAssets(filename: String, toPath: String) {
        val bufferSize = 8192
        try {
            val stream = assets.open(filename)
            val fos = FileOutputStream("$toPath$filename")
            val output = BufferedOutputStream(fos)

            val data = ByteArray(bufferSize)
            var count: Int
            while (stream.read(data).also { count = it } != -1) {
                output.write(data, 0, count)
            }



            output.flush()
            print("#NDZL copyFromAssets: len=fos.size() ${fos.channel.size()}")
            output.close()
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun flipBitmapMatrix(src: Bitmap, recycleSrc: Boolean = false): Bitmap {
        // Creates a new bitmap that is horizontally flipped.
        // This typically calls into native code and is very fast / optimized.
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        val flipped = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (recycleSrc && flipped !== src) src.recycle()
        return flipped
    }

    private fun takePhotoAndDoOCR() {
        var imageCapture = imageCaptureUsecase ?: return

        try {
            //Log.d("TAKING PICTURE", "TO DO OCR")
            imageCapture.takePicture(cameraExecutor, object :
                ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)

                    lateinit var bitmap:Bitmap

                    bitmap= rotateBitmapIfNeeded(  image  )

                    bitmap = flipBitmapMatrix(image.toBitmap() , false)



                    Log.d("OCR", "Picture size: w${bitmap.width} x h${bitmap.height}")

                    if(useDetectParagraphsAPI)
                        doOCRasPARAGRAPHSthenProcessResults("","CAMERA", bitmap)
                    else {
                        doOCRthenProcessResults("", "CAMERA", bitmap)
                    }
                    //doOCRasWORDSthenProcessResults("","CAMERA", bitmap)
                    //doOCRasPARAGRAPHSthenProcessResults("","CAMERA", bitmap)

                    //keep the following as the last command
                    image.close() //VIP! else you get Skip to acquire the next image because the acquired image count has reached the max images count

                    var picturePath: String? = ""
                    if (mustSave) {
                        //note that the image saved is not the same image that went into the OCR!
                        picturePath = saveBitmapToAppExtStorageAsPNG(
                            baseContext,
                            bitmap,
                            "ocr_" + System.currentTimeMillis() + ".png"
                        )
                        Log.d("OCR", "SAVING PICTURE TO APP STORAGE - path $picturePath")
                    }

                    if (mustLogToServer){ logToServerDeviceDetails() }
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.e("OCR", "take picture / onerror "+exception.message)
                    cameraExecutor.shutdown()  //winning choice! destroy here and recreate in onResume
                }

            })
        } catch (e: Exception) {
            //log exception
            Log.e("OCR", "take picture / external exception "+e.message)
            cameraExecutor.shutdown() //winning choice! destroy here and recreate in onResume

        }
    }

    private fun createSampleBitmap(): Bitmap{
        //create a sample bitmap with random letters
        val bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888) //create a blank bitmap
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 50f
            style = android.graphics.Paint.Style.FILL
        }
        val random = Random(System.currentTimeMillis())
        for (i in 0 until 3) {
            val x = random.nextInt(bmp.width)
            val y = random.nextInt(bmp.height)
            val letter = ('A' + random.nextInt(26)) // Random letter A-Z
            canvas.drawText(letter.toString(), x.toFloat(), y.toFloat(), paint)
        }
        Log.d("OCR", "Bitmap created with random letters of size ${bmp.width}x${bmp.height}")
        return bmp
    }


    private fun CameraXActivity.doConcurrencyTest() {
        Log.d("OCR", "doConcurrencyTest() from thread ${Thread.currentThread().name}")


        val bmp1 = createSampleBitmap()
        val bmp2 = createSampleBitmap()


        //launch 2 functions in parallel using cor
        // outines
        runBlocking{
            val job1 = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
                Log.d("OCR-1", "Job 1 started on thread ${Thread.currentThread().name}")
                delay(/*random delay between 100 and 200*/ 100L + (Math.random() * 100).toLong())

                val localexecutor1 = Executors.newSingleThreadExecutor()
                Log.i("job1", "localexecutor1 is ${localexecutor1.toString()}")
                val futureResult: CompletableFuture<Array<Word>> =  textOCR_ONE?.detectWords( bmp1, localexecutor1 ) ?: CompletableFuture.completedFuture(emptyArray())
                val ocrResult1: List<Int>? = futureResult.thenApplyAsync { words: Array<Word> ->
                    words.mapNotNull { word ->
                        Log.i("job1", "Processing word: ${word.decodes[0].content} on thread ${Thread.currentThread().name}")
                    }
                }.join()

                Log.d("OCR-1", "Job 1 finished")
            }

            val job2 = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
                Log.d("OCR-2", "Job 2 started on thread ${Thread.currentThread().name}")
                delay(/*random delay between 100 and 200*/ 100L + (Math.random() * 100).toLong())

                val localexecutor2 = Executors.newSingleThreadExecutor()
                Log.i("job2", "localexecutor2 is ${localexecutor2.toString()}")
                val futureResult: CompletableFuture<Array<Word>> =  textOCR_TWO?.detectWords( bmp2, localexecutor2 ) ?: CompletableFuture.completedFuture(emptyArray())
                val ocrResult1: List<Int>? = futureResult.thenApplyAsync { words: Array<Word> ->
                    words.mapNotNull { word ->
                        Log.i("job2", "Processing word: ${word.decodes[0].content} on thread ${Thread.currentThread().name}")
                    }
                }.join()

                Log.d("OCR-2", "Job 2 finished")
            }

            joinAll(job1, job2) // Wait for both jobs to complete
        }

    }

    private fun doOCRthenProcessResults(imageFileName: String="", imageSourceDescription:String, bitmap: Bitmap) {
        var timeBefore = System.currentTimeMillis()

        //val ocrResults = textOCR!!.detect(bitmap, executor)                   /*HERE OCR IS PERFORMED ON THE IMAGE CAPTURED BY THE CAMERA AND SAVED IN MEMORY*/

//        var timeDelta = System.currentTimeMillis() - timeBefore
//        val ocrTook = timeDelta
//        Log.d("OCR", "OCR took $timeDelta ms")

        val futureResult: CompletableFuture<Array<Word>> =  textOCR?.detectWords( bitmap, executor ) ?: CompletableFuture.completedFuture(emptyArray())
        val ocrResult: List<DetectedObjectResult> = futureResult.thenApplyAsync { words: Array<Word> ->
            words.mapNotNull { word ->
                //val wid = getWordID(Pair(word.bbox.x[0].toInt(), word.bbox.y[0].toInt()))
                if(word.decodes[0].confidence < 0.9) return@mapNotNull null
                if(word.decodes[0].content.contains("?")) return@mapNotNull null
                if(word.decodes[0].content.length<3) return@mapNotNull null
                if(word.decodes[0].content.length>9) return@mapNotNull null

                val label = word.decodes[0].content
                if (true) {  //a word identifier (tracker) is missing, so using word itself.
                    val coords = (word.bbox.x[0].toInt() to word.bbox.y[0].toInt())
                    val rotatedCoordinates = coords

                    DetectedObjectResult(1f, label, rotatedCoordinates)
                } else null
            }
        }.join()


        val sbOCR: StringBuilder = StringBuilder()
        sbOCR.append("(" + imageFileName + ")\n<")


        for (i:DetectedObjectResult in ocrResult) {
            sbOCR.append(i.label)
            //sbOCR.append(" "+i.bbox.prob)
            sbOCR.append("\n")
        }
        sbOCR.append(">\n")
        Log.d("OCR", sbOCR.toString())
        if (sbOCR.toString().isNotEmpty())
            setOutputtextInMainThread(sbOCR.toString().replace("\n", " "))

        if (mustLogToServer) {
            sbOCR.append(imageSourceDescription+"\n")
            //sbOCR.append(" ("+ocrTook+"ms)\n")
            val _android_id = "A_ID=" + Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
            sbOCR.append(_android_id+"\n")
            logToServerOCRresults(sbOCR.toString().replace("\n", "&nbsp;"))
        }
    }


    private fun doOCRasWORDSthenProcessResults(imageFileName: String="", imageSourceDescription:String, bitmap: Bitmap) {
        var timeBefore = System.currentTimeMillis()

        val futureResult: CompletableFuture<Array<Word>> =  textOCR?.detectWords( bitmap , executor) ?: CompletableFuture.completedFuture(emptyArray())
        val ocrResult: List<DetectedObjectResult> = futureResult.thenApplyAsync { words: Array<Word> ->
            words.mapNotNull { word ->
                //val wid = getWordID(Pair(word.bbox.x[0].toInt(), word.bbox.y[0].toInt()))
                if(word.decodes[0].confidence < 0.9) return@mapNotNull null
                if(word.decodes[0].content.contains("?")) return@mapNotNull null
                if(word.decodes[0].content.length<3) return@mapNotNull null
                if(word.decodes[0].content.length>9) return@mapNotNull null

                val label = word.decodes[0].content
                if (true) {  //a word identifier (tracker) is missing, so using word itself.
                    val coords = (word.bbox.x[0].toInt() to word.bbox.y[0].toInt())
                    val rotatedCoordinates = coords

                    DetectedObjectResult(1f, label, rotatedCoordinates)
                } else null
            }
        }.join()

        var timeDelta = System.currentTimeMillis() - timeBefore
        val ocrTook = timeDelta
        Log.d("OCR", "OCR took $timeDelta ms")

        val sbOCR: StringBuilder = StringBuilder("OCR USING detectWords()\n")
        sbOCR.append("(" + imageFileName + ")\n<")
        for (i in ocrResult) {
            sbOCR.append( i.label + " " )
            //sbOCR.append(" "+i.bbox.prob)
            sbOCR.append("\n")
        }
        sbOCR.append(">\n")
        Log.d("OCR", sbOCR.toString())
        if (sbOCR.toString().isNotEmpty())
            setOutputtextInMainThread(sbOCR.toString().replace("\n", " "))

        if (mustLogToServer) {
            sbOCR.append(imageSourceDescription+"\n")
            sbOCR.append(" ("+ocrTook+"ms)\n")
            val _android_id = "A_ID=" + Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
            sbOCR.append(_android_id+"\n")
            logToServerOCRresults(sbOCR.toString().replace("\n", "&nbsp;"))
        }
    }

    fun rotateBitmapIfNeeded(imageProxy: ImageProxy): Bitmap {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if(isDeviceInPortrait())
            return rotateBitmap(imageProxy.toBitmap(), 1f*rotationDegrees)
        else
            return rotateBitmap(imageProxy.toBitmap(), 0f)
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap

        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun isDeviceInPortrait(): Boolean {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val rotation = windowManager.defaultDisplay.rotation
        return rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
    }

    private fun doOCRasPARAGRAPHSthenProcessResults(imageFileName: String="", imageSourceDescription:String, bitmap: Bitmap) {

        //reviewed on Jan2025 with async apis
        //ABOUT IMAGE ROTATION:  "AUTO-ROTATE" DISPLAY SETTINGS MUST BE On!  (else the image is rotated 90 degrees)
        //and with auto-rotate=off, isDeviceInPortrait() will always return true if it had been locked in portrait.

        var timeBefore = System.currentTimeMillis()

        var sbRes = StringBuilder()
        val futureResult: CompletableFuture<Array<TextParagraph>> =  textOCR?.detectParagraphs( bitmap, executor ) ?: CompletableFuture.completedFuture(emptyArray())
        val ocrResults: List<DetectedObjectResult> = futureResult.thenApplyAsync { textParagraph: Array<TextParagraph> ->

            textParagraph.mapNotNull {
                paragraph ->
                for (l in paragraph.lines) {
                    for (w in l.words){
                        val label = w.decodes[0].content
                        if (true) {  //a word identifier (tracker) is missing, so using word itself.
                            val coords = (w.bbox.x[0].toInt() to w.bbox.y[0].toInt())
                            val rotatedCoordinates = coords

                            DetectedObjectResult(1f, label, rotatedCoordinates)
                            sbRes.append(label)
                        } else null
                        sbRes.append(" ")
                    }
                    sbRes.append("\n")
                  }
                null
            }

        }.join()

        var timeDelta = System.currentTimeMillis() - timeBefore
        val ocrTook = timeDelta
        Log.d("OCR", "OCR detectParagraphs took $timeDelta ms")

        Log.d("OCR", sbRes.toString())
        if (sbRes.toString().isNotEmpty())
            setOutputtextInMainThread(sbRes.toString().replace("\n", " "))

//        if (mustLogToServer) {
//            sbOCR.append(imageSourceDescription+"\n")
//            sbOCR.append(" ("+ocrTook+"ms)\n")
//            val _android_id = "A_ID=" + Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
//            sbOCR.append(_android_id+"\n")
//            logToServerOCRresults(sbOCR.toString().replace("\n", "&nbsp;"))
//        }
    }



    private fun logToServerOCRresults(ocrResult:String){

        CallerLog().execute(ocrResult)
    }

    private fun logToServerDeviceDetails(){
        CallerLog().execute(getDeviceDetails())
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    //saves to content://media/external_primary/images/media/1000000067, 200KB IMAGES SAVED IN 200ms
    fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg") // Adjust MIME type as needed
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val contentResolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var itemUri: Uri? = null

        return runCatching {
            contentResolver.insert(collection, contentValues)?.also { uri ->
                itemUri = uri
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                   // bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream) // TAKES 2000+ms (4-5MB each)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream) // TAKES 200ms (200kB each)
                }
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
        }.getOrElse {
            // Handle exceptions
            if (itemUri != null) {
                contentResolver.delete(itemUri!!, null, null)
            }
            null
        }
    }

    fun saveBitmapToAppExtStorageAsJPG(context: Context, bitmap: Bitmap, filename: String, quality: Int = 80) : String{ // 80% quality=400kb, 50% quality=200kb   <100ms to save
        val file = File(context.externalCacheDir, filename)//externalCacheDir saves to /storage/emulated/0/Android/data/com.ndzl.cv.ocr/cache/ocr_1720099489030.jpg
        try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }
        } catch (e: IOException) {
            Log.e("OCR", "saveBitmapToAppExtStorageAsJPG / external storage exception "+e.message)
        }
        return file.absolutePath
    }

    fun saveBitmapToAppExtStorageAsPNG(context: Context, bitmap: Bitmap, filename: String, quality: Int = 100) : String{ // 80% quality=400kb, 50% quality=200kb   <100ms to save
        val file = File(context.externalCacheDir, filename)//externalCacheDir saves to /storage/emulated/0/Android/data/com.ndzl.cv.ocr/cache/ocr_1720099489030.jpg
        try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, quality, outputStream)
            }
        } catch (e: IOException) {
            Log.e("OCR", "saveBitmapToAppExtStorageAsPNG / external storage exception "+e.message)
        }
        return file.absolutePath
    }

    private fun setOutputtextInMainThread( txt: String){
        runOnUiThread {
            viewBinding.tvOCRout.text = txt
        }
    }

    @SuppressLint("NewApi")
    fun setNonSysWin(b: Boolean) {
        try {
            window.setHideOverlayWindows( b )
        } catch (e: java.lang.Exception) {
            Log.e("TAG", "onClickbtn_HideNonSysWin " + e.message)
        }
    }

    private fun getSupportedCameraResolutions() {
        Log.i("getSupportedCameraResolutions", "---------------------------------------------")
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                characteristics?.let {
                    Log.d("getSupportedCameraResolutions", "Camera ID: $cameraId")
                    Log.d("getSupportedCameraResolutions", "Lens Facing: ${it.get(CameraCharacteristics.LENS_FACING)}")
                    Log.d("getSupportedCameraResolutions", "Sensor Orientation: ${it.get(CameraCharacteristics.SENSOR_ORIENTATION)}")
                    Log.d("getSupportedCameraResolutions", "Flash Available: ${it.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)}")
                    Log.d("getSupportedCameraResolutions", "Max Zoom Ratio: ${it.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)}")
                }
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)


                val outputSizes: Array<Size>? = map?.getOutputSizes(ImageFormat.JPEG)
                outputSizes?.let {
                    for (size in it) {
                        Log.d("JPEG", "Supported resolution: ${size.width}x${size.height}")
                    }
                }

                val outputSizesRAWSENSOR: Array<Size>? = map?.getOutputSizes(ImageFormat.RAW_SENSOR)
                outputSizesRAWSENSOR?.let {
                    for (size in it) {
                        Log.d("RAWSENSOR", "Supported resolution: ${size.width}x${size.height}")
                    }
                }

                //for the ImageAnalysis.Analyzer - The image provided has format ImageFormat.YUV_420_888.
                val outputSizesYUV: Array<Size>? = map?.getOutputSizes(ImageFormat.YUV_420_888)
                outputSizesYUV?.let {
                    for (size in it) {
                        Log.d("YUV_420_888", "Supported resolution: ${size.width}x${size.height}")
                    }
                }

            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //resize bitmap
    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    //load png
    private fun loadBitmapFromAsset(): ByteArray {
        val inputStream = assets.open("technologies.png")
        val buffer = ByteArray(inputStream.available())
        inputStream.read(buffer)
        inputStream.close()
        return buffer
    }

//    //rotate bitmap
//    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
//        val matrix = android.graphics.Matrix()
//        matrix.postRotate(rotationDegrees.toFloat())
//        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
//    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun captureVideo() {}

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val previewUsecase = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCaptureUsecase = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(Surface.ROTATION_0)
                .setTargetResolution(Size(600, 800))
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, previewUsecase, imageCaptureUsecase)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun requestPermissions() {}

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


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
        private const val TAG = "CameraXApp"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
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


