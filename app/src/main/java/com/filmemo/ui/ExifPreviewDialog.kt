package com.filmemo.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.filmemo.data.Exposure
import com.filmemo.data.FilmDatabase
import com.filmemo.databinding.DialogExifPreviewBinding
import com.filmemo.util.LightMeter

class ExifPreviewDialog : DialogFragment() {

    private var _binding: DialogExifPreviewBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FilmDatabase

    private var filmId: Long = 0
    private var iso: Int = 400
    private var aperture: Double = 2.8
    private var shutterSpeed: String = "1/125"
    private var hasFlash: Boolean = false
    private var flashGN: Double? = null

    private var onSaveListener: (() -> Unit)? = null

    companion object {
        fun newInstance(
            filmId: Long,
            iso: Int,
            aperture: Double,
            shutterSpeed: String,
            hasFlash: Boolean,
            flashGN: Double?
        ): ExifPreviewDialog {
            val dialog = ExifPreviewDialog()
            val args = Bundle()
            args.putLong("film_id", filmId)
            args.putInt("iso", iso)
            args.putDouble("aperture", aperture)
            args.putString("shutter_speed", shutterSpeed)
            args.putBoolean("has_flash", hasFlash)
            flashGN?.let { args.putDouble("flash_gn", it) }
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
            iso = it.getInt("iso")
            aperture = it.getDouble("aperture")
            shutterSpeed = it.getString("shutter_speed") ?: "1/125"
            hasFlash = it.getBoolean("has_flash")
            if (it.containsKey("flash_gn")) {
                flashGN = it.getDouble("flash_gn")
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogExifPreviewBinding.inflate(LayoutInflater.from(context))
        db = FilmDatabase(requireContext())

        // Set initial values
        binding.etAperture.setText(aperture.toString())
        binding.etShutter.setText(shutterSpeed)
        binding.tvIso.text = iso.toString()

        // Setup flash switch
        binding.switchFlash.isChecked = hasFlash
        binding.layoutFlashGn.visibility = if (hasFlash) View.VISIBLE else View.GONE
        if (flashGN != null) {
            binding.etFlashGn.setText(flashGN.toString())
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

    private fun saveExposure() {
        val apertureStr = binding.etAperture.text.toString().trim()
        val shutterStr = binding.etShutter.text.toString().trim()

        if (apertureStr.isEmpty()) {
            binding.etAperture.error = "请输入光圈值"
            return
        }

        if (shutterStr.isEmpty()) {
            binding.etShutter.error = "请输入快门速度"
            return
        }

        val apertureValue = apertureStr.toDoubleOrNull()
        if (apertureValue == null || apertureValue <= 0) {
            binding.etAperture.error = "无效的光圈值"
            return
        }

        val shutterSeconds = LightMeter.parseShutterSpeed(shutterStr)
        if (shutterSeconds == null || shutterSeconds <= 0) {
            binding.etShutter.error = "无效的快门速度"
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

        // Save to database
        val exposure = Exposure(
            filmId = filmId,
            aperture = apertureValue,
            shutterSpeed = shutterStr,
            iso = iso,
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
