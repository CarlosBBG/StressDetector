package com.example.stressdetector.preprocessing

import kotlin.math.*

/**
 * Configuración del preprocesamiento de señales.
 * Valores basados en el pipeline Python para MAX86150.
 */
data class PreprocessConfig(
    val fsTarget: Float = 400f,           // Frecuencia de muestreo objetivo
    val gapThresholdS: Float = 0.5f,      // Umbral de gap para segmentación (segundos)
    val minSegmentS: Float = 10f,         // Duración mínima de segmento (segundos)
    val minSamples: Int = 200,            // Mínimo de muestras por segmento
    
    // Filtros
    val ecgBandLow: Float = 0.5f,         // Filtro pasabanda ECG - frecuencia baja
    val ecgBandHigh: Float = 40f,         // Filtro pasabanda ECG - frecuencia alta
    val bvpBandLow: Float = 0.3f,         // Filtro pasabanda BVP - frecuencia baja
    val bvpBandHigh: Float = 8f,          // Filtro pasabanda BVP - frecuencia alta
    val qrsBandLow: Float = 5f,           // Filtro QRS - frecuencia baja
    val qrsBandHigh: Float = 20f,         // Filtro QRS - frecuencia alta
    val filterOrder: Int = 4,             // Orden del filtro Butterworth
    
    // Recorte de bordes
    val edgeCropS: Float = 0.5f,          // Segundos a recortar de cada borde
    
    // Clipping robusto
    val clipK: Float = 6f,                // Factor K para clipping basado en MAD
    
    // Rango de frecuencia de muestreo válido
    val fsOkMin: Float = 360f,
    val fsOkMax: Float = 460f
)

/**
 * Segmento de señal procesado, listo para análisis.
 */
data class ProcessedSegment(
    val segmentId: Int,
    val startIdx: Int,
    val endIdx: Int,
    val durationS: Float,
    val effectiveFs: Float,
    val timeAxis: FloatArray,             // Eje de tiempo uniforme
    val ecgFiltered: FloatArray,          // ECG filtrado y normalizado
    val bvpFiltered: FloatArray,          // BVP filtrado y normalizado
    val ecgNormalized: FloatArray,        // ECG z-score normalizado
    val wasFlipped: Boolean,              // Si se invirtió polaridad
    val outlierRatioEcg: Float,           // Proporción de outliers en ECG
    val outlierRatioBvp: Float            // Proporción de outliers en BVP
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProcessedSegment
        return segmentId == other.segmentId
    }
    
    override fun hashCode(): Int = segmentId
}

/**
 * Preprocesador de señales ECG y BVP.
 * Implementa el mismo pipeline que el código Python:
 * - Segmentación por gaps
 * - Resampling uniforme
 * - Filtrado Butterworth
 * - Detección/corrección de polaridad
 * - Normalización z-score
 */
class SignalPreprocessor(private val config: PreprocessConfig = PreprocessConfig()) {
    
    companion object {
        private const val TAG = "SignalPreprocessor"
        private const val NORM_EPS = 1e-8f
    }
    
