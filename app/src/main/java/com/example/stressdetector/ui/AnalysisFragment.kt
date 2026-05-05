package com.example.stressdetector.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.stressdetector.R
import com.example.stressdetector.api.ApiConfig
import com.example.stressdetector.api.ApiService
import com.example.stressdetector.auth.SessionManager
import com.example.stressdetector.databinding.FragmentAnalysisBinding
import com.example.stressdetector.models.parseError
import com.example.stressdetector.models.toMeasurementRequest
import com.example.stressdetector.preprocessing.StressAnalyzer
import com.example.stressdetector.preprocessing.StressRecommendations
import com.example.stressdetector.preprocessing.StressResult
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!

    private lateinit var stressAnalyzer: StressAnalyzer
    private lateinit var apiService: ApiService

    private var selectedUri: Uri? = null
    private var currentFileName: String = "archivo.csv"
    private var lastStressResult: StressResult? = null

    private val colorNormal = Color.parseColor("#10B981")
    private val colorStress = Color.parseColor("#EF4444")
    private val colorWarning = Color.parseColor("#F59E0B")
    private val colorText = Color.parseColor("#D1D5DB")

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            currentFileName = getFileName(uri)
            binding.txtFileName.text = currentFileName
            analyzeFile(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiService = ApiConfig.getApiService(requireContext())

        hideResults()
        initAnalyzer()
        setupListeners()
    }

    private fun hideResults() {
        binding.scrollView.visibility = View.GONE
        binding.cardStatus.visibility = View.VISIBLE
        binding.btnRecommendations.visibility = View.GONE
        lastStressResult = null
    }

    private fun showResultCards(result: StressResult) {
        binding.cardStatus.visibility = View.GONE
        binding.scrollView.visibility = View.VISIBLE

        // Result header
        binding.resultHeader.visibility = View.VISIBLE
        binding.txtResultLabel.text = result.label
        binding.txtResultLabel.setTextColor(if (result.isStressed) colorStress else colorNormal)
        binding.txtProbability.text = "${(result.probability * 100).toInt()}%"
        binding.txtStressLevel.text = "Nivel: ${result.stressLevel}"
        binding.txtPattern.text = result.stressPattern()

        // Clear previous data from containers
        binding.containerMultiWindow.removeAllViews()
        binding.containerDistribution.removeAllViews()
        binding.containerHeartRate.removeAllViews()
        binding.containerHrv.removeAllViews()
        binding.containerCoverage.removeAllViews()
        binding.containerWindows.removeAllViews()

        // Multi-window card
        val multiRows = mutableListOf<Pair<String, String>>()
        multiRows.add("Promedio" to formatPct(result.probability))
        multiRows.add("Mediana" to formatPct(result.probabilityMedian))
        multiRows.add("Rango" to "${formatPct(result.probabilityMin)} - ${formatPct(result.probabilityMax)}")
        multiRows.add("Variabilidad" to formatPct(result.probabilityStd))
        populateCard(binding.cardMultiWindow, binding.containerMultiWindow, multiRows)

        // Distribution card
        val distRows = mutableListOf<Pair<String, String>>()
        distRows.add("Ventanas analizadas" to "${result.totalWindows}")
        distRows.add("Con estrés" to "${result.stressedWindows} (${formatFloat(result.stressPercentage)}%)")
        distRows.add("Patrón" to result.stressPattern())
        if (result.windowsRejected > 0) {
            distRows.add("Rechazadas" to "${result.windowsRejected}")
        }
        populateCard(binding.cardDistribution, binding.containerDistribution, distRows)

        // Heart rate card
        val hrv = result.hrvMetrics
        val hrRows = mutableListOf<Pair<String, String>>()
        if (!hrv.hrBpm.isNaN()) hrRows.add("FC combinada" to "${formatFloat(hrv.hrBpm)} BPM")
        if (!hrv.hrEcgBpm.isNaN()) hrRows.add("FC (ECG)" to "${formatFloat(hrv.hrEcgBpm)} BPM")
        if (!hrv.hrBvpBpm.isNaN()) hrRows.add("FC (BVP)" to "${formatFloat(hrv.hrBvpBpm)} BPM")
        populateCard(binding.cardHeartRate, binding.containerHeartRate, hrRows)

        // HRV card
        val hrvRows = mutableListOf<Pair<String, String>>()
        if (!hrv.rmssd.isNaN()) hrvRows.add("RMSSD" to "${formatFloat(hrv.rmssd)} ms")
        if (!hrv.sdnn.isNaN()) hrvRows.add("SDNN" to "${formatFloat(hrv.sdnn)} ms")
        if (!hrv.pnn50.isNaN()) hrvRows.add("pNN50" to formatPct(hrv.pnn50))
        if (!hrv.rrMeanS.isNaN()) hrvRows.add("RR medio" to "${formatFloat(hrv.rrMeanS * 1000)} ms")
        populateCard(binding.cardHrv, binding.containerHrv, hrvRows)

        // Coverage card
        val covRows = mutableListOf<Pair<String, String>>()
        covRows.add("Segmentos" to "${result.segmentsUsed}")
        covRows.add("Duración" to "${formatFloat(result.totalDurationS)} s")
        covRows.add("Calidad" to formatQuality(result.quality.score))
        covRows.add("Procesamiento" to "${result.processingTimeMs}ms")
        covRows.add("Archivo" to currentFileName)
        populateCard(binding.cardCoverage, binding.containerCoverage, covRows)

        // Individual windows card
        if (result.windowResults.isNotEmpty()) {
            binding.cardWindows.visibility = View.VISIBLE
            for (w in result.windowResults) {
                val wColor = if (w.isStressed) "#EF4444" else "#10B981"
                val label = "Seg${w.segmentId}-V${w.windowIndex}"
                val value = "${formatPct(w.probability)} @${formatFloat(w.startS)}s"
                addWindowRow(binding.containerWindows, label, value, wColor)
            }
        }

        binding.scrollView.smoothScrollTo(0, 0)
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
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val labelTv = TextView(ctx).apply {
            text = label
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueTv = TextView(ctx).apply {
            text = value
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        row.addView(labelTv)
        row.addView(valueTv)
        container.addView(row)

        if (!isLast) {
            val divider = View(ctx).apply {
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
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }

        val dot = TextView(ctx).apply {
            text = "\u25CF"
            setTextColor(Color.parseColor(dotColor))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 10 }
        }

        val labelTv = TextView(ctx).apply {
            text = label
            setTextColor(Color.parseColor("#D1D5DB"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueTv = TextView(ctx).apply {
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

    private fun formatPct(value: Float): String {
        if (value.isNaN()) return "N/A"
        return "${(value * 100).toInt()}%"
    }

    private fun formatFloat(value: Float): String {
        if (value.isNaN()) return "N/A"
        return "%.1f".format(value)
    }

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

    private fun initAnalyzer() {
        stressAnalyzer = StressAnalyzer(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    stressAnalyzer.initialize()
                }
                binding.txtResult.setTextColor(colorText)
                binding.txtResult.text = "Modelo cargado correctamente.\n\nSelecciona un archivo CSV del sensor MAX86150 para analizar."
            } catch (e: Exception) {
                binding.txtResult.setTextColor(colorWarning)
                binding.txtResult.text = "Error cargando modelo: ${e.message}"
            }
        }
    }

    private fun setupListeners() {
        binding.btnSelectFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf(
                "text/csv",
                "text/comma-separated-values",
                "application/csv",
                "*/*"
            ))
        }

        binding.btnRecommendations.setOnClickListener {
            openRecommendationsSheet()
        }
    }

    private fun analyzeFile(uri: Uri) {
        showLoading(true)
        hideResults()
        binding.txtResult.setTextColor(colorText)
        binding.txtResult.text = "Analizando señales...\n\nEsto puede tomar unos segundos."

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                stressAnalyzer.analyzeFromUri(uri)
            }

            showLoading(false)

            result.onSuccess { stressResult ->
                showResultCards(stressResult)
                lastStressResult = stressResult
                binding.btnRecommendations.visibility = View.VISIBLE

                // Only save to server if not in offline mode
                if (!SessionManager.isOfflineMode()) {
                    saveMeasurementToServer(stressResult)
                }
            }

            result.onFailure { error ->
                hideResults()
                binding.txtResult.setTextColor(colorWarning)
                binding.txtResult.text = buildString {
                    appendLine("Error en el análisis")
                    appendLine()
                    appendLine(error.message)
                    appendLine()
                    appendLine("Posibles causas:")
                    appendLine("  - Formato de archivo incorrecto")
                    appendLine("  - Datos muy cortos (< 10 segundos)")
                    appendLine("  - Señal con demasiado ruido")
                }
            }
        }
    }

    private fun openRecommendationsSheet() {
        val result = lastStressResult ?: return
        val ctx = requireContext()
        val recommendation = StressRecommendations.getRecommendation(
            result.stressLevel,
            result.stressPattern()
        )

        val dialog = BottomSheetDialog(ctx)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_recommendations, null)
        dialog.setContentView(sheetView)

        (sheetView.parent as View).setBackgroundColor(Color.TRANSPARENT)

        val txtLevelBadge = sheetView.findViewById<TextView>(R.id.txtLevelBadge)
        val badgeColor = when (result.stressLevel) {
            "Alto" -> Color.parseColor("#EF4444")
            "Moderado-Alto" -> Color.parseColor("#F59E0B")
            "Moderado-Bajo" -> Color.parseColor("#3B82F6")
            else -> Color.parseColor("#10B981")
        }
        val badgeBg = GradientDrawable().apply {
            setColor(badgeColor)
            cornerRadius = 16f
        }
        txtLevelBadge.background = badgeBg
        txtLevelBadge.text = result.stressLevel

        val txtSubtitle = sheetView.findViewById<TextView>(R.id.txtSubtitle)
        txtSubtitle.text = StressRecommendations.getSubtitle(result.stressLevel)

        val containerTechniques = sheetView.findViewById<LinearLayout>(R.id.containerTechniques)
        for (technique in recommendation.techniques) {
            containerTechniques.addView(createRecommendationItem(technique, "#818CF8"))
        }

        val containerTips = sheetView.findViewById<LinearLayout>(R.id.containerTips)
        for (tip in recommendation.tips) {
            containerTips.addView(createRecommendationItem(tip, "#34D399"))
        }

        if (recommendation.patternName != null && recommendation.patternTip != null) {
            val cardPattern = sheetView.findViewById<LinearLayout>(R.id.cardPattern)
            cardPattern.visibility = View.VISIBLE
            sheetView.findViewById<TextView>(R.id.txtPatternTitle).text = recommendation.patternName
            sheetView.findViewById<TextView>(R.id.txtPatternTip).text = recommendation.patternTip
        }

        dialog.show()
    }

    private fun createRecommendationItem(itemText: String, dotColor: String): LinearLayout {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
            gravity = android.view.Gravity.TOP
        }

        val dot = TextView(ctx).apply {
            text = "\u25CF"
            setTextColor(Color.parseColor(dotColor))
            textSize = 8f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6
                marginEnd = 12
            }
        }

        val label = TextView(ctx).apply {
            text = itemText
            setTextColor(Color.parseColor("#D1D5DB"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setLineSpacing(4f, 1f)
        }

        row.addView(dot)
        row.addView(label)
        return row
    }

    private fun saveMeasurementToServer(stressResult: StressResult) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = stressResult.toMeasurementRequest(currentFileName)
                val response = withContext(Dispatchers.IO) {
                    apiService.createMeasurement(request)
                }
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Medición guardada", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMsg = response.parseError()
                    Log.w("AnalysisFragment", "Error guardando medición: $errorMsg")
                    Toast.makeText(requireContext(), "Error al guardar: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("AnalysisFragment", "Error de red al guardar medición", e)
                Toast.makeText(requireContext(), "Sin conexión al servidor", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.isVisible = loading
        binding.btnSelectFile.isEnabled = !loading
    }

    private fun getFileName(uri: Uri): String {
        var name = "archivo.csv"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
        return name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stressAnalyzer.close()
        _binding = null
    }
}
