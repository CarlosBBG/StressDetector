package com.example.stressdetector.analysis

import android.content.Context
import android.net.Uri
import com.example.stressdetector.TFLiteRunner
import com.example.stressdetector.preprocessing.CSVLoader
import com.example.stressdetector.preprocessing.HRVMetrics
import com.example.stressdetector.preprocessing.ProcessedSegment
import com.example.stressdetector.preprocessing.QualityChecker
import com.example.stressdetector.preprocessing.QualityResult
import com.example.stressdetector.preprocessing.RPeakDetector
import com.example.stressdetector.preprocessing.SignalPreprocessor

/**
 * Resultado de una ventana individual.
 */
data class WindowResult(
    val segmentId: Int,
    val windowIndex: Int,
    val startS: Float,                    // Inicio de la ventana en segundos
    val probability: Float,               // Probabilidad de estrés [0, 1]
    val isStressed: Boolean               // probability >= 0.5
)

/**
 * Resultado del análisis de estrés con múltiples ventanas.
 */
data class StressResult(
    // Resultados agregados
    val probability: Float,               // Probabilidad promedio de estrés [0, 1]
    val probabilityMedian: Float,         // Mediana de probabilidades
    val label: String,                    // "ESTRÉS" o "NORMAL" (basado en promedio)
    val isStressed: Boolean,              // probability >= 0.5
    val stressLevel: String,              // "Bajo", "Moderado", "Alto", "Muy Alto"

    // Estadísticas de ventanas
    val totalWindows: Int,                // Total de ventanas analizadas
    val stressedWindows: Int,             // Ventanas con estrés (prob >= 0.5)
    val stressPercentage: Float,          // % de ventanas con estrés
    val probabilityMin: Float,            // Probabilidad mínima
    val probabilityMax: Float,            // Probabilidad máxima
    val probabilityStd: Float,            // Desviación estándar
    val windowsRejected: Int = 0,         // Ventanas rechazadas por baja calidad
    val duplicatesDetected: Int = 0,      // Segmentos duplicados detectados

    // Detalles por ventana
    val windowResults: List<WindowResult>,

    // Métricas del mejor segmento (para HRV)
    val segmentsUsed: Int,                // Número de segmentos usados
    val totalDurationS: Float,            // Duración total analizada
    val quality: QualityResult,           // Métricas de calidad del mejor segmento
    val hrvMetrics: HRVMetrics,           // Métricas HRV del mejor segmento
    val processingTimeMs: Long            // Tiempo de procesamiento
) {
    /**
     * Detecta si hubo picos de estrés significativos.
     */
    fun hasStressPeaks(): Boolean = probabilityMax >= 0.7f

    /**
     * Cuenta ventanas con estrés severo (>75%).
     */
    fun severeStressWindows(): Int = windowResults.count { it.probability >= 0.75f }

    /**
     * Determina el patrón de estrés.
     */
    fun stressPattern(): String {
        val severeCount = severeStressWindows()
        val moderateCount = windowResults.count { it.probability in 0.5f..0.75f }

        return when {
            stressPercentage >= 70f -> "Estrés sostenido"
            severeCount > 0 && stressPercentage < 30f -> "Picos de estrés aislados"
            stressPercentage in 30f..70f -> "Estrés intermitente"
            probabilityStd > 0.25f -> "Alta variabilidad"
            else -> "Estado estable"
        }
    }

    /**
     * Resumen detallado sin emojis para la UI mejorada.
     * Los títulos de sección están marcados con [TITLE] para formateo especial.
     */
    fun toDetailedSummary(): String {
        val hasHighPeaks = hasStressPeaks()
        val severeCount = severeStressWindows()
        val pattern = stressPattern()

        return buildString {
            // Alerta de picos si aplica
            if (hasHighPeaks && !isStressed) {
                appendLine("[TITLE]ATENCIÓN")
                appendLine("Se detectaron picos de estrés")
                appendLine("Pico máximo: ${(probabilityMax * 100).format(0)}%")
                appendLine("Ventanas severas: $severeCount")
                appendLine()
            }

            appendLine("[TITLE]Análisis Multi-Ventana")
            appendLine("Promedio: ${(probability * 100).format(1)}%")
            appendLine("Mediana: ${(probabilityMedian * 100).format(1)}%")
            appendLine("Rango: ${(probabilityMin * 100).format(0)}% - ${(probabilityMax * 100).format(0)}%")
            appendLine("Variabilidad: ${(probabilityStd * 100).format(1)}%")
            appendLine()

            appendLine("[TITLE]Distribución de Ventanas")
            appendLine("Ventanas analizadas: $totalWindows")
            appendLine("Con estrés (≥50%): $stressedWindows (${stressPercentage.format(1)}%)")
            appendLine("Con estrés severo (>75%): $severeCount")
            if (windowsRejected > 0) {
                appendLine("Rechazadas por calidad: $windowsRejected")
            }
            appendLine("Patrón detectado: $pattern")
            appendLine()

            appendLine("[TITLE]Frecuencia Cardíaca")
            appendLine("FC usando ECG y BVP: ${hrvMetrics.hrBpm.format(2)} BPM")
            /*appendLine("FC por ECG: ${hrvMetrics.hrEcgBpm.format(1)} BPM")
            appendLine("FC por BVP: ${hrvMetrics.hrBvpBpm.format(1)} BPM")
            appendLine("Picos detectados: ${hrvMetrics.numPeaksEcg} ECG, ${hrvMetrics.numPeaksBvp} BVP")*/
            appendLine()

            appendLine("[TITLE]Variabilidad Cardíaca (HRV)")
            appendLine("RMSSD: ${hrvMetrics.rmssd.format(1)} ms")
            appendLine("SDNN: ${hrvMetrics.sdnn.format(1)} ms")
            appendLine("pNN50: ${(hrvMetrics.pnn50 * 100).format(1)}%")
            appendLine()

            appendLine("[TITLE]Cobertura del Análisis")
            appendLine("Segmentos utilizados: $segmentsUsed")
            appendLine("Duración total: ${totalDurationS.format(1)} segundos")
            appendLine("Calidad de señal: ${quality.score.format(2)}")
            appendLine()
            appendLine("Tiempo de procesamiento: ${processingTimeMs}ms")
        }
    }

    fun toSummary(): String {
        val hasHighPeaks = hasStressPeaks()
        val severeCount = severeStressWindows()
        val pattern = stressPattern()

        return buildString {
            appendLine("══════════════════════════════════")
            appendLine("   RESULTADO: $label")
            appendLine("   Nivel de estrés: $stressLevel")
            appendLine("══════════════════════════════════")
            appendLine()

            // Alerta de picos si aplica
            if (hasHighPeaks && !isStressed) {
                appendLine("ATENCIÓN: Se detectaron picos de estrés")
                appendLine("   • Pico máximo: ${(probabilityMax * 100).format(0)}%")
                appendLine("   • Ventanas severas (>75%): $severeCount")
                appendLine("   • Patrón: $pattern")
                appendLine()
            }

            appendLine("Análisis multi-ventana:")
            appendLine("   • Probabilidad promedio: ${(probability * 100).format(1)}%")
            appendLine("   • Probabilidad mediana: ${(probabilityMedian * 100).format(1)}%")
            appendLine("   • Rango: ${(probabilityMin * 100).format(0)}% - ${(probabilityMax * 100).format(0)}%")
            appendLine("   • Variabilidad: ${(probabilityStd * 100).format(1)}%")
            appendLine()
            appendLine("Distribución de ventanas:")
            appendLine("   • Total analizadas: $totalWindows")
            appendLine("   • Con estrés (≥50%): $stressedWindows (${stressPercentage.format(1)}%)")
            appendLine("   • Con estrés severo (≥75%): $severeCount")
            if (windowsRejected > 0) {
                appendLine("   • Rechazadas por calidad: $windowsRejected")
            }
            if (duplicatesDetected > 0) {
                appendLine("   • Segmentos duplicados: $duplicatesDetected")
            }
            appendLine("   • Patrón detectado: $pattern")
            appendLine()
            appendLine("Métricas cardíacas (promedio):")
            appendLine("   • FC combinada: ${hrvMetrics.hrBpm.format(1)} BPM")
            appendLine("   • FC por ECG: ${hrvMetrics.hrEcgBpm.format(1)} BPM")
            appendLine("   • FC por BVP: ${hrvMetrics.hrBvpBpm.format(1)} BPM")
            appendLine("   • Picos: ${hrvMetrics.numPeaksEcg} ECG, ${hrvMetrics.numPeaksBvp} BVP")
            appendLine()
            appendLine("Variabilidad cardíaca (HRV):")
            appendLine("   • RMSSD: ${hrvMetrics.rmssd.format(1)} ms")
            appendLine("   • SDNN: ${hrvMetrics.sdnn.format(1)} ms")
            appendLine("   • pNN50: ${(hrvMetrics.pnn50 * 100).format(1)}%")
            appendLine()
            appendLine("Cobertura:")
            appendLine("   • Segmentos usados: $segmentsUsed")
            appendLine("   • Duración total: ${totalDurationS.format(1)}s")
            appendLine("   • Calidad señal: ${quality.score.format(2)}")
            appendLine()
            appendLine("Procesado en ${processingTimeMs}ms")
        }
    }

    private fun Float.format(decimals: Int): String {
        return if (this.isNaN()) "N/A" else "%.${decimals}f".format(this)
    }
}

