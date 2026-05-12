package com.filmemo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.filmemo.MainActivity
import com.filmemo.R
import com.filmemo.data.FilmDatabase
import com.filmemo.databinding.FragmentLightmeterBinding
import com.filmemo.util.ExifReader
import com.filmemo.util.LightMeter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LightmeterFragment : Fragment() {

    private var _binding: FragmentLightmeterBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FilmDatabase

    // Common values
    private val commonApertures = listOf(1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0)
    private val commonShutters = listOf("1/1000", "1/500", "1/250", "1/125", "1/60", "1/30", "1/15", "1/8", "1/4", "1/2", "1", "2", "4", "8", "15", "30")
    private val commonDistances = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0)

    // Current values
    private var currentApertureIndex = 2 // f/2.8
    private var currentShutterIndex = 5 // 1/60
    private var currentISO = 400
    private var flashEnabled = false
    private var flashApertureIndex = 2
    private var flashDistanceIndex = 3 // 2.0m

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLightmeterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FilmDatabase(requireContext())

        // Get current film ISO
        val currentFilmId = db.getCurrentFilmId()
        if (currentFilmId != null) {
            val film = db.getFilmById(currentFilmId)
            film?.let {
                currentISO = it.iso
                binding.tvIsoValue.text = currentISO.toString()
                binding.seekbarIso.max = 100
                binding.seekbarIso.progress = 50
            }
        }

        // Setup exposure seekbars
        setupExposureSeekbars()

        // Setup flash switch
        binding.switchFlash.setOnCheckedChangeListener { _, isChecked ->
            flashEnabled = isChecked
            binding.layoutFlashContent.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateFlashAperture()
        }

        // Setup flash seekbars
        setupFlashSeekbars()

        // Setup start lightmeter button
        binding.btnStartLightmeter.setOnClickListener {
            startLightmeter()
        }

        // Initial calculation
        updateEV()
    }

    private fun setupExposureSeekbars() {
        // Aperture seekbar
        binding.seekbarAperture.max = commonApertures.size - 1
        binding.seekbarAperture.progress = currentApertureIndex
        binding.tvApertureValue.text = "f/${commonApertures[currentApertureIndex]}"

        binding.seekbarAperture.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentApertureIndex = progress
                    binding.tvApertureValue.text = "f/${commonApertures[currentApertureIndex]}"
                    updateShutterFromAperture()
                    updateFlashAperture()
                    updateEV()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Shutter seekbar
        binding.seekbarShutter.max = commonShutters.size - 1
        binding.seekbarShutter.progress = currentShutterIndex
        binding.tvShutterValue.text = commonShutters[currentShutterIndex]

        binding.seekbarShutter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentShutterIndex = progress
                    binding.tvShutterValue.text = commonShutters[currentShutterIndex]
                    updateApertureFromShutter()
                    updateFlashAperture()
                    updateEV()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupFlashSeekbars() {
        // Flash aperture seekbar
        binding.seekbarFlashAperture.max = commonApertures.size - 1
        binding.seekbarFlashAperture.progress = flashApertureIndex
        binding.tvFlashApertureValue.text = "f/${commonApertures[flashApertureIndex]}"

        binding.seekbarFlashAperture.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    flashApertureIndex = progress
                    binding.tvFlashApertureValue.text = "f/${commonApertures[flashApertureIndex]}"
                    updateExposureAperture()
                    updateGN()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Flash distance seekbar
        binding.seekbarFlashDistance.max = commonDistances.size - 1
        binding.seekbarFlashDistance.progress = flashDistanceIndex
        binding.tvFlashDistanceValue.text = "${commonDistances[flashDistanceIndex]}米"

        binding.seekbarFlashDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    flashDistanceIndex = progress
                    binding.tvFlashDistanceValue.text = "${commonDistances[flashDistanceIndex]}米"
                    updateGN()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateShutterFromAperture() {
        val currentShutterSeconds = LightMeter.parseShutterSpeed(commonShutters[currentShutterIndex]) ?: return
        val currentAperture = commonApertures[currentApertureIndex]
        val ev = LightMeter.calculateEv(currentAperture, currentShutterSeconds)

        // Find best matching shutter speed for new aperture
        val newAperture = commonApertures[currentApertureIndex]
        val newShutterSeconds = LightMeter.compensateShutter(commonApertures[currentApertureIndex], currentShutterSeconds, newAperture)

        // Find closest common shutter speed
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
    }

    private fun updateApertureFromShutter() {
        val currentShutterSeconds = LightMeter.parseShutterSpeed(commonShutters[currentShutterIndex]) ?: return
        val currentAperture = commonApertures[currentApertureIndex]
        val ev = LightMeter.calculateEv(currentAperture, currentShutterSeconds)

        // Find best matching aperture for new shutter speed
        val newShutterSeconds = LightMeter.parseShutterSpeed(commonShutters[currentShutterIndex]) ?: return
        val newAperture = LightMeter.compensateAperture(currentAperture, currentShutterSeconds, newShutterSeconds)

        // Find closest common aperture
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
    }

    private fun updateFlashAperture() {
        if (flashEnabled) {
            flashApertureIndex = currentApertureIndex
            binding.seekbarFlashAperture.progress = flashApertureIndex
            binding.tvFlashApertureValue.text = "f/${commonApertures[flashApertureIndex]}"
            updateGN()
        }
    }

    private fun updateExposureAperture() {
        currentApertureIndex = flashApertureIndex
        binding.seekbarAperture.progress = currentApertureIndex
        binding.tvApertureValue.text = "f/${commonApertures[currentApertureIndex]}"
        updateShutterFromAperture()
        updateEV()
    }

    private fun updateEV() {
        val aperture = commonApertures[currentApertureIndex]
        val shutterSeconds = LightMeter.parseShutterSpeed(commonShutters[currentShutterIndex]) ?: return
        val ev = LightMeter.calculateEv(aperture, shutterSeconds)
        binding.tvEvValue.text = "EV值: %.1f".format(ev)
    }

    private fun updateGN() {
        if (flashEnabled) {
            val aperture = commonApertures[flashApertureIndex]
            val distance = commonDistances[flashDistanceIndex]
            val gn = LightMeter.calculateGuideNumber(aperture, distance)
            binding.tvGnValue.text = "闪光指数 GN: %.1f".format(gn)
        }
    }

    private fun startLightmeter() {
        // Check if there are any films
        val films = db.getAllFilms()
        if (films.isEmpty()) {
            Toast.makeText(context, "请先创建胶卷", Toast.LENGTH_SHORT).show()
            // Navigate to films tab
            (activity as? MainActivity)?.let {
                it.binding.bottomNav.selectedItemId = R.id.nav_films
            }
            return
        }

        // Check camera permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
            return
        }

        openCamera()
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(context, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    private lateinit var photoUri: Uri

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // Read EXIF
            val exifData = ExifReader.readExifFromUri(requireContext(), photoUri)
            if (exifData != null) {
                showExifPreviewDialog(exifData)
            } else {
                Toast.makeText(context, "无法读取EXIF信息，请重新拍摄", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCamera() {
        val photoFile = createTempImageFile()
        photoUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", photoFile)
        takePicture.launch(photoUri)
    }

    private fun createTempImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().cacheDir
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun showExifPreviewDialog(exifData: com.filmemo.util.ExifData) {
        val currentFilmId = db.getCurrentFilmId() ?: return
        val film = db.getFilmById(currentFilmId) ?: return

        val dialog = ExifPreviewDialog.newInstance(
            filmId = currentFilmId,
            iso = film.iso,
            aperture = exifData.aperture,
            shutterSpeed = exifData.shutterSpeed,
            hasFlash = exifData.hasFlash,
            flashGN = exifData.flashGN
        )
        dialog.show(parentFragmentManager, "exif_preview")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
