package com.example.android_camera

import com.example.android_camera.MiniPID
import com.example.android_camera.PodUsbSerialService
import com.example.android_camera.BoxWithText
import com.example.android_camera.CommanderPacket

class Driver internal constructor() {
    private val P = 0.5
    private val I = 0.0
    private val D = 0.0
    private val sizeSetpoint = 245760.0
    private val centerSetpoint = 340.0
    private var frames_empty = 0.0
    private val forwardPID = MiniPID(P, I, D)
    private val sidePID = MiniPID(P, I, D)


    fun drive(mPodUsbSerialService: PodUsbSerialService?, detectedObjects: List<BoxWithText>) {
        if (mPodUsbSerialService == null) {
            return
        }
        if (detectedObjects.isEmpty()) {
            if (frames_empty >= 3) {
                driveCar(mPodUsbSerialService, 0f, 0f)
            }
            frames_empty++
            return
        }

        val center = calculateCenter(detectedObjects)
        val size = calculateSize(detectedObjects)
        driveCar(mPodUsbSerialService,forwardPID.getOutput(size).toFloat(),sidePID.getOutput(center).toFloat())

    }

    private fun driveCar(mPodUsbSerialService: PodUsbSerialService, forward: Float, side: Float) {
        val cp = CommanderPacket(side, forward, 0f, 14000.toShort().toUShort())
        mPodUsbSerialService.usbSendData(cp.toByteArray())
    }

    private fun calculateCenter(detectedObjects: List<BoxWithText>): Double {
        var center = 0.0
        for (i in detectedObjects.indices) {
            /*var indiv_center = 0.0
            detectedObjects[i].box.centerX()
            indiv_center = if (detectedObjects[i].box.left > detectedObjects[i].box.right) {
                ((detectedObjects[i].box.left - detectedObjects[i].box.right) / 2 + detectedObjects[i].box.right).toDouble()
            } else {
                ((detectedObjects[i].box.right - detectedObjects[i].box.left) / 2 + detectedObjects[i].box.left).toDouble()
            }*/
            center += detectedObjects[i].box.centerX()
        }
        center /= detectedObjects.size
        return center
    }

    private fun calculateSize(detectedObjects: List<BoxWithText>): Double {
        var size = 0.0
        for (i in detectedObjects.indices) {
            size += detectedObjects[i].box.width() * detectedObjects[i].box.height()
        }
        size /= detectedObjects.size
        return size
    }

    init {
        sidePID.setSetpoint(centerSetpoint)
        forwardPID.setSetpoint(sizeSetpoint)
    }
}