package com.ndzl.aisuite.ocr.lowlevel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.util.concurrent.ConcurrentHashMap

import java.util.concurrent.ConcurrentLinkedDeque

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
//    data class BCEvent(val x: Float, val y: Float, val paint: Paint, val timestamp: Timestamp)

    companion object {
        private const val TAG = "OverlayView"

        //GOOD RESOLUTIONS FOR BARCODES, 2FT AWAY:  1080x1920 (2Mpx) less accurate, 2048x1536 (3Mpx) ,  1920x2560 (5Mpx) slower
        var CAMERA_RESOLUTION_WIDTH = 480
        var CAMERA_RESOLUTION_HEIGHT = 640
        var CHOSEN_SCENE=0
        var ZOOM_RATIO=1.0


        var PREVIEW_WIDTH = 0
        var PREVIEW_HEIGHT = 0

//        var C1=0.toBigInteger()
//        var C2=0.toBigInteger()
//        var C3=0.toBigInteger()
//
//        var L1 = 0
//        var L2 = 0
//        var L3 = 0
    }

    val clq = ConcurrentLinkedDeque<BCEvent>() // java.util.concurrent.ConcurrentLinkedQueue<BCEvent>()
    val performanceSet: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val highlightSet: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val paintYellow = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    val paintGray = Paint().apply {
        color = Color.GRAY
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    val paintGreen = Paint().apply {
        color = Color.rgb(0, 190, 0)
        strokeWidth = 20f
        style = Paint.Style.STROKE
    }

    val paintRed = Paint().apply {
        color = Color.rgb(190, 0, 0) // Color.RED
        strokeWidth = 20f
        style = Paint.Style.FILL_AND_STROKE
    }

    val ink = Paint().apply {
        color = Color.YELLOW
        textSize = 50f
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val timestamp = System.currentTimeMillis() as Long

        if(clq.isNotEmpty())
            clq.map {

                if (timestamp - (it.timestamp) < 600) {
                        canvas.drawText( "[${it.word}]",it.xavg, it.yavg, ink)
                }
                else {

                    clq.remove(it)

                }
            }
            Log.d("drawCircle", "clq size = "+clq.size.toString())
    }

    fun mapFlippedToOriginalPixel(flipped: PointF, bitmapWidth: Int): PointF {
        // use -1 if you treat coordinates as integer pixel indices; for float coords this is safe too
        val origX = bitmapWidth - 1f - flipped.x
        val origY = flipped.y
        return PointF(origX, origY)
    }

    fun rotateAndScaleCoordinates(
        cameraCoords: Pair<Float, Float>,
        cameraWidth: Float, cameraHeight: Float,
        screenWidth: Float, screenHeight: Float
    ): Pair<Float, Float> {
        val scaleX = screenWidth / cameraWidth
        val scaleY = screenHeight / cameraHeight
        return Pair((cameraWidth - cameraCoords.second) * scaleX, cameraCoords.first * scaleY)
    }

}
