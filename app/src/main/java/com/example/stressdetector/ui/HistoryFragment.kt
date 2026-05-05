package com.example.stressdetector.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stressdetector.api.ApiConfig
import com.example.stressdetector.api.ApiService
import com.example.stressdetector.databinding.FragmentHistoryBinding
import com.example.stressdetector.models.MeasurementSummary
import com.example.stressdetector.models.parseError
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var apiService: ApiService
    private lateinit var adapter: MeasurementAdapter

    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiService = ApiConfig.getApiService(requireContext())
        setupRecyclerView()
        loadMeasurements()
    }

    private fun setupRecyclerView() {
        adapter = MeasurementAdapter { measurement ->
            val intent = Intent(requireContext(), MeasurementDetailActivity::class.java)
            intent.putExtra("measurement_id", measurement.id)
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.currentList[position]
                confirmDelete(item, position)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerView)

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = rv.layoutManager as LinearLayoutManager
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val total = layoutManager.itemCount
                if (!isLoading && currentPage < totalPages && lastVisible >= total - 3) {
                    currentPage++
                    loadMeasurements()
                }
            }
        })
    }

    private fun loadMeasurements() {
        isLoading = true
        binding.progressBar.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = apiService.getMeasurements(page = currentPage)
                if (response.isSuccessful) {
                    val body = response.body()!!
                    totalPages = body.pages

                    val currentList = if (currentPage == 1) {
                        body.measurements
                    } else {
                        adapter.currentList + body.measurements
                    }

                    adapter.submitList(currentList)

                    if (currentList.isEmpty()) {
                        binding.emptyContainer.visibility = View.VISIBLE
                        binding.cardStats.visibility = View.GONE
                    } else {
                        binding.emptyContainer.visibility = View.GONE
                        updateStats(currentList)
                    }
                } else {
                    showError(response.parseError())
                }
            } catch (e: Exception) {
                showError("Error de conexión: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
                isLoading = false
            }
        }
    }

    private fun updateStats(list: List<MeasurementSummary>) {
        binding.cardStats.visibility = View.VISIBLE

        val total = list.size
        val stressed = list.count { it.isStressed }
        val avgProbability = if (total > 0) list.map { it.probability }.average().toFloat() else 0f
        val avgPct = (avgProbability * 100).toInt()

        binding.txtStatTotal.text = "$total"
        binding.txtStatAvg.text = "$avgPct%"
        binding.txtStatStressed.text = "$stressed"

        // Color the average based on value
        val avgColor = when {
            avgPct >= 70 -> Color.parseColor("#EF4444")
            avgPct >= 50 -> Color.parseColor("#F59E0B")
            avgPct >= 30 -> Color.parseColor("#3B82F6")
            else -> Color.parseColor("#10B981")
        }
        binding.txtStatAvg.setTextColor(avgColor)

        // Update subtitle with count
        binding.txtSubtitle.text = "$total mediciones registradas"
    }

    private fun confirmDelete(item: MeasurementSummary, position: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar medición")
            .setMessage("¿Estás seguro de que deseas eliminar esta medición?")
            .setPositiveButton("Eliminar") { _, _ -> deleteMeasurement(item) }
            .setNegativeButton("Cancelar") { _, _ ->
                adapter.notifyItemChanged(position)
            }
            .setOnCancelListener {
                adapter.notifyItemChanged(position)
            }
            .show()
    }

    private fun deleteMeasurement(item: MeasurementSummary) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = apiService.deleteMeasurement(item.id)
                if (response.isSuccessful) {
                    val updated = adapter.currentList.toMutableList()
                    updated.remove(item)
                    adapter.submitList(updated)

                    if (updated.isEmpty()) {
                        binding.emptyContainer.visibility = View.VISIBLE
                        binding.cardStats.visibility = View.GONE
                    } else {
                        updateStats(updated)
                    }
                } else {
                    showError(response.parseError())
                    currentPage = 1
                    loadMeasurements()
                }
            } catch (e: Exception) {
                showError("Error de conexión: ${e.message}")
                currentPage = 1
                loadMeasurements()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentPage = 1
        loadMeasurements()
    }

    private fun showError(message: String) {
        binding.txtError.text = message
        binding.errorContainer.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