    /**
     * Procesa los datos crudos del sensor y retorna los segmentos válidos.
     */
    fun process(data: SensorData): List<ProcessedSegment> {
        if (data.isEmpty()) {
            android.util.Log.w(TAG, "Datos vacíos recibidos")
            return emptyList()
        }
        
        // 1. Segmentar por gaps temporales
        val segments = findSegmentsByGaps(data.timestamps)
        android.util.Log.d(TAG, "Segmentos encontrados: ${segments.size}")
        
        val results = mutableListOf<ProcessedSegment>()
        
        for ((idx, segment) in segments.withIndex()) {
            val (s0, s1) = segment
            
            // Extraer datos del segmento
            val segTimestamps = data.timestamps.sliceArray(s0 until s1)
            val segEcg = data.ecg.sliceArray(s0 until s1)
            val segBvp = data.bvp.sliceArray(s0 until s1)
            
            // Calcular frecuencia efectiva y duración
            val durationMs = (segTimestamps.last() - segTimestamps.first()).toFloat()
            val durationS = durationMs / 1000f
            val effectiveFs = if (durationS > 0) segTimestamps.size / durationS else 0f
            
            // Validar frecuencia y duración
            if (durationS < config.minSegmentS) continue
            if (effectiveFs < config.fsOkMin || effectiveFs > config.fsOkMax) continue
            
            try {
                // 2. Construir eje de tiempo por muestras
                val tSamples = buildSampleTimeAxis(segTimestamps)
                
                // 3. Resamplear a frecuencia uniforme
                val (tUniform, ecgUniform) = resampleUniform(tSamples, segEcg)
                val (_, bvpUniform) = resampleUniform(tSamples, segBvp)
                
                // 4. Centrar BVP
                val bvpCentered = centerSignal(bvpUniform)
                
                // 5. Clipping robusto e interpolación
                val (ecgClipped, ecgOutRatio) = robustClipAndInterpolate(ecgUniform)
                val (bvpClipped, bvpOutRatio) = robustClipAndInterpolate(bvpCentered)
                
                // 6. Decidir polaridad ECG
                val (ecgCorrected, flipped) = decideEcgPolarity(ecgClipped)
                
                // 7. Filtrado pasabanda
                val ecgFiltered = bandpassFilter(
                    ecgCorrected, 
                    config.fsTarget, 
                    config.ecgBandLow, 
                    config.ecgBandHigh
                )
                val bvpFiltered = bandpassFilter(
                    bvpClipped, 
                    config.fsTarget, 
                    config.bvpBandLow, 
                    config.bvpBandHigh
                )
                
                // 8. Recortar bordes
                val cropSamples = (config.edgeCropS * config.fsTarget).toInt()
                if (ecgFiltered.size <= 2 * cropSamples) continue
                
                val ecgCropped = ecgFiltered.sliceArray(cropSamples until ecgFiltered.size - cropSamples)
                val bvpCropped = bvpFiltered.sliceArray(cropSamples until bvpFiltered.size - cropSamples)
                val tCropped = tUniform.sliceArray(cropSamples until tUniform.size - cropSamples)
                
                // Verificar duración mínima tras recorte
                val finalDurationS = tCropped.size / config.fsTarget
                if (finalDurationS < config.minSegmentS) continue
                
                // 9. Normalización z-score
                val ecgNorm = zscore(ecgCropped)
                
                results.add(ProcessedSegment(
                    segmentId = idx + 1,
                    startIdx = s0,
                    endIdx = s1,
                    durationS = finalDurationS,
                    effectiveFs = effectiveFs,
                    timeAxis = tCropped,
                    ecgFiltered = ecgCropped,
                    bvpFiltered = bvpCropped,
                    ecgNormalized = ecgNorm,
                    wasFlipped = flipped,
                    outlierRatioEcg = ecgOutRatio,
                    outlierRatioBvp = bvpOutRatio
                ))
                
                android.util.Log.d(TAG, "Segmento ${idx + 1}: indices[$s0..$s1], " +
                    "duration=${finalDurationS}s, samples=${ecgNorm.size}")
                
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Error procesando segmento ${idx + 1}: ${e.message}")
            }
        }
        
        android.util.Log.d(TAG, "Segmentos válidos procesados: ${results.size}")
        return results
    }
    
    /**
     * Encuentra segmentos dividiendo por gaps temporales.
     */
    private fun findSegmentsByGaps(timestamps: LongArray): List<Pair<Int, Int>> {
        if (timestamps.size < 2) return emptyList()
        
        val gapThresholdMs = (config.gapThresholdS * 1000).toLong()
        val segments = mutableListOf<Pair<Int, Int>>()
        
        var segStart = 0
        for (i in 1 until timestamps.size) {
            val delta = timestamps[i] - timestamps[i - 1]
            if (delta > gapThresholdMs) {
                if (i - segStart >= config.minSamples) {
                    segments.add(segStart to i)
                }
                segStart = i
            }
        }
        
        // Agregar último segmento
        if (timestamps.size - segStart >= config.minSamples) {
            segments.add(segStart to timestamps.size)
        }
        
        return segments
    }
    
    /**
     * Construye eje de tiempo por muestra basándose en bloques de timestamps idénticos.
     */
    private fun buildSampleTimeAxis(timestamps: LongArray): FloatArray {
        val n = timestamps.size
        val tSamples = FloatArray(n)
        
        // Encontrar cambios de timestamp
        val changes = mutableListOf(0)
        for (i in 1 until n) {
            if (timestamps[i] != timestamps[i - 1]) {
                changes.add(i)
            }
        }
        changes.add(n)
        
        // Calcular tiempo base
        val t0 = timestamps[0]
        
        for (b in 0 until changes.size - 1) {
            val blockStart = changes[b]
            val blockEnd = changes[b + 1]
            val blockSize = blockEnd - blockStart
            
            val baseTime = (timestamps[blockStart] - t0) / 1000f
            
            // Calcular step para este bloque
            val step = if (b < changes.size - 2) {
                val nextBlockTime = (timestamps[changes[b + 1]] - t0) / 1000f
                (nextBlockTime - baseTime) / blockSize
            } else {
                1f / config.fsTarget
            }
            
            for (i in 0 until blockSize) {
                tSamples[blockStart + i] = baseTime + i * step
            }
        }
        
        return tSamples
    }
    
