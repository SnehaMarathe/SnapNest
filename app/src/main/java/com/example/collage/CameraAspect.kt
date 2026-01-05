package com.example.collage

import androidx.camera.core.AspectRatio
import kotlin.math.abs

object CameraAspect {
    fun closestCameraXAspect(slotAspect: Float): Int {
        val r43 = 4f / 3f
        val r169 = 16f / 9f
        return if (abs(slotAspect - r43) <= abs(slotAspect - r169)) {
            AspectRatio.RATIO_4_3
        } else {
            AspectRatio.RATIO_16_9
        }
    }
}
