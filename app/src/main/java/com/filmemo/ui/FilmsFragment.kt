package com.filmemo.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.filmemo.R
import com.filmemo.data.Film
import com.filmemo.data.FilmDatabase
import com.filmemo.databinding.FragmentFilmsBinding
import com.filmemo.util.LightMeter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilmsFragment : Fragment() {

    private var _binding: FragmentFilmsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FilmDatabase
    private lateinit var adapter: FilmsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFilmsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FilmDatabase(requireContext())

        adapter = FilmsAdapter(
            onSetCurrent = { film ->
                db.setCurrentFilm(film.id)
                Toast.makeText(context, "已设为当前胶卷: ${film.name}", Toast.LENGTH_SHORT).show()
                loadFilms()
            },
            onFinish = { film ->
                AlertDialog.Builder(requireContext())
                    .setTitle("结束胶卷")
                    .setMessage("确定要结束 \"${film.name}\" 吗？结束后不可再添加曝光。")
                    .setPositiveButton("确定") { _, _ ->
                        db.finishFilm(film.id)
                        Toast.makeText(context, "胶卷已结束", Toast.LENGTH_SHORT).show()
                        loadFilms()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            },
            onFilmClick = { film ->
                val fragment = FilmDetailFragment.newInstance(film.id)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        )

        binding.recyclerFilms.layoutManager = LinearLayoutManager(context)
        binding.recyclerFilms.adapter = adapter

        binding.fabAdd.setOnClickListener {
            showCreateFilmDialog()
        }

        loadFilms()
    }

    private fun loadFilms() {
        val films = db.getAllFilms()
        val currentFilmId = db.getCurrentFilmId()
        adapter.submitList(films, currentFilmId)

        binding.tvEmpty.visibility = if (films.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerFilms.visibility = if (films.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCreateFilmDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_film, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val spinnerIso = dialogView.findViewById<Spinner>(R.id.spinner_iso)

        val isoAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, LightMeter.VALID_ISOS)
        spinnerIso.adapter = isoAdapter
        // Default to ISO 400
        spinnerIso.setSelection(LightMeter.VALID_ISOS.indexOf(400))

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
            .apply {
                dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dismiss() }
                dialogView.findViewById<View>(R.id.btn_create).setOnClickListener {
                    val name = etName.text.toString().trim()
                    if (name.isEmpty()) {
                        etName.error = "请输入胶卷名称"
                        return@setOnClickListener
                    }
                    val iso = spinnerIso.selectedItem as Int
                    val film = Film(name = name, iso = iso)
                    db.insertFilm(film)
                    Toast.makeText(context, "胶卷已创建: $name", Toast.LENGTH_SHORT).show()
                    dismiss()
                    loadFilms()
                }
                show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class FilmsAdapter(
    private val onSetCurrent: (Film) -> Unit,
    private val onFinish: (Film) -> Unit,
    private val onFilmClick: (Film) -> Unit
) : RecyclerView.Adapter<FilmsAdapter.ViewHolder>() {

    private var films = listOf<Film>()
    private var currentFilmId: Long? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun submitList(newFilms: List<Film>, currentId: Long?) {
        films = newFilms
        currentFilmId = currentId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_film, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(films[position])
    }

    override fun getItemCount() = films.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName = itemView.findViewById<TextView>(R.id.tv_film_name)
        private val tvStatus = itemView.findViewById<TextView>(R.id.tv_status)
        private val tvIso = itemView.findViewById<TextView>(R.id.tv_iso)
        private val tvDate = itemView.findViewById<TextView>(R.id.tv_date)
        private val layoutActions = itemView.findViewById<View>(R.id.layout_actions)
        private val btnSetCurrent = itemView.findViewById<View>(R.id.btn_set_current)
        private val btnFinish = itemView.findViewById<View>(R.id.btn_finish)

        fun bind(film: Film) {
            tvName.text = film.name
            tvIso.text = "ISO ${film.iso}"
            tvDate.text = dateFormat.format(Date(film.createdAt))

            val isCurrent = film.id == currentFilmId
            tvName.text = if (isCurrent) "${film.name} ★" else film.name

            if (film.isActive) {
                tvStatus.text = "进行中"
                tvStatus.setBackgroundColor(itemView.context.getColor(R.color.success))
                layoutActions.visibility = View.VISIBLE
                btnSetCurrent.setOnClickListener { onSetCurrent(film) }
                btnFinish.setOnClickListener { onFinish(film) }
            } else {
                tvStatus.text = "已完成"
                tvStatus.setBackgroundColor(itemView.context.getColor(R.color.text_secondary))
                layoutActions.visibility = View.GONE
                film.finishedAt?.let {
                    tvDate.text = "${dateFormat.format(Date(film.createdAt))} → ${dateFormat.format(Date(it))}"
                }
            }

            // Add click listener for the entire card
            itemView.setOnClickListener { onFilmClick(film) }
        }
    }
}