    /**
     * Resamplea la señal a frecuencia uniforme usando interpolación lineal.
     */
    private fun resampleUniform(tSamples: FloatArray, signal: FloatArray): Pair<FloatArray, FloatArray> {
        val tEnd = tSamples.last()
        val step = 1f / config.fsTarget
        val nUniform = (tEnd / step).toInt()
        
        val tUniform = FloatArray(nUniform) { it * step }
        val signalUniform = FloatArray(nUniform)
        
        // Interpolación lineal
        var j = 0
        for (i in 0 until nUniform) {
            val t = tUniform[i]
            
            // Buscar índice en tSamples
            while (j < tSamples.size - 1 && tSamples[j + 1] < t) {
                j++
            }
            
            if (j >= tSamples.size - 1) {
                signalUniform[i] = signal.last()
            } else {
                val t0 = tSamples[j]
                val t1 = tSamples[j + 1]
                val alpha = if (t1 > t0) (t - t0) / (t1 - t0) else 0f
                signalUniform[i] = signal[j] * (1 - alpha) + signal[j + 1] * alpha
            }
        }
        
        return tUniform to signalUniform
    }
    
    /**
     * Centra la señal restando la media.
     */
    private fun centerSignal(signal: FloatArray): FloatArray {
        val mean = signal.average().toFloat()
        return FloatArray(signal.size) { signal[it] - mean }
    }
    
    /**
     * Clipping robusto basado en MAD (Median Absolute Deviation).
     * Interpola valores fuera de rango.
     */
    private fun robustClipAndInterpolate(signal: FloatArray): Pair<FloatArray, Float> {
        val result = signal.copyOf()
        
        // Calcular mediana
        val sorted = signal.sortedArray()
        val median = sorted[sorted.size / 2]
        
        // Calcular MAD
        val deviations = FloatArray(signal.size) { abs(signal[it] - median) }
        val sortedDev = deviations.sortedArray()
        val mad = sortedDev[sortedDev.size / 2] + 1e-12f
        
        // Calcular sigma y límites
        val sigma = 1.4826f * mad
        val lo = median - config.clipK * sigma
        val hi = median + config.clipK * sigma
        
        // Detectar outliers
        val outlierMask = BooleanArray(signal.size) { signal[it] < lo || signal[it] > hi }
        var outlierCount = 0
        
        // Clipear
        for (i in result.indices) {
            if (outlierMask[i]) {
                result[i] = result[i].coerceIn(lo, hi)
                outlierCount++
            }
        }
        
        // Interpolar outliers
        if (outlierCount > 0 && outlierCount < signal.size - 2) {
            val validIndices = mutableListOf<Int>()
            val validValues = mutableListOf<Float>()
            
            for (i in result.indices) {
                if (!outlierMask[i]) {
                    validIndices.add(i)
                    validValues.add(result[i])
                }
            }
            
            if (validIndices.size >= 2) {
                for (i in result.indices) {
                    if (outlierMask[i]) {
                        result[i] = interpolateAt(i, validIndices, validValues)
                    }
                }
            }
        }
        
        val outlierRatio = outlierCount.toFloat() / signal.size
        return result to outlierRatio
    }
    
    /**
     * Interpola un valor en el índice dado usando los puntos válidos.
     */
    private fun interpolateAt(idx: Int, indices: List<Int>, values: List<Float>): Float {
        // Encontrar índices vecinos
        var left = -1
        var right = -1
        
        for (i in indices.indices) {
            if (indices[i] <= idx) left = i
            if (indices[i] >= idx && right == -1) right = i
        }
        
        return when {
            left == -1 -> values.first()
            right == -1 -> values.last()
            left == right -> values[left]
            else -> {
                val i0 = indices[left]
                val i1 = indices[right]
                val alpha = (idx - i0).toFloat() / (i1 - i0)
                values[left] * (1 - alpha) + values[right] * alpha
            }
        }
    }
    
