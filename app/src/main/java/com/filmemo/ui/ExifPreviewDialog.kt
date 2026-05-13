package com.filmemo.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.filmemo.data.Exposure
import com.filmemo.data.FilmDatabase
import com.filmemo.databinding.DialogExifPreviewBinding
import com.filmemo.util.ExposureCalculator
import com.filmemo.util.ExposureParams
import com.filmemo.util.LightMeter
import kotlin.math.pow
import kotlin.math.sqrt

class ExifPreviewDialog : DialogFragment() {

    private var _binding: DialogExifPreviewBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FilmDatabase
    private val calculator = ExposureCalculator()

    private var filmId: Long = 0
    private var filmIso: Int = 400
    private var cameraIso: Int = 400
    private var exifAperture: Double = 2.8
    private var exifShutterSpeed: String = "1/125"
    private var exifHasFlash: Boolean = false
    private var exifFlashGN: Double? = null
    private var photoPath: String? = null

    private val commonApertures = LightMeter.COMMON_APERTURES
    private val commonShutters = LightMeter.COMMON_SHUTTER_SPEEDS
    private var currentApertureIndex = 2
    private var currentShutterIndex = 5
    private var baseEv = 0.0

    private var onSaveListener: (() -> Unit)? = null

    companion object {
        fun newInstance(
            filmId: Long,
            iso: Int,
            cameraIso: Int = iso,
            aperture: Double,
            shutterSpeed: String,
            hasFlash: Boolean,
            flashGN: Double?,
            photoPath: String? = null
        ): ExifPreviewDialog {
            val dialog = ExifPreviewDialog()
            val args = Bundle()
            args.putLong("film_id", filmId)
            args.putInt("iso", iso)
            args.putInt("camera_iso", cameraIso)
            args.putDouble("aperture", aperture)
            args.putString("shutter_speed", shutterSpeed)
            args.putBoolean("has_flash", hasFlash)
            flashGN?.let { args.putDouble("flash_gn", it) }
            if (photoPath != null) args.putString("photo_path", photoPath)
            dialog.arguments = args
            return dialog
        }
    }

    fun setOnSaveListener(listener: () -> Unit) {
        onSaveListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            filmId = it.getLong("film_id")
            filmIso = it.getInt("iso")
            cameraIso = it.getInt("camera_iso", filmIso)
            exifAperture = it.getDouble("aperture")
            exifShutterSpeed = it.getString("shutter_speed") ?: "1/125"
            exifHasFlash = it.getBoolean("has_flash")
            if (it.containsKey("flash_gn")) {
                exifFlashGN = it.getDouble("flash_gn")
            }
            photoPath = it.getString("photo_path")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogExifPreviewBinding.inflate(LayoutInflater.from(context))
        db = FilmDatabase(requireContext())

        // Convert EXIF values to target film ISO
        val exifShutterSeconds = LightMeter.parseShutterSpeed(exifShutterSpeed) ?: 0.0167
        baseEv = LightMeter.calculateEv(exifAperture, exifShutterSeconds)
        val exposureParams = ExposureParams(exifAperture, exifShutterSeconds, cameraIso)
        val converted = calculator.convertExposureForTargetIso(exposureParams, filmIso, keepAperture = true)

        if (converted != null) {
            val convertedAperture = calculator.snapToStandardAperture(converted.aperture)
            val convertedShutter = calculator.snapToStandardShutter(converted.shutterSpeed)

            currentApertureIndex = commonApertures.indexOf(convertedAperture).coerceAtLeast(0)
            currentShutterIndex = commonShutters.indexOf(convertedShutter).coerceAtLeast(0)
        }

        // Show original EXIF values
        binding.tvOriginal.text = "原始EXIF:  ${calculator.formatAperture(exifAperture)}  $exifShutterSpeed  ISO$cameraIso"

        updateConvertedText()
        setupSeekbars()

        binding.switchFlash.isChecked = exifHasFlash
        binding.layoutFlashGn.visibility = if (exifHasFlash) View.VISIBLE else View.GONE
        if (exifFlashGN != null) {
            binding.etFlashGn.setText(exifFlashGN.toString())
        }

        binding.switchFlash.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutFlashGn.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onStart() {
        super.onStart()

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            saveExposure()
        }
    }

