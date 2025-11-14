package com.ndzl.aisuite.ocr.lowlevel

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.sin

class SoundMachine {


        val notetobeplayed: ByteArray = generateNote(10000.0, 80, 1000)

        val audioTrack = preparePCMData(notetobeplayed)

    private val handlerThread = HandlerThread("SoundMachineThread").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()

    init {
        handler.post {
            println("#NDZL SoundMachine/init handler.post1 Thread: ${Thread.currentThread().name}")
            lock.lock()
            try {
                condition.await() // Block the thread initially
            } finally {
                lock.unlock()
            }
        }
    }

    fun playSound() {
        println("#NDZL SoundMachine/playsound called Thread: ${Thread.currentThread().name}")
        lock.lock()
        condition.signal()
        lock.unlock()
        handler.post {
            try {
                audioTrack?.stop()
                audioTrack?.reloadStaticData()
                audioTrack?.play()
                println("#NDZL SoundMachine/handler.post2 Thread: ${Thread.currentThread().name}")
            } catch (e: Exception) {
                // Handle exception
            } finally {
                lock.lock()
                condition.await()
                lock.unlock()
            }
        }
    }

    private fun preparePCMData(data: ByteArray): AudioTrack? {
        try {
            val audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                    .setSampleRate(11025)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
                data.size,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack.write(data, 0, data.size)
            return audioTrack
        } catch (e: Exception) {
            return null
        }
    }

    private fun generateNote(frequency: Double, durationInMillis: Int, sampleRate: Int): ByteArray {
        val numSamples = (durationInMillis/1000.0 * sampleRate).toInt()
        val samples = ByteArray(numSamples)

        for (i in 0 until numSamples) {
            val time = i / 100.0
            samples[i] = ((sin(frequency * time) * 127.0).toInt().toByte())
        }

        return samples
    }


}