package com.ndzl.aisuite.ocr.lowlevel

import android.graphics.Paint

data class BCEvent(val xavg: Float, val yavg: Float, val paint: Paint, val word:String, val timestamp: Long) {

}
