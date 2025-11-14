package com.ndzl.aisuite.ocr.lowlevel

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.util.Range
import android.util.Rational
import android.util.Size
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import zebra.BarcodeDetector.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var binding: ActivitySettingsBinding

    val soundMachine = SoundMachine()

    companion object {
        var isLicenseOK = false
    }







    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("SettingsPreferences", MODE_PRIVATE)
        binding=ActivitySettingsBinding.inflate(layoutInflater).apply {
            setContentView(root)
        }
        loadSettings()

        soundMachine.playSound()

     //   getAndLogBackCameraProperties( this )


    }



    @OptIn(ExperimentalCamera2Interop::class)
    private fun getSupportedCameraFormat() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val camera = cameraProvider.bindToLifecycle(this, cameraSelector)
            val cameraInfo = camera.cameraInfo as CameraInfo
            val cameraCharacteristics = cameraInfo.cameraState

//            val cameraFormats = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
//            val outputFormats = cameraFormats!!.outputFormats
//            for (outputFormat in outputFormats) {
//                Log.d("CameraX", "Supported format: $outputFormat")
//            }
            val camera2CameraInfo = cameraInfo as? Camera2CameraInfo
            val characteristics = Camera2CameraInfo.extractCameraCharacteristics(cameraInfo)
            val streamConfigurationMap = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val supportedFrameRates = 0


        }, ContextCompat.getMainExecutor(this))
    }

    fun formatSize(size: Size): String {
        return "(${size.width}x${size.height})"
    }

    @SuppressLint("ServiceCast")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getAndLogBackCameraProperties(context: Context) {
        val cameraManager = context.getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    Log.i("System.out", "Device Fingerprint: ${Build.FINGERPRINT}")

                    Log.i("System.out", "Camera ID: $cameraId")

                    val lensInfoAvailableFocalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf()
                    val lensInfoMinimumFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0.0f
                    val lensInfoHyperfocalDistance = characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE) ?: 0.0f
                    val sensorInfoPhysicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: Size(0, 0)
                    val cameraModelDescription = "Focal Lengths: [${lensInfoAvailableFocalLengths.joinToString()}], " +
                            "Min Focus: $lensInfoMinimumFocusDistance, " +
                            "Hyperfocal: $lensInfoHyperfocalDistance, " +
                            "Sensor Size: ${(sensorInfoPhysicalSize)}"
                    Log.i("System.out", "Camera Model: $cameraModelDescription")

                    val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                    Log.i("CameraProperties", "sensorOrientation: $sensorOrientation")

                    val availableCapabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.forEach { println ("REQUEST_AVAILABLE_CAPABILITIES "+it) }
                    //Log.i("CameraProperties", "availableCapabilities: $availableCapabilities")

                    val availableAeModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.forEach { println ("CONTROL_AE_AVAILABLE_MODES "+it) }
                    //Log.i("CameraProperties", "availableAeModes: $availableAeModes")

                    val availableAfModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.forEach { println ("CONTROL_AF_AVAILABLE_MODES "+it) }
                    Log.i("CameraProperties", "availableAfModes: $availableAfModes")

                    val availableAwbModes = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.forEach { println ("CONTROL_AWB_AVAILABLE_MODES "+it) }
                    Log.i("CameraProperties", "availableAwbModes: $availableAwbModes")

                    val aeCompensationRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(0, 0)
                    Log.i("CameraProperties", "aeCompensationRange: $aeCompensationRange")

                    val aeCompensationStep = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: Rational(0, 1)
                    Log.i("CameraProperties", "aeCompensationStep: $aeCompensationStep")

                    val maxRegionsAe = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0
                    Log.i("CameraProperties", "maxRegionsAe: $maxRegionsAe")

                    val maxRegionsAf = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
                    Log.i("CameraProperties", "maxRegionsAf: $maxRegionsAf")

                    val maxRegionsAwb = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) ?: 0
                    Log.i("CameraProperties", "maxRegionsAwb: $maxRegionsAwb")

                    val physicalCameraIds = characteristics.physicalCameraIds.toList().forEach { println ("physicalCameraIds "+it) }
                    Log.i("CameraProperties", "physicalCameraIds: $physicalCameraIds")

                    val requestMaxNumOutputRaw = characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW) ?: 0
                    Log.i("CameraProperties", "requestMaxNumOutputRaw: $requestMaxNumOutputRaw")

