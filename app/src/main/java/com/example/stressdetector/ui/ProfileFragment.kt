package com.example.stressdetector.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.stressdetector.api.ApiConfig
import com.example.stressdetector.api.ApiService
import com.example.stressdetector.auth.SessionManager
import com.example.stressdetector.databinding.FragmentProfileBinding
import com.example.stressdetector.models.MeasurementSummary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pantalla de perfil con datos basicos y estadisticas.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var apiService: ApiService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiService = ApiConfig.getApiService(requireContext())

        val userName = SessionManager.getUserName()
        val cedula = SessionManager.getCedula()
        val fechaNacimiento = SessionManager.getFechaNacimiento()

        // Header
        binding.txtAvatarInitial.text = userName.firstOrNull()?.uppercase() ?: "U"
        binding.txtName.text = userName
        binding.txtCedulaHeader.text = "C.I. $cedula"

        // Card values
        binding.txtNameValue.text = userName
        binding.txtCedula.text = cedula
        binding.txtFechaNacimiento.text = formatBirthDate(fechaNacimiento)

        // Load statistics
        loadStatistics()

        binding.btnLogout.setOnClickListener {
            SessionManager.clearSession()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    /**
     * Pide al servidor las mediciones para calcular resumenes.
     */
    private fun loadStatistics() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = apiService.getMeasurements(page = 1)
                if (response.isSuccessful) {
                    val measurements = response.body()?.measurements ?: emptyList()
                    updateStatistics(measurements)
                }
            } catch (e: Exception) {
                setDefaultStatistics()
            }
        }
    }

    /**
     * Actualiza la UI con los numeros del historial.
     */
    private fun updateStatistics(measurements: List<MeasurementSummary>) {
        if (measurements.isEmpty()) {
            setDefaultStatistics()
            return
        }

        val total = measurements.size
        val stressed = measurements.count { it.isStressed }
        val avgProbability = measurements.map { it.probability }.average().toFloat()
        val avgPct = (avgProbability * 100).toInt()

        binding.txtTotalAnalysis.text = "$total"
        binding.txtStressedCount.text = "$stressed"
        binding.txtAvgStress.text = "$avgPct%"

        val avgColor = when {
            avgPct >= 70 -> Color.parseColor("#EF4444")
            avgPct >= 50 -> Color.parseColor("#F59E0B")
            avgPct >= 30 -> Color.parseColor("#3B82F6")
            else -> Color.parseColor("#10B981")
        }
        binding.txtAvgStress.setTextColor(avgColor)

        val lastMeasurement = measurements.firstOrNull()
        if (lastMeasurement != null) {
            try {
                // Parse timestamp from ISO format (e.g., "2025-03-29T15:30:00")
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = inputFormat.parse(lastMeasurement.timestamp)

                val outputFormat = SimpleDateFormat("dd/MM/yy · HH:mm", Locale.getDefault())
                val formattedDate = if (date != null) outputFormat.format(date) else lastMeasurement.timestamp

                val status = if (lastMeasurement.isStressed) "Estrés" else "Normal"
                binding.txtLastAnalysis.text = "$formattedDate · $status"
            } catch (e: Exception) {
                binding.txtLastAnalysis.text = lastMeasurement.timestamp
            }
        } else {
            binding.txtLastAnalysis.text = "Sin análisis"
        }
    }

    /**
     * Muestra ceros cuando no hay datos.
     */
    private fun setDefaultStatistics() {
        binding.txtTotalAnalysis.text = "0"
        binding.txtStressedCount.text = "0"
        binding.txtAvgStress.text = "0%"
        binding.txtAvgStress.setTextColor(Color.parseColor("#10B981"))
        binding.txtLastAnalysis.text = "Sin análisis"
    }

    /**
     * Convierte la fecha a un formato facil de leer.
     */
    private fun formatBirthDate(rawDate: String): String {
        if (rawDate.isBlank()) return "No disponible"

        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = inputFormat.parse(rawDate)
            if (date != null) outputFormat.format(date) else rawDate
        } catch (e: Exception) {
            rawDate
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


