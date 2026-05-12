package com.filmemo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.filmemo.data.Exposure
import com.filmemo.data.Film
import com.filmemo.data.FilmDatabase
import com.filmemo.databinding.FragmentFilmDetailBinding
import com.filmemo.databinding.ItemExposureDetailBinding
import com.filmemo.util.ExifReader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilmDetailFragment : Fragment() {

    private var _binding: FragmentFilmDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FilmDatabase
    private lateinit var adapter: ExposureDetailAdapter
    private var filmId: Long = 0
    private var film: Film? = null

    companion object {
        private const val ARG_FILM_ID = "film_id"

        fun newInstance(filmId: Long): FilmDetailFragment {
            val fragment = FilmDetailFragment()
            val args = Bundle()
            args.putLong(ARG_FILM_ID, filmId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filmId = arguments?.getLong(ARG_FILM_ID) ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFilmDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FilmDatabase(requireContext())

        // Load film info
        film = db.getFilmById(filmId)
        film?.let {
            binding.tvFilmName.text = it.name
            binding.tvIso.text = "ISO ${it.iso}"
        }

        // Setup back button
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Setup adapter
        adapter = ExposureDetailAdapter()
        binding.recyclerExposures.layoutManager = LinearLayoutManager(context)
        binding.recyclerExposures.adapter = adapter

        // Setup drag-to-reorder
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                adapter.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not supported
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                adapter.saveOrder()
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerExposures)

        // Setup FAB
        binding.fabStartLightmeter.setOnClickListener {
            startLightmeter()
        }

        loadExposures()
    }

    private fun loadExposures() {
        val exposures = db.getAllExposuresByFilmId(filmId)
        adapter.submitList(exposures)

        val count = exposures.size
        binding.tvExposureCount.text = "曝光: ${count}张"

        binding.tvEmpty.visibility = if (exposures.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerExposures.visibility = if (exposures.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun startLightmeter() {
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
        val dialog = ExifPreviewDialog.newInstance(
            filmId = filmId,
            iso = film?.iso ?: 400,
            aperture = exifData.aperture,
            shutterSpeed = exifData.shutterSpeed,
            hasFlash = exifData.hasFlash,
            flashGN = exifData.flashGN
        )
        dialog.setOnSaveListener {
            loadExposures()
        }
        dialog.show(parentFragmentManager, "exif_preview")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class ExposureDetailAdapter : RecyclerView.Adapter<ExposureDetailAdapter.ViewHolder>() {

        private var exposures = listOf<Exposure>()
        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun submitList(newExposures: List<Exposure>) {
            exposures = newExposures
            notifyDataSetChanged()
        }

        fun moveItem(fromPos: Int, toPos: Int) {
            val mutableList = exposures.toMutableList()
            val item = mutableList.removeAt(fromPos)
            mutableList.add(toPos, item)
            exposures = mutableList
            notifyItemMoved(fromPos, toPos)
        }

        fun saveOrder() {
            exposures.forEachIndexed { index, exposure ->
                db.updateExposureOrder(exposure.id, index)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemExposureDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(exposures[position], position + 1)
        }

        override fun getItemCount() = exposures.size

        inner class ViewHolder(private val binding: ItemExposureDetailBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(exposure: Exposure, index: Int) {
                binding.tvIndex.text = "$index."
                binding.tvAperture.text = "f/${exposure.aperture}"
                binding.tvShutter.text = exposure.shutterSpeed

                if (exposure.hasFlash && exposure.flashGN != null) {
                    binding.tvFlash.text = "⚡${exposure.flashGN}"
                } else {
                    binding.tvFlash.text = "⚡️⃠"
                }

                binding.tvTime.text = dateFormat.format(Date(exposure.createdAt))

                // Long press to delete
                itemView.setOnLongClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle("删除曝光")
                        .setMessage("确定要删除此曝光记录吗？")
                        .setPositiveButton("确定") { _, _ ->
                            db.deleteExposure(exposure.id)
                            loadExposures()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                }
            }
        }
    }
}
