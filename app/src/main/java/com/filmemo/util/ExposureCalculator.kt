package com.filmemo.util

import android.media.ExifInterface
import kotlin.math.sqrt

data class ExposureParams(
    val aperture: Double,      // f-number, e.g. 2.8
    val shutterSpeed: Double,  // seconds, e.g. 0.0167 (1/60)
    val iso: Int               // ISO sensitivity, e.g. 200
)

data class ConvertedExposure(
    val aperture: Double,
    val shutterSpeed: Double,
    val targetIso: Int,
    val keepAperture: Boolean
)

class ExposureCalculator {

    /**
     * 从图片EXIF读取曝光参数
     */
    fun readExifFromImage(imagePath: String): ExposureParams? {
        return try {
            val exif = ExifInterface(imagePath)

            val aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)

            val exposureTimeRational = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
            val shutterSpeed = exposureTimeRational?.let { parseRationalToDouble(it) } ?: 0.0

            val iso = exif.getAttributeInt(ExifInterface.TAG_ISO, 0)

            if (aperture == 0.0 || shutterSpeed == 0.0 || iso == 0) {
                return null
            }
            ExposureParams(aperture, shutterSpeed, iso)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseRationalToDouble(rational: String): Double {
        val parts = rational.split('/')
        return if (parts.size == 2) {
            parts[0].toDoubleOrNull()?.div(parts[1].toDoubleOrNull() ?: 1.0) ?: 0.0
        } else {
            rational.toDoubleOrNull() ?: 0.0
        }
    }

    /**
     * 曝光转换算法
     * 相同场景亮度下，曝光值(EV)与ISO成正比
     * (N² / t) ∝ ISO
     *
     * @param currentParams 原始曝光参数
     * @param targetIso 目标胶片ISO
     * @param keepAperture true=调整快门速度, false=调整光圈
     */
    fun convertExposureForTargetIso(
        currentParams: ExposureParams,
        targetIso: Int,
        keepAperture: Boolean = true
    ): ConvertedExposure? {
        if (targetIso <= 0) return null
        val oldIso = currentParams.iso
        if (oldIso == 0) return null

        val ratio = oldIso.toDouble() / targetIso.toDouble()

        return if (keepAperture) {
            val newShutter = currentParams.shutterSpeed * ratio
            ConvertedExposure(
                aperture = currentParams.aperture,
                shutterSpeed = newShutter,
                targetIso = targetIso,
                keepAperture = true
            )
        } else {
            val newAperture = sqrt(currentParams.aperture * currentParams.aperture / ratio)
            ConvertedExposure(
                aperture = newAperture,
                shutterSpeed = currentParams.shutterSpeed,
                targetIso = targetIso,
                keepAperture = false
            )
        }
    }

    /**
     * 将快门速度转换为最接近的胶片标准挡位
     */
    fun snapToStandardShutter(seconds: Double): String {
        val standardShutters = listOf(
            1.0/1000, 1.0/500, 1.0/250, 1.0/125, 1.0/60, 1.0/30, 1.0/15, 1.0/8,
            1.0/4, 1.0/2, 1.0, 2.0, 4.0, 8.0, 15.0, 30.0
        )

        var bestMatch = standardShutters[0]
        var bestDiff = Double.MAX_VALUE

        for (shutter in standardShutters) {
            val diff = kotlin.math.abs(shutter - seconds)
            if (diff < bestDiff) {
                bestDiff = diff
                bestMatch = shutter
            }
        }

        return formatShutterSpeed(bestMatch)
    }

    /**
     * 将光圈转换为最接近的胶片标准挡位
     */
    fun snapToStandardAperture(fNumber: Double): Double {
        val standardApertures = listOf(1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0)

        var bestMatch = standardApertures[0]
        var bestDiff = Double.MAX_VALUE

        for (aperture in standardApertures) {
            val diff = kotlin.math.abs(aperture - fNumber)
            if (diff < bestDiff) {
                bestDiff = diff
                bestMatch = aperture
            }
        }

        return bestMatch
    }

    fun formatShutterSpeed(seconds: Double): String {
        return if (seconds >= 1.0) {
            String.format("%.0f", seconds)
        } else {
            val denominator = (1.0 / seconds).toInt()
            "1/$denominator"
        }
    }

    fun formatAperture(fNumber: Double): String = String.format("%.1f", fNumber)
}