    /**
     * Decide la polaridad del ECG analizando los picos QRS.
     */
    private fun decideEcgPolarity(ecg: FloatArray): Pair<FloatArray, Boolean> {
        // Filtrar para QRS
        val ecgQrs = bandpassFilter(ecg, config.fsTarget, config.qrsBandLow, config.qrsBandHigh)
        
        // Detectar picos
        val peaks = findLocalMaxima(ecgQrs.map { abs(it) }.toFloatArray(), 
            minDistance = (0.3f * config.fsTarget).toInt())
        
        if (peaks.size < 8) {
            return ecg to false
        }
        
        // Analizar signo de los picos
        val w = (0.04f * config.fsTarget).toInt()
        val signValues = mutableListOf<Float>()
        
        for (p in peaks) {
            val start = maxOf(0, p - w)
            val end = minOf(ecgQrs.size, p + w + 1)
            val segment = ecgQrs.sliceArray(start until end)
            
            if (segment.size < 3) continue
            
            val vmax = segment.maxOrNull() ?: 0f
            val vmin = segment.minOrNull() ?: 0f
            signValues.add(vmax + vmin)
        }
        
        if (signValues.size < 5) {
            return ecg to false
        }
        
        // Mediana del signo
        val sortedSigns = signValues.sorted()
        val medianSign = sortedSigns[sortedSigns.size / 2]
        
        val shouldFlip = medianSign < 0
        
        return if (shouldFlip) {
            FloatArray(ecg.size) { -ecg[it] } to true
        } else {
            ecg to false
        }
    }
    
    /**
     * Filtro Butterworth pasabanda de orden 4.
     * Usa la implementación correcta que replica scipy.signal.butter + sosfiltfilt.
     */
    fun bandpassFilter(signal: FloatArray, fs: Float, lowFreq: Float, highFreq: Float): FloatArray {
        return ButterworthFilter.bandpassFilter(signal, fs, lowFreq, highFreq, order = config.filterOrder)
    }
    
    /**
     * Filtro pasabajos IIR simple (1er orden).
     */
    private fun lowpassFilter(signal: FloatArray, normalizedFreq: Float): FloatArray {
        val alpha = normalizedFreq.coerceIn(0.01f, 0.99f)
        val result = FloatArray(signal.size)
        result[0] = signal[0]
        
        for (i in 1 until signal.size) {
            result[i] = alpha * signal[i] + (1 - alpha) * result[i - 1]
        }
        
        // Aplicar en reversa para fase cero
        val backward = FloatArray(signal.size)
        backward[signal.size - 1] = result[signal.size - 1]
        
        for (i in signal.size - 2 downTo 0) {
            backward[i] = alpha * result[i] + (1 - alpha) * backward[i + 1]
        }
        
        return backward
    }
    
    /**
     * Filtro pasaaltos IIR simple (1er orden).
     */
    private fun highpassFilter(signal: FloatArray, normalizedFreq: Float): FloatArray {
        val alpha = (1 - normalizedFreq).coerceIn(0.01f, 0.99f)
        val result = FloatArray(signal.size)
        result[0] = signal[0]
        
        for (i in 1 until signal.size) {
            result[i] = alpha * result[i - 1] + alpha * (signal[i] - signal[i - 1])
        }
        
        // Aplicar en reversa para fase cero
        val backward = FloatArray(signal.size)
        backward[signal.size - 1] = result[signal.size - 1]
        
        for (i in signal.size - 2 downTo 0) {
            backward[i] = alpha * backward[i + 1] + alpha * (result[i] - result[i + 1])
        }
        
        return backward
    }
    
    /**
     * Encuentra máximos locales en la señal.
     */
    private fun findLocalMaxima(signal: FloatArray, minDistance: Int = 1): List<Int> {
        val peaks = mutableListOf<Int>()
        
        for (i in 1 until signal.size - 1) {
            if (signal[i] > signal[i - 1] && signal[i] > signal[i + 1]) {
                // Verificar distancia mínima
                if (peaks.isEmpty() || i - peaks.last() >= minDistance) {
                    peaks.add(i)
                } else if (signal[i] > signal[peaks.last()]) {
                    peaks[peaks.lastIndex] = i
                }
            }
        }
        
        return peaks
    }
    
    /**
     * Normalización z-score.
     */
    private fun zscore(signal: FloatArray): FloatArray {
        val mean = signal.average().toFloat()
        var sumSq = 0f
        for (v in signal) sumSq += (v - mean) * (v - mean)
        val std = sqrt(sumSq / signal.size) + NORM_EPS
        
        return FloatArray(signal.size) { (signal[it] - mean) / std }
    }
}
