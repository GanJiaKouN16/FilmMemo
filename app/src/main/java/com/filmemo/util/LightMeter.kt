package com.filmemo.util

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

object LightMeter {

    // Valid ISO values
    val VALID_ISOS = listOf(
        25, 50, 100, 125, 160, 200, 250, 320, 400,
        500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400
    )

    // Common aperture values
    val COMMON_APERTURES = listOf(1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0)

    // Common shutter speeds
    val COMMON_SHUTTER_SPEEDS = listOf(
        "1/1000", "1/500", "1/250", "1/125", "1/60", "1/30", "1/15", "1/8",
        "1/4", "1/2", "1", "2", "4", "8", "15", "30"
    )

    fun isValidIso(iso: Int): Boolean = iso in VALID_ISOS

    /**
     * Calculate EV (Exposure Value)
     * EV = log2(N² / t)
     * N = aperture f-number, t = shutter speed in seconds
     */
    fun calculateEv(aperture: Double, shutterSpeedSeconds: Double): Double {
        if (shutterSpeedSeconds <= 0) return 0.0
        return ln(aperture.pow(2) / shutterSpeedSeconds) / ln(2.0)
    }

    /**
     * Compensate shutter speed to maintain same EV
     * t2 = N2² × t1 / N1²
     */
    fun compensateShutter(currentAperture: Double, currentShutter: Double, newAperture: Double): Double {
        if (currentAperture == 0.0) return 0.0
        return (newAperture.pow(2) * currentShutter) / currentAperture.pow(2)
    }

    /**
     * Compensate aperture to maintain same EV
     * N2 = sqrt(N1² × t2 / t1)
     */
    fun compensateAperture(currentAperture: Double, currentShutter: Double, newShutter: Double): Double {
        if (currentShutter == 0.0) return 0.0
        return sqrt(currentAperture.pow(2) * newShutter / currentShutter)
    }

    /**
     * Calculate Guide Number
     * GN = aperture × distance
     */
    fun calculateGuideNumber(aperture: Double, distance: Double): Double {
        return aperture * distance
    }

    /**
     * Calculate aperture from Guide Number
     * aperture = GN / distance
     */
    fun calculateApertureFromGN(guideNumber: Double, distance: Double): Double {
        if (distance == 0.0) return 0.0
        return guideNumber / distance
    }

    /**
     * Calculate flash power
     * power = (requiredGN / maxGN)²
     */
    fun calculateFlashPower(requiredGN: Double, maxGN: Double): Double {
        if (maxGN == 0.0) return 0.0
        return (requiredGN / maxGN).pow(2)
    }

    /**
     * Parse shutter speed string to seconds
     * "1/125" -> 0.008, "1/60" -> 0.0167, "1" -> 1.0, "2" -> 2.0
     */
    fun parseShutterSpeed(speed: String): Double? {
        val trimmed = speed.trim()
        if (trimmed.isEmpty()) return null

        return if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            if (parts.size != 2) return null
            val numerator = parts[0].toDoubleOrNull() ?: return null
            val denominator = parts[1].toDoubleOrNull() ?: return null
            if (denominator == 0.0) return null
            numerator / denominator
        } else {
            trimmed.toDoubleOrNull()
        }
    }

    /**
     * Format shutter speed seconds to display string
     * 0.008 -> "1/125", 1.0 -> "1s", 2.0 -> "2s"
     */
    fun formatShutterSpeed(seconds: Double): String {
        if (seconds <= 0) return ""
        if (seconds >= 1.0) return "${seconds}s"

        // Try to find a nice fraction
        val commonDenominators = listOf(2, 4, 8, 15, 30, 60, 125, 250, 500, 1000, 2000, 4000)
        for (den in commonDenominators) {
            val numerator = seconds * den
            if (kotlin.math.abs(numerator - kotlin.math.round(numerator)) < 0.01) {
                return "1/$den"
            }
        }
        return "${seconds}s"
    }
}