/**
 * Resultado de análisis fallido.
 */
data class AnalysisError(
    val message: String,
    val details: String? = null
)

/**
 * Analizador de estrés principal.
 * Orquesta todo el pipeline: carga CSV → preprocesamiento → inferencia TFLite.
 *
 * Usa análisis multi-ventana para resultados más robustos:
 * - Extrae múltiples ventanas de 10s de cada segmento válido
 * - Ejecuta inferencia en cada ventana
 * - Calcula estadísticas agregadas (promedio, mediana, distribución)
 */
class StressAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "StressAnalyzer"
        private const val MODEL_INPUT_SIZE = 4000  // 10 segundos @ 400Hz
        private const val NUM_CHANNELS = 2        // ECG + BVP
        private const val FS = 400f               // Frecuencia de muestreo

        // Configuración de ventanas (igual que en entrenamiento)
        private const val WINDOW_S = 10f          // Duración de ventana en segundos
        private const val OVERLAP = 0.5f          // 50% overlap entre ventanas

        // Umbrales de calidad de ventana
        private const val MIN_STD = 0.10f         // Mínima desviación estándar
        private const val MAX_STD = 8.0f          // Máxima std (señal muy ruidosa)
        private const val MIN_SNR = 2.0f          // Mínimo SNR estimado
        private const val MAX_ZERO_CROSSINGS_RATIO = 0.4f  // Máximo ratio de cruces por cero
        private const val MAX_ARTIFACT_RATIO = 0.05f       // Máximo % de artefactos permitidos
        private const val MIN_QUALITY_SCORE = 0.5f         // Score mínimo de calidad [0-1]
    }

    private val csvLoader = CSVLoader(context)
    private val preprocessor = SignalPreprocessor()
    private val rPeakDetector = RPeakDetector()
    private val qualityChecker = QualityChecker()
    private val tfliteRunner = TFLiteRunner(context)

    private var modelLoaded = false

    /**
     * Inicializa el modelo TFLite.
     */
    fun initialize(modelName: String = "stress_detector.tflite") {
        tfliteRunner.loadModelFromAssets(modelName)
        modelLoaded = true
        android.util.Log.d(TAG, "StressAnalyzer inicializado")
    }

    /**
     * Analiza un archivo CSV desde URI usando múltiples ventanas.
     * @return StressResult con estadísticas agregadas
     */
    fun analyzeFromUri(uri: Uri): Result<StressResult> {
        if (!modelLoaded) {
            return Result.failure(IllegalStateException("Modelo no cargado. Llama initialize() primero."))
        }

        val startTime = System.currentTimeMillis()

        return try {
            // 1. Cargar CSV
            android.util.Log.d(TAG, "Cargando CSV...")
            val sensorData = csvLoader.loadFromUri(uri)

            if (sensorData.isEmpty()) {
                return Result.failure(IllegalArgumentException("El archivo CSV está vacío o tiene formato inválido"))
            }
            android.util.Log.d(TAG, "CSV cargado: ${sensorData.size} muestras")

            // 2. Preprocesar
            android.util.Log.d(TAG, "Preprocesando señales...")
            val segments = preprocessor.process(sensorData)

            if (segments.isEmpty()) {
                return Result.failure(IllegalArgumentException(
                    "No se encontraron segmentos válidos. " +
                    "Verifica que el archivo tenga datos continuos de al menos 10 segundos."
                ))
            }
            android.util.Log.d(TAG, "Segmentos procesados: ${segments.size}")

            // 3. Análisis multi-ventana
            val result = analyzeMultiWindow(segments, startTime)

            android.util.Log.d(TAG, "Análisis completado: ${result.label} (${result.probability})")
            Result.success(result)

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error en análisis", e)
            Result.failure(e)
        }
    }

    /**
     * Analiza múltiples ventanas de todos los segmentos válidos.
     */
    private fun analyzeMultiWindow(segments: List<ProcessedSegment>, startTime: Long): StressResult {
        val allWindowResults = mutableListOf<WindowResult>()
        var totalDuration = 0f
        var windowsRejected = 0
        var duplicatesDetected = 0

        // Detectar y filtrar segmentos duplicados primero
        val uniqueSegments = filterDuplicateSegments(segments)
        if (uniqueSegments.size < segments.size) {
            duplicatesDetected = segments.size - uniqueSegments.size
            android.util.Log.w(TAG, "Detectados $duplicatesDetected segmentos duplicados, usando ${uniqueSegments.size}")
        }

        // Calcular HRV promedio de TODOS los segmentos (ECG + BVP combinados)
        val allHrvMetrics = mutableListOf<HRVMetrics>()
        for (segment in uniqueSegments) {
            val (_, hrvMetrics) = rPeakDetector.analyze(segment.ecgFiltered, segment.bvpFiltered)
            if (hrvMetrics.isValid()) {
                allHrvMetrics.add(hrvMetrics)
            }
        }
        val avgHrvMetrics = averageHRVMetrics(allHrvMetrics)
        android.util.Log.d(TAG, "HRV promedio de ${allHrvMetrics.size} segmentos: " +
            "HR_ECG=${avgHrvMetrics.hrEcgBpm}, HR_BVP=${avgHrvMetrics.hrBvpBpm}, HR_Combined=${avgHrvMetrics.hrBpm}")

        // Para calidad, usar el mejor segmento
        val bestSegment = selectBestSegment(uniqueSegments)
        val bestPeaks = rPeakDetector.detectRPeaks(bestSegment.ecgFiltered)
        val bestHrv = rPeakDetector.computeHRVMetrics(bestPeaks)
        val quality = qualityChecker.checkQuality(bestSegment.ecgFiltered, bestPeaks, bestHrv)
        if (uniqueSegments.size < segments.size) {
            duplicatesDetected = segments.size - uniqueSegments.size
            android.util.Log.w(TAG, "Detectados $duplicatesDetected segmentos duplicados, usando ${uniqueSegments.size}")
        }

        // Procesar cada segmento único
        for (segment in uniqueSegments) {
            totalDuration += segment.durationS

            // Log para detectar duplicados
            val ecgHash = segment.ecgNormalized.take(100).hashCode()
            val bvpHash = segment.bvpFiltered.take(100).hashCode()
            android.util.Log.d(TAG, "Segmento #${segment.segmentId}: duration=${segment.durationS}s, " +
                "samples=${segment.ecgNormalized.size}, ecgHash=$ecgHash, bvpHash=$bvpHash")

            // Extraer ventanas de este segmento con evaluación de calidad
            val (windows, rejected) = extractWindowsWithQuality(segment)
            windowsRejected += rejected
            android.util.Log.d(TAG, "Segmento #${segment.segmentId}: ${windows.size} ventanas válidas, $rejected rechazadas")

            // Ejecutar inferencia en cada ventana válida
            for ((windowIdx, window) in windows.withIndex()) {
                val probability = tfliteRunner.predict(window.input)

                allWindowResults.add(WindowResult(
                    segmentId = segment.segmentId,
                    windowIndex = windowIdx,
                    startS = window.startS,
                    probability = probability,
                    isStressed = probability >= 0.5f
                ))
            }
        }

        android.util.Log.d(TAG, "Total: ${allWindowResults.size} ventanas válidas, $windowsRejected rechazadas por calidad")

        val endTime = System.currentTimeMillis()

        // Calcular estadísticas
        return if (allWindowResults.isEmpty()) {
            // Fallback: usar solo el mejor segmento (una ventana)
            val input = prepareModelInput(bestSegment)
            val prob = tfliteRunner.predict(input)

            StressResult(
                probability = prob,
                probabilityMedian = prob,
                label = if (prob >= 0.5f) "ESTRÉS" else "NORMAL",
                isStressed = prob >= 0.5f,
                stressLevel = getStressLevel(prob),
                totalWindows = 1,
                stressedWindows = if (prob >= 0.5f) 1 else 0,
                stressPercentage = if (prob >= 0.5f) 100f else 0f,
                probabilityMin = prob,
                probabilityMax = prob,
                probabilityStd = 0f,
                windowsRejected = windowsRejected,
                duplicatesDetected = duplicatesDetected,
                windowResults = listOf(WindowResult(bestSegment.segmentId, 0, 0f, prob, prob >= 0.5f)),
                segmentsUsed = 1,
                totalDurationS = bestSegment.durationS,
                quality = quality,
                hrvMetrics = avgHrvMetrics,
                processingTimeMs = endTime - startTime
            )
        } else {
            calculateAggregatedResult(
                allWindowResults,
                uniqueSegments.size,
                totalDuration,
                quality,
                avgHrvMetrics,
                endTime - startTime,
                windowsRejected,
                duplicatesDetected
            )
        }
    }

    /**
     * Filtra segmentos duplicados basándose en hash de contenido.
     */
    private fun filterDuplicateSegments(segments: List<ProcessedSegment>): List<ProcessedSegment> {
        val seen = mutableSetOf<Long>()
        val unique = mutableListOf<ProcessedSegment>()

        for (segment in segments) {
            // Crear hash combinando ECG y BVP
            val ecgSample = segment.ecgNormalized.take(200)
            val bvpSample = segment.bvpFiltered.take(200)
            val combinedHash = ecgSample.hashCode().toLong() * 31 + bvpSample.hashCode()

            if (combinedHash !in seen) {
                seen.add(combinedHash)
                unique.add(segment)
            } else {
                android.util.Log.w(TAG, "Segmento #${segment.segmentId} detectado como duplicado, ignorando")
            }
        }

        return unique
    }

    /**
     * Extrae ventanas de 10s de un segmento con evaluación de calidad.
     * Retorna par de (ventanas válidas, cantidad rechazadas)
     */
    private data class WindowData(val input: FloatArray, val startS: Float, val qualityScore: Float)

    private fun extractWindowsWithQuality(segment: ProcessedSegment): Pair<List<WindowData>, Int> {
        val windows = mutableListOf<WindowData>()
        var rejected = 0

        val ecg = segment.ecgNormalized
        val bvpNorm = normalizeZScore(segment.bvpFiltered)

        val windowSamples = (WINDOW_S * FS).toInt()
        val stepSamples = (windowSamples * (1 - OVERLAP)).toInt().coerceAtLeast(1)

        val minLen = minOf(ecg.size, bvpNorm.size)
        if (minLen < windowSamples) return Pair(windows, 0)

        var start = 0
        while (start + windowSamples <= minLen) {
            val end = start + windowSamples

            val segEcg = ecg.sliceArray(start until end)
            val segBvp = bvpNorm.sliceArray(start until end)

            // Evaluar calidad de la ventana
            val qualityResult = evaluateWindowQuality(segEcg, segBvp)

            if (qualityResult.isValid) {
                val input = FloatArray(MODEL_INPUT_SIZE * NUM_CHANNELS)

                for (i in 0 until MODEL_INPUT_SIZE) {
                    input[i * NUM_CHANNELS] = segEcg[i]
                    input[i * NUM_CHANNELS + 1] = segBvp[i]
                }

                windows.add(WindowData(input, start / FS, qualityResult.score))
            } else {
                rejected++
                if (rejected <= 3) { // Solo log las primeras 3 rechazadas
                    android.util.Log.d(TAG, "Ventana rechazada @${start/FS}s: ${qualityResult.reason}")
                }
            }

            start += stepSamples
        }

        return Pair(windows, rejected)
    }

    /**
     * Resultado de evaluación de calidad de ventana.
     */
    private data class WindowQuality(
        val isValid: Boolean,
        val score: Float,      // 0-1, mayor es mejor
        val reason: String     // Razón de rechazo si isValid=false
    )

    /**
     * Evalúa la calidad de una ventana de señal.
     * Retorna si es válida para inferencia.
     */
    private fun evaluateWindowQuality(ecg: FloatArray, bvp: FloatArray): WindowQuality {
        // 1. Verificar desviación estándar (no muy plana ni muy ruidosa)
        val stdEcg = standardDev(ecg)
        val stdBvp = standardDev(bvp)

        if (stdEcg < MIN_STD || stdBvp < MIN_STD) {
            return WindowQuality(false, 0f, "Señal plana (std ECG=${stdEcg.f2()}, BVP=${stdBvp.f2()})")
        }

        if (stdEcg > MAX_STD || stdBvp > MAX_STD) {
            return WindowQuality(false, 0f, "Señal muy ruidosa (std ECG=${stdEcg.f2()}, BVP=${stdBvp.f2()})")
        }

        // 2. Detectar artefactos (valores extremos)
        val ecgArtifacts = countArtifacts(ecg)
        val bvpArtifacts = countArtifacts(bvp)
        val artifactRatio = (ecgArtifacts + bvpArtifacts).toFloat() / (ecg.size + bvp.size)

        if (artifactRatio > MAX_ARTIFACT_RATIO) {
            return WindowQuality(false, 0f, "Demasiados artefactos (${(artifactRatio*100).f1()}%)")
        }

        // 3. Verificar SNR estimado (señal vs ruido de alta frecuencia)
        val snrEcg = estimateSNR(ecg)
        val snrBvp = estimateSNR(bvp)

        if (snrEcg < MIN_SNR || snrBvp < MIN_SNR) {
            return WindowQuality(false, 0f, "SNR bajo (ECG=${snrEcg.f2()}, BVP=${snrBvp.f2()})")
        }

        // 4. Verificar cruces por cero excesivos (ruido de alta frecuencia)
        val zcRatioEcg = zeroCrossingRatio(ecg)
        val zcRatioBvp = zeroCrossingRatio(bvp)

        if (zcRatioEcg > MAX_ZERO_CROSSINGS_RATIO || zcRatioBvp > MAX_ZERO_CROSSINGS_RATIO) {
            return WindowQuality(false, 0f, "Ruido alta frecuencia (ZC ECG=${zcRatioEcg.f2()}, BVP=${zcRatioBvp.f2()})")
        }

        // 5. Verificar que hay periodicidad (señal cardíaca)
        val hasPeriodicityEcg = hasCardiacPeriodicity(ecg)
        if (!hasPeriodicityEcg) {
            return WindowQuality(false, 0f, "Sin periodicidad cardíaca detectada")
        }

        // Calcular score de calidad (0-1)
        val stdScore = 1f - kotlin.math.abs(stdEcg - 1f).coerceIn(0f, 1f)  // Ideal std ~ 1
        val snrScore = ((snrEcg + snrBvp) / 2f / 10f).coerceIn(0f, 1f)
        val artifactScore = 1f - (artifactRatio / MAX_ARTIFACT_RATIO).coerceIn(0f, 1f)
        val zcScore = 1f - ((zcRatioEcg + zcRatioBvp) / 2f / MAX_ZERO_CROSSINGS_RATIO).coerceIn(0f, 1f)

        val score = (stdScore + snrScore + artifactScore + zcScore) / 4f

        if (score < MIN_QUALITY_SCORE) {
            return WindowQuality(false, score, "Score bajo (${score.f2()})")
        }

        return WindowQuality(true, score, "OK")
    }

    /**
     * Cuenta artefactos (valores a más de 4 desviaciones estándar).
     */
    private fun countArtifacts(signal: FloatArray): Int {
        val mean = signal.average().toFloat()
        val std = standardDev(signal)
        val threshold = 4f * std

        return signal.count { kotlin.math.abs(it - mean) > threshold }
    }

    /**
     * Estima SNR comparando varianza de señal vs varianza de diferencias (ruido).
     */
    private fun estimateSNR(signal: FloatArray): Float {
        if (signal.size < 2) return 0f

        val signalVar = variance(signal)

        // Estimar ruido como varianza de diferencias de primer orden
        val diffs = FloatArray(signal.size - 1) { signal[it + 1] - signal[it] }
        val noiseVar = variance(diffs) / 2f  // Factor 2 por diferenciación

        if (noiseVar < 1e-8f) return 100f  // Señal muy limpia

        return signalVar / noiseVar
    }

    /**
     * Calcula ratio de cruces por cero.
     */
    private fun zeroCrossingRatio(signal: FloatArray): Float {
        if (signal.size < 2) return 0f

        val mean = signal.average().toFloat()
        var crossings = 0

        for (i in 1 until signal.size) {
            if ((signal[i-1] - mean) * (signal[i] - mean) < 0) {
                crossings++
            }
        }

        return crossings.toFloat() / signal.size
    }

    /**
     * Detecta si hay periodicidad cardíaca (40-180 BPM = 0.67-3 Hz).
     * Usa autocorrelación simplificada.
     */
    private fun hasCardiacPeriodicity(ecg: FloatArray): Boolean {
        // Rango de lags para frecuencias cardíacas típicas (40-180 BPM)
        val minLag = (FS / 3f).toInt()    // 180 BPM = 3 Hz
        val maxLag = (FS / 0.67f).toInt() // 40 BPM = 0.67 Hz

        if (ecg.size < maxLag + 100) return true // Asumir ok si muy corta

        val mean = ecg.average().toFloat()
        val centered = FloatArray(ecg.size) { ecg[it] - mean }

        // Calcular autocorrelación normalizada en el rango cardíaco
        val autoCorr0 = centered.map { it * it }.sum()
        if (autoCorr0 < 1e-8f) return false

        var maxCorr = 0f
        for (lag in minLag..minOf(maxLag, ecg.size / 2)) {
            var sum = 0f
            for (i in 0 until ecg.size - lag) {
                sum += centered[i] * centered[i + lag]
            }
            val corr = sum / autoCorr0
            if (corr > maxCorr) maxCorr = corr
        }

        // Si hay pico de correlación significativo, hay periodicidad
        return maxCorr > 0.2f
    }

    private fun variance(arr: FloatArray): Float {
        if (arr.isEmpty()) return 0f
        val mean = arr.average().toFloat()
        return arr.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    // Extensiones para formateo
    private fun Float.f1() = "%.1f".format(this)
    private fun Float.f2() = "%.2f".format(this)

    /**
     * Calcula resultado agregado de todas las ventanas.
     */
    private fun calculateAggregatedResult(
        windowResults: List<WindowResult>,
        segmentsUsed: Int,
        totalDurationS: Float,
        quality: QualityResult,
        hrvMetrics: HRVMetrics,
        processingTimeMs: Long,
        windowsRejected: Int = 0,
        duplicatesDetected: Int = 0
    ): StressResult {
        val probabilities = windowResults.map { it.probability }

        // Estadísticas básicas
        val mean = probabilities.average().toFloat()
        val sorted = probabilities.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        } else {
            sorted[sorted.size / 2]
        }
        val min = probabilities.minOrNull() ?: 0f
        val max = probabilities.maxOrNull() ?: 0f
        val std = standardDev(probabilities.toFloatArray())

        val stressedCount = windowResults.count { it.isStressed }
        val stressPercentage = (stressedCount.toFloat() / windowResults.size) * 100f

        android.util.Log.d(TAG, "Multi-window: ${windowResults.size} ventanas válidas, " +
            "$windowsRejected rechazadas, mean=${mean}, median=${median}, stressed=$stressedCount (${stressPercentage}%)")

        return StressResult(
            probability = mean,
            probabilityMedian = median,
            label = if (mean >= 0.5f) "ESTRÉS" else "NORMAL",
            isStressed = mean >= 0.5f,
            stressLevel = getStressLevel(mean),
            totalWindows = windowResults.size,
            stressedWindows = stressedCount,
            stressPercentage = stressPercentage,
            probabilityMin = min,
            probabilityMax = max,
            probabilityStd = std,
            windowsRejected = windowsRejected,
            duplicatesDetected = duplicatesDetected,
            windowResults = windowResults,
            segmentsUsed = segmentsUsed,
            totalDurationS = totalDurationS,
            quality = quality,
            hrvMetrics = hrvMetrics,
            processingTimeMs = processingTimeMs
        )
    }

    /**
     * Calcula el promedio de métricas HRV de múltiples segmentos.
     */
    private fun averageHRVMetrics(metricsList: List<HRVMetrics>): HRVMetrics {
        if (metricsList.isEmpty()) {
            return HRVMetrics.empty()
        }

        val validEcg = metricsList.filter { !it.hrEcgBpm.isNaN() }
        val validBvp = metricsList.filter { !it.hrBvpBpm.isNaN() }
        val validCombined = metricsList.filter { !it.hrBpm.isNaN() }

        val hrEcgAvg = if (validEcg.isNotEmpty()) validEcg.map { it.hrEcgBpm }.average().toFloat() else Float.NaN
        val hrBvpAvg = if (validBvp.isNotEmpty()) validBvp.map { it.hrBvpBpm }.average().toFloat() else Float.NaN
        val hrCombinedAvg = if (validCombined.isNotEmpty()) validCombined.map { it.hrBpm }.average().toFloat() else Float.NaN

        val validRmssd = metricsList.filter { !it.rmssd.isNaN() }
        val validSdnn = metricsList.filter { !it.sdnn.isNaN() }
        val validPnn50 = metricsList.filter { !it.pnn50.isNaN() }
        val validRrMean = metricsList.filter { !it.rrMeanS.isNaN() }
        val validRrStd = metricsList.filter { !it.rrStdS.isNaN() }

        return HRVMetrics(
            hrBpm = hrCombinedAvg,
            hrEcgBpm = hrEcgAvg,
            hrBvpBpm = hrBvpAvg,
            rrMeanS = if (validRrMean.isNotEmpty()) validRrMean.map { it.rrMeanS }.average().toFloat() else Float.NaN,
            rrStdS = if (validRrStd.isNotEmpty()) validRrStd.map { it.rrStdS }.average().toFloat() else Float.NaN,
            rrCv = if (validRrMean.isNotEmpty() && validRrStd.isNotEmpty()) {
                validRrStd.map { it.rrStdS }.average().toFloat() / validRrMean.map { it.rrMeanS }.average().toFloat()
            } else Float.NaN,
            rmssd = if (validRmssd.isNotEmpty()) validRmssd.map { it.rmssd }.average().toFloat() else Float.NaN,
            sdnn = if (validSdnn.isNotEmpty()) validSdnn.map { it.sdnn }.average().toFloat() else Float.NaN,
            pnn50 = if (validPnn50.isNotEmpty()) validPnn50.map { it.pnn50 }.average().toFloat() else Float.NaN,
            numPeaksEcg = metricsList.sumOf { it.numPeaksEcg },
            numPeaksBvp = metricsList.sumOf { it.numPeaksBvp }
        )
    }

    /**
     * Determina el nivel de estrés basado en la probabilidad.
     */
    private fun getStressLevel(probability: Float): String {
        return when {
            probability < 0.25f -> "Bajo"
            probability < 0.50f -> "Moderado-Bajo"
            probability < 0.75f -> "Moderado-Alto"
            else -> "Alto"
        }
    }

    /**
     * Selecciona el mejor segmento basado en calidad.
     */
    private fun selectBestSegment(segments: List<ProcessedSegment>): ProcessedSegment {
        data class ScoredSegment(val segment: ProcessedSegment, val quality: QualityResult)

        val scored = segments.map { seg ->
            val peaks = rPeakDetector.detectRPeaks(seg.ecgFiltered)
            val hrv = rPeakDetector.computeHRVMetrics(peaks)
            val quality = qualityChecker.checkQuality(seg.ecgFiltered, peaks, hrv)
            ScoredSegment(seg, quality)
        }

        val sorted = scored.sortedWith(compareBy(
            { if (it.quality.isOk) 0 else 1 },
            { -it.quality.score },
            { -it.segment.durationS }
        ))

        return sorted.first().segment
    }

    /**
     * Prepara el input para una ventana central del segmento (fallback).
     */
    private fun prepareModelInput(segment: ProcessedSegment): FloatArray {
        val input = FloatArray(MODEL_INPUT_SIZE * NUM_CHANNELS)

        val ecg = segment.ecgNormalized
        val bvpNorm = normalizeZScore(segment.bvpFiltered)

        val offset = if (ecg.size > MODEL_INPUT_SIZE) {
            (ecg.size - MODEL_INPUT_SIZE) / 2
        } else 0

        val availableSamples = minOf(ecg.size - offset, bvpNorm.size - offset, MODEL_INPUT_SIZE)

        for (i in 0 until MODEL_INPUT_SIZE) {
            val srcIdx = offset + i
            if (i < availableSamples && srcIdx < ecg.size && srcIdx < bvpNorm.size) {
                input[i * NUM_CHANNELS] = ecg[srcIdx]
                input[i * NUM_CHANNELS + 1] = bvpNorm[srcIdx]
            } else {
                input[i * NUM_CHANNELS] = 0f
                input[i * NUM_CHANNELS + 1] = 0f
            }
        }

        return input
    }

    private fun standardDev(arr: FloatArray): Float {
        if (arr.isEmpty()) return 0f
        val mean = arr.average().toFloat()
        var sumSq = 0f
        for (v in arr) sumSq += (v - mean) * (v - mean)
        return kotlin.math.sqrt(sumSq / arr.size)
    }

    /**
     * Normalización z-score.
     */
    private fun normalizeZScore(signal: FloatArray): FloatArray {
        val mean = signal.average().toFloat()
        var sumSq = 0f
        for (v in signal) sumSq += (v - mean) * (v - mean)
        val std = kotlin.math.sqrt(sumSq / signal.size) + 1e-8f

        return FloatArray(signal.size) { (signal[it] - mean) / std }
    }

    /**
     * Libera recursos.
     */
    fun close() {
        tfliteRunner.close()
        modelLoaded = false
    }
}