    private fun setupSeekbars() {
        binding.seekbarAperture.max = commonApertures.size - 1
        binding.seekbarAperture.progress = currentApertureIndex
        binding.tvApertureValue.text = "f/${commonApertures[currentApertureIndex]}"

        binding.seekbarAperture.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentApertureIndex = progress
                    binding.tvApertureValue.text = "f/${commonApertures[currentApertureIndex]}"
                    recalculateFromAperture()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekbarShutter.max = commonShutters.size - 1
        binding.seekbarShutter.progress = currentShutterIndex
        binding.tvShutterValue.text = commonShutters[currentShutterIndex]

        binding.seekbarShutter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentShutterIndex = progress
                    binding.tvShutterValue.text = commonShutters[currentShutterIndex]
                    recalculateFromShutter()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun recalculateFromAperture() {
        val newAperture = commonApertures[currentApertureIndex]
        val newShutterSeconds = newAperture.pow(2) / 2.0.pow(baseEv)

        var bestIndex = 0
        var bestDiff = Double.MAX_VALUE
        commonShutters.forEachIndexed { index, shutterStr ->
            val shutterSeconds = LightMeter.parseShutterSpeed(shutterStr) ?: return@forEachIndexed
            val diff = kotlin.math.abs(shutterSeconds - newShutterSeconds)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIndex = index
            }
        }

        currentShutterIndex = bestIndex
        binding.seekbarShutter.progress = currentShutterIndex
        binding.tvShutterValue.text = commonShutters[currentShutterIndex]
        updateConvertedText()
    }

    private fun recalculateFromShutter() {
        val newShutterSeconds = LightMeter.parseShutterSpeed(commonShutters[currentShutterIndex]) ?: 0.0167
        val newAperture = kotlin.math.sqrt(2.0.pow(baseEv) * newShutterSeconds)

        var bestIndex = 0
        var bestDiff = Double.MAX_VALUE
        commonApertures.forEachIndexed { index, aperture ->
            val diff = kotlin.math.abs(aperture - newAperture)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIndex = index
            }
        }

        currentApertureIndex = bestIndex
        binding.seekbarAperture.progress = currentApertureIndex
        binding.tvApertureValue.text = "f/${commonApertures[currentApertureIndex]}"
        updateConvertedText()
    }

    private fun updateConvertedText() {
        val aperture = commonApertures[currentApertureIndex]
        val shutter = commonShutters[currentShutterIndex]
        binding.tvConverted.text = "胶片挡位:  ${calculator.formatAperture(aperture)}  $shutter  ISO${filmIso}"
    }

    private fun saveExposure() {
        val apertureValue = commonApertures[currentApertureIndex]
        val shutterStr = commonShutters[currentShutterIndex]

        val shutterSeconds = LightMeter.parseShutterSpeed(shutterStr)
        if (shutterSeconds == null || shutterSeconds <= 0) {
            Toast.makeText(context, "无效的快门速度", Toast.LENGTH_SHORT).show()
            return
        }

        val hasFlashValue = binding.switchFlash.isChecked
        var flashGNValue: Double? = null

        if (hasFlashValue) {
            val gnStr = binding.etFlashGn.text.toString().trim()
            if (gnStr.isNotEmpty()) {
                flashGNValue = gnStr.toDoubleOrNull()
                if (flashGNValue == null || flashGNValue <= 0) {
                    binding.etFlashGn.error = "无效的闪光指数"
                    return
                }
            }
        }

        val exposure = Exposure(
            filmId = filmId,
            aperture = apertureValue,
            shutterSpeed = shutterStr,
            iso = filmIso,
            hasFlash = hasFlashValue,
            flashGN = flashGNValue
        )

        db.insertExposure(exposure)
        Toast.makeText(context, "曝光记录已保存", Toast.LENGTH_SHORT).show()

        onSaveListener?.invoke()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
