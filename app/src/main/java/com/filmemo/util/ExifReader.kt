package com.filmemo.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

data class ExifData(
    val aperture: Double,
    val shutterSpeed: String,
    val iso: Int,
    val hasFlash: Boolean,
    val flashGN: Double?
)

object ExifReader {
    fun readExifFromUri(context: Context, uri: Uri): ExifData? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val exif = ExifInterface(inputStream)

            // Read aperture
            val aperture = exif.getAttributeDouble(ExifInterface.TAG_APERTURE_VALUE, 0.0)

            // Read shutter speed
            val exposureTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
            val shutterSpeed = if (exposureTime > 0) {
                if (exposureTime < 1) {
                    "1/${(1 / exposureTime).toInt()}"
                } else {
                    exposureTime.toInt().toString()
                }
            } else {
                null
            }

            // Read ISO
            val iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0)

            // Read flash
            val flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, 0)
            val hasFlash = flash != 0

            inputStream.close()

            return if (aperture > 0 && shutterSpeed != null && iso > 0) {
                ExifData(aperture, shutterSpeed, iso, hasFlash, null)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