//                    val requestMaxNumOutputProcessed = characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROCESSED) ?: 0
//                    Log.i("CameraProperties", "requestMaxNumOutputProcessed: $requestMaxNumOutputProcessed")
//
//                    val requestMaxNumOutputStall = characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_STALL) ?: 0
//                    Log.i("CameraProperties", "requestMaxNumOutputStall: $requestMaxNumOutputStall")
//
//                    val scalerStreamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: StreamConfigurationMap(arrayOf(), arrayOf())
//                    Log.i("CameraProperties", "scalerStreamConfigurationMap: $scalerStreamConfigurationMap")

                    val sensorInfoActiveArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect(0, 0, 0, 0)
                    Log.i("CameraProperties", "sensorInfoActiveArraySize: $sensorInfoActiveArraySize")

                    val sensorInfoPixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: Size(0,0)
                    Log.i("CameraProperties", "sensorInfoPixelArraySize: $sensorInfoPixelArraySize")

                    val sensorInfoSensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: Range(0, 0)
                    Log.i("CameraProperties", "sensorInfoSensitivityRange: $sensorInfoSensitivityRange")

                    val sensorInfoTimestampSource = characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ?: 0
                    Log.i("CameraProperties", "sensorInfoTimestampSource: $sensorInfoTimestampSource")

                    val flashInfoAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    Log.i("CameraProperties", "flashInfoAvailable: $flashInfoAvailable")

                    val controlAeAvailableAntisandingModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)?.forEach { println ("CONTROL_AE_AVAILABLE_ANTIBANDING_MODES "+it) }
                    Log.i("CameraProperties", "controlAeAvailableAntisandingModes: $controlAeAvailableAntisandingModes")


                    val controlAwbLockAvailable = characteristics.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) ?: false
                    Log.i("CameraProperties", "controlAwbLockAvailable: $controlAwbLockAvailable")

                    val controlMaxRegionsAe = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0
                    Log.i("CameraProperties", "controlMaxRegionsAe: $controlMaxRegionsAe")

                    val controlMaxRegionsAf = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
                    Log.i("CameraProperties", "controlMaxRegionsAf: $controlMaxRegionsAf")

                    val controlMaxRegionsAwb = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) ?: 0
                    Log.i("CameraProperties", "controlMaxRegionsAwb: $controlMaxRegionsAwb")

                    val controlAvailableEffects = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)?.forEach { println ("CONTROL_AVAILABLE_EFFECTS "+it) }
                    Log.i("CameraProperties", "controlAvailableEffects: $controlAvailableEffects")

                    val controlAvailableSceneModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)?.forEach { println ("CONTROL_AVAILABLE_SCENE_MODES "+it) }
                    Log.i("CameraProperties", "controlAvailableSceneModes: $controlAvailableSceneModes")

                    val controlAvailableVideoStabilizationModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.forEach { println ("CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES "+it) }
                    Log.i("CameraProperties", "controlAvailableVideoStabilizationModes: $controlAvailableVideoStabilizationModes")

                    val controlAvailableModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_MODES)?.forEach { println ("CONTROL_AVAILABLE_MODES "+it) }
                    Log.i("CameraProperties", "controlAvailableModes: $controlAvailableModes")

                    break
                }
            }
        } catch (e: Exception) {
            Log.e("CameraProperties", "Error getting camera properties", e)
        }
    }




    fun onClickbtn_SUBMIT(view: View) {

        sharedPreferences.edit().apply {
            putInt("IMAGESIZE", binding.spinnerIMAGESIZE.selectedItemPosition)
            putInt("SCENE", binding.spinnerSCENE.selectedItemPosition)
            putInt("ZOOM", binding.spinnerZOOM.selectedItemPosition)




            apply()
        }
        finish()
    }

    private fun loadSettings() {
        val imageSize = sharedPreferences.getInt("IMAGESIZE", 1)
        val scene = sharedPreferences.getInt("SCENE", 0)
        val zoom = sharedPreferences.getInt("ZOOM", 0)
        val barcodesToHighlight = sharedPreferences.getString("BARCODESTOHIGHLIGHT", "")

        binding.spinnerIMAGESIZE.setSelection(imageSize)
        binding.spinnerSCENE.setSelection(scene)
        binding.spinnerZOOM.setSelection(zoom)




    }
}