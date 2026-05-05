package com.example.stressdetector.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stressdetector.api.ApiConfig
import com.example.stressdetector.api.ApiService
import com.example.stressdetector.auth.SessionManager
import com.example.stressdetector.databinding.ActivityMeasurementDetailBinding
import com.example.stressdetector.models.MeasurementDetail
import com.example.stressdetector.models.parseError
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MeasurementDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMeasurementDetailBinding
    private lateinit var apiService: ApiService
    private var measurementId = -1

    private val colorNormal = Color.parseColor("#10B981")
    private val colorStress = Color.parseColor("#EF4444")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeasurementDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(this)
        apiService = ApiConfig.getApiService(this)

        measurementId = intent.getIntExtra("measurement_id", -1)
        if (measurementId == -1) {
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        loadDetail()
    }

    private fun loadDetail() {
        binding.progressBar.visibility = View.VISIBLE
        binding.txtError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = apiService.getMeasurement(measurementId)
                if (response.isSuccessful) {
                    displayDetail(response.body()!!)
                } else {
                    showError(response.parseError())
                }
            } catch (e: Exception) {
                showError("Error de conexión: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun displayDetail(detail: MeasurementDetail) {
        val isStressed = detail.isStressed
        val color = if (isStressed) colorStress else colorNormal

        // Result header
        binding.resultHeader.visibility = View.VISIBLE
        binding.txtResultLabel.text = detail.label
        binding.txtResultLabel.setTextColor(color)
        binding.txtProbability.text = "${(detail.probability * 100).toInt()}%"
        binding.txtStressLevel.text = "Nivel: ${detail.stressLevel ?: "-"}"

        try {
            val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFmt = SimpleDateFormat("dd MMM yyyy - HH:mm", Locale("es"))
            val date = inputFmt.parse(detail.timestamp)
            binding.txtDate.text = if (date != null) outputFmt.format(date) else detail.timestamp
        } catch (e: Exception) {
            binding.txtDate.text = detail.timestamp
        }

        // Multi-window card
        val multiWindowRows = mutableListOf<Pair<String, String>>()
        multiWindowRows.add("Promedio" to formatPct(detail.probability))
        detail.probabilityMedian?.let { multiWindowRows.add("Mediana" to formatPct(it)) }
        if (detail.probabilityMin != null && detail.probabilityMax != null) {
            multiWindowRows.add("Rango" to "${formatPct(detail.probabilityMin)} - ${formatPct(detail.probabilityMax)}")
        }
        detail.probabilityStd?.let { multiWindowRows.add("Variabilidad" to formatPct(it)) }
        populateCard(binding.cardMultiWindow, binding.containerMultiWindow, multiWindowRows)

        // Distribution card
        val distRows = mutableListOf<Pair<String, String>>()
        detail.totalWindows?.let { distRows.add("Ventanas analizadas" to "$it") }
        if (detail.stressedWindows != null && detail.stressPercentage != null) {
            distRows.add("Con estrés" to "${detail.stressedWindows} (${formatFloat(detail.stressPercentage)}%)")
        }
        detail.stressPattern?.let { distRows.add("Patrón" to it) }
        populateCard(binding.cardDistribution, binding.containerDistribution, distRows)

        // Heart rate card
        val hrRows = mutableListOf<Pair<String, String>>()
        detail.hrBpm?.let { hrRows.add("Frecuencia cardíaca" to "${formatFloat(it)} BPM") }
        populateCard(binding.cardHeartRate, binding.containerHeartRate, hrRows)

        // HRV card
        val hrvRows = mutableListOf<Pair<String, String>>()
        detail.rmssd?.let { hrvRows.add("RMSSD" to "${formatFloat(it)} ms") }
        detail.sdnn?.let { hrvRows.add("SDNN" to "${formatFloat(it)} ms") }
        detail.pnn50?.let { hrvRows.add("pNN50" to formatPct(it)) }
        populateCard(binding.cardHrv, binding.containerHrv, hrvRows)

        // Coverage card
        val covRows = mutableListOf<Pair<String, String>>()
        detail.segmentsUsed?.let { covRows.add("Segmentos" to "$it") }
        detail.totalDurationS?.let { covRows.add("Duración" to "${formatFloat(it)} s") }
        detail.qualityScore?.let { covRows.add("Calidad" to formatQuality(it)) }
        detail.processingTimeMs?.let { covRows.add("Procesamiento" to "${it}ms") }
        detail.fileName?.let { covRows.add("Archivo" to it) }
        populateCard(binding.cardCoverage, binding.containerCoverage, covRows)

        // Individual windows card
        if (detail.windowResults != null && detail.windowResults.isNotEmpty()) {
            binding.cardWindows.visibility = View.VISIBLE
            for (w in detail.windowResults) {
                val wColor = if (w.isStressed) "#EF4444" else "#10B981"
                val label = "Seg${w.segmentId}-V${w.windowIndex}"
                val value = "${formatPct(w.probability)} @${formatFloat(w.startS)}s"
                addWindowRow(binding.containerWindows, label, value, wColor)
            }
        }
    }

    private fun populateCard(
        card: LinearLayout,
        container: LinearLayout,
        rows: List<Pair<String, String>>
    ) {
        if (rows.isEmpty()) return
        card.visibility = View.VISIBLE
        for ((i, pair) in rows.withIndex()) {
            addDetailRow(container, pair.first, pair.second, isLast = i == rows.lastIndex)
        }
    }

    private fun addDetailRow(container: LinearLayout, label: String, value: String, isLast: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (!isLast) setPadding(0, 0, 0, 0)
        }

        val labelTv = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueTv = TextView(this).apply {
            text = value
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        row.addView(labelTv)
        row.addView(valueTv)
        container.addView(row)

        // Add divider if not last
        if (!isLast) {
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply {
                    topMargin = 12
                    bottomMargin = 12
                }
                setBackgroundColor(Color.parseColor("#2D2D3A"))
            }
            container.addView(divider)
        }
    }

    private fun addWindowRow(container: LinearLayout, label: String, value: String, dotColor: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }

        val dot = TextView(this).apply {
            text = "\u25CF"
            setTextColor(Color.parseColor(dotColor))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 10 }
        }

        val labelTv = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#D1D5DB"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueTv = TextView(this).apply {
            text = value
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        row.addView(dot)
        row.addView(labelTv)
        row.addView(valueTv)
        container.addView(row)
    }

    private fun formatPct(value: Float): String = "${(value * 100).toInt()}%"
    private fun formatFloat(value: Float): String = "%.1f".format(value)

    private fun formatQuality(score: Float): String {
        val label = when {
            score >= 3.5f -> "Excelente"
            score >= 2.5f -> "Buena"
            score >= 1.5f -> "Aceptable"
            score >= 0.5f -> "Baja"
            else -> "Muy baja"
        }
        return "${"%.1f".format(score)}/4.0 · $label"
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar medición")
            .setMessage("¿Estás seguro de que deseas eliminar esta medición?")
            .setPositiveButton("Eliminar") { _, _ -> deleteMeasurement() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteMeasurement() {
        lifecycleScope.launch {
            try {
                val response = apiService.deleteMeasurement(measurementId)
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@MeasurementDetailActivity,
                        "Medición eliminada",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    showError(response.parseError())
                }
            } catch (e: Exception) {
                showError("Error de conexión: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        binding.txtError.text = message
        binding.txtError.visibility = View.VISIBLE
    }
}
