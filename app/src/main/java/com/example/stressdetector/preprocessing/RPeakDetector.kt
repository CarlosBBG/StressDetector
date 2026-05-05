package com.example.stressdetector.preprocessing

import kotlin.math.*

/**
 * Configuración para la detección de picos R en ECG.
 */
data class RPeakConfig(
    val fs: Float = 400f,                 // Frecuencia de muestreo
    val minRRS: Float = 0.30f,            // Intervalo RR mínimo en segundos
    val qrsBandLow: Float = 5f,           // Filtro QRS - frecuencia baja
    val qrsBandHigh: Float = 20f,         // Filtro QRS - frecuencia alta
    val prominenceFactor: Float = 3f      // Factor para calcular prominencia (x sigma)
)

/**
 * Métricas HRV calculadas a partir de los picos R.
 */
data class HRVMetrics(
    val hrBpm: Float,                     // Heart rate en BPM (combinado ECG+BVP)
    val hrEcgBpm: Float,                  // Heart rate solo de ECG
    val hrBvpBpm: Float,                  // Heart rate solo de BVP
    val rrMeanS: Float,                   // Media de intervalos RR (segundos)
    val rrStdS: Float,                    // Desviación estándar de RR (segundos)
    val rrCv: Float,                      // Coeficiente de variación de RR
    val rmssd: Float,                     // Root mean square of successive differences (ms)
    val sdnn: Float,                      // Standard deviation of NN intervals (ms)
    val pnn50: Float,                     // Porcentaje de diferencias > 50ms
    val numPeaksEcg: Int,                 // Número de picos R detectados en ECG
    val numPeaksBvp: Int                  // Número de picos detectados en BVP
) {
    fun isValid(): Boolean = !hrBpm.isNaN() && hrBpm > 0 && (numPeaksEcg >= 2 || numPeaksBvp >= 2)
    
    companion object {
        fun empty() = HRVMetrics(
            hrBpm = Float.NaN, hrEcgBpm = Float.NaN, hrBvpBpm = Float.NaN,
            rrMeanS = Float.NaN, rrStdS = Float.NaN, rrCv = Float.NaN,
            rmssd = Float.NaN, sdnn = Float.NaN, pnn50 = Float.NaN,
            numPeaksEcg = 0, numPeaksBvp = 0
        )
    }
}

/**
 * Detector de picos R en señales ECG y picos sistólicos en BVP.
 * Combina ambas señales para una estimación más robusta de HR.
 */
class RPeakDetector(private val config: RPeakConfig = RPeakConfig()) {
    
    private val preprocessor = SignalPreprocessor()
    
    companion object {
        private const val TAG = "RPeakDetector"
    }
    
    /**
     * Detecta picos R en una señal ECG.
     * @param ecg Señal ECG (preferiblemente ya filtrada)
     * @return Lista de índices donde se encontraron picos R
     */
    fun detectRPeaks(ecg: FloatArray): List<Int> {
        // Filtrar para resaltar QRS
        val ecgQrs = preprocessor.bandpassFilter(ecg, config.fs, config.qrsBandLow, config.qrsBandHigh)
        
        // Usar valor absoluto para detectar
        val det = FloatArray(ecgQrs.size) { abs(ecgQrs[it]) }
        
        // Calcular prominencia basada en MAD
        val sorted = det.sortedArray()
        val median = sorted[sorted.size / 2]
        val deviations = FloatArray(det.size) { abs(det[it] - median) }
        val sortedDev = deviations.sortedArray()
        val mad = sortedDev[sortedDev.size / 2] + 1e-12f
        val sigma = 1.4826f * mad
        val prominence = config.prominenceFactor * sigma
        
        // Distancia mínima entre picos
        val minDistance = (config.minRRS * config.fs).toInt()
        
        // Encontrar picos
        return findPeaksWithProminence(det, minDistance, prominence)
    }
    
    /**
     * Detecta picos sistólicos en señal BVP/PPG.
     * Los picos sistólicos corresponden al máximo de cada pulso.
     */
    fun detectBvpPeaks(bvp: FloatArray): List<Int> {
        // Filtrar BVP para resaltar pulsos (0.5-4 Hz = 30-240 BPM)
        val bvpFiltered = preprocessor.bandpassFilter(bvp, config.fs, 0.5f, 4f)
        
        // Calcular prominencia basada en MAD
        val sorted = bvpFiltered.sortedArray()
        val median = sorted[sorted.size / 2]
        val deviations = FloatArray(bvpFiltered.size) { abs(bvpFiltered[it] - median) }
        val sortedDev = deviations.sortedArray()
        val mad = sortedDev[sortedDev.size / 2] + 1e-12f
        val sigma = 1.4826f * mad
        val prominence = 2.0f * sigma  // Menos restrictivo que ECG
        
        // Distancia mínima entre picos (igual que ECG)
        val minDistance = (config.minRRS * config.fs).toInt()
        
        // Encontrar picos (máximos locales)
        return findPeaksWithProminence(bvpFiltered, minDistance, prominence)
    }
    
    /**
     * Calcula métricas HRV combinando ECG y BVP.
     */
    fun computeHRVMetrics(ecgPeaks: List<Int>, bvpPeaks: List<Int>): HRVMetrics {
        // Calcular HR de cada señal
        val hrEcg = calculateHRFromPeaks(ecgPeaks)
        val hrBvp = calculateHRFromPeaks(bvpPeaks)
        
        // Combinar HR: usar promedio ponderado si ambos son válidos
        val hrCombined = when {
            hrEcg.isNaN() && hrBvp.isNaN() -> Float.NaN
            hrEcg.isNaN() -> hrBvp
            hrBvp.isNaN() -> hrEcg
            else -> {
                // Ponderar por número de picos (más picos = más confiable)
                val totalPeaks = ecgPeaks.size + bvpPeaks.size
                (hrEcg * ecgPeaks.size + hrBvp * bvpPeaks.size) / totalPeaks
            }
        }
        
        // Usar la señal con más picos para métricas HRV detalladas
        val primaryPeaks = if (ecgPeaks.size >= bvpPeaks.size) ecgPeaks else bvpPeaks
        
        if (primaryPeaks.size < 2) {
            return HRVMetrics(
                hrBpm = hrCombined,
                hrEcgBpm = hrEcg,
                hrBvpBpm = hrBvp,
                rrMeanS = Float.NaN,
                rrStdS = Float.NaN,
                rrCv = Float.NaN,
                rmssd = Float.NaN,
                sdnn = Float.NaN,
                pnn50 = Float.NaN,
                numPeaksEcg = ecgPeaks.size,
                numPeaksBvp = bvpPeaks.size
            )
        }
        
        // Calcular intervalos RR en segundos
        val rr = FloatArray(primaryPeaks.size - 1) { i ->
            (primaryPeaks[i + 1] - primaryPeaks[i]) / config.fs
        }
        
        // Estadísticas básicas
        val rrMean = rr.average().toFloat()
        val rrStd = standardDeviation(rr)
        val rrCv = rrStd / (rrMean + 1e-12f)
        
        // Métricas HRV en milisegundos
        val rrMs = FloatArray(rr.size) { rr[it] * 1000f }
        val diffRr = FloatArray(rrMs.size - 1) { i ->
            rrMs[i + 1] - rrMs[i]
        }
        
        // RMSSD
        val rmssd = if (diffRr.isNotEmpty()) {
            val sumSq = diffRr.sumOf { (it * it).toDouble() }
            sqrt(sumSq / diffRr.size).toFloat()
        } else Float.NaN
        
        // SDNN
        val sdnn = standardDeviation(rrMs)
        
        // pNN50
        val pnn50 = if (diffRr.isNotEmpty()) {
            val count50 = diffRr.count { abs(it) > 50f }
            count50.toFloat() / diffRr.size
        } else Float.NaN
        
        return HRVMetrics(
            hrBpm = hrCombined,
            hrEcgBpm = hrEcg,
            hrBvpBpm = hrBvp,
            rrMeanS = rrMean,
            rrStdS = rrStd,
            rrCv = rrCv,
            rmssd = rmssd,
            sdnn = sdnn,
            pnn50 = pnn50,
            numPeaksEcg = ecgPeaks.size,
            numPeaksBvp = bvpPeaks.size
        )
    }
    
    /**
     * Versión legacy: calcula métricas solo con picos ECG.
     */
    fun computeHRVMetrics(peaks: List<Int>): HRVMetrics {
        return computeHRVMetrics(peaks, emptyList())
    }
    
    /**
     * Calcula HR en BPM a partir de picos.
     */
    private fun calculateHRFromPeaks(peaks: List<Int>): Float {
        if (peaks.size < 2) return Float.NaN
        
        val rr = FloatArray(peaks.size - 1) { i ->
            (peaks[i + 1] - peaks[i]) / config.fs
        }
        val rrMean = rr.average().toFloat()
        return if (rrMean > 0) 60f / rrMean else Float.NaN
    }
    
    /**
     * Detecta picos R y BVP, calcula métricas combinadas.
     */
    fun analyze(ecg: FloatArray, bvp: FloatArray): Pair<List<Int>, HRVMetrics> {
        val ecgPeaks = detectRPeaks(ecg)
        val bvpPeaks = detectBvpPeaks(bvp)
        val metrics = computeHRVMetrics(ecgPeaks, bvpPeaks)
        
        android.util.Log.d(TAG, "ECG peaks: ${ecgPeaks.size}, BVP peaks: ${bvpPeaks.size}, " +
            "HR_ECG: ${metrics.hrEcgBpm}, HR_BVP: ${metrics.hrBvpBpm}, HR_Combined: ${metrics.hrBpm}")
        
        return ecgPeaks to metrics
    }
    
    /**
     * Versión legacy para compatibilidad.
     */
    fun analyze(ecg: FloatArray): Pair<List<Int>, HRVMetrics> {
        val peaks = detectRPeaks(ecg)
        val metrics = computeHRVMetrics(peaks)
        return peaks to metrics
    }
    
    /**
     * Encuentra picos con prominencia mínima.
     */
    private fun findPeaksWithProminence(
        signal: FloatArray, 
        minDistance: Int, 
        minProminence: Float
    ): List<Int> {
        val peaks = mutableListOf<Int>()
        
        for (i in 1 until signal.size - 1) {
            // Es un máximo local?
            if (signal[i] <= signal[i - 1] || signal[i] <= signal[i + 1]) continue
            
            // Calcular prominencia (diferencia con los valles vecinos)
            val leftMin = findLeftMin(signal, i)
            val rightMin = findRightMin(signal, i)
            val prominence = signal[i] - maxOf(leftMin, rightMin)
            
            if (prominence < minProminence) continue
            
            // Verificar distancia mínima
            if (peaks.isEmpty() || i - peaks.last() >= minDistance) {
                peaks.add(i)
            } else if (signal[i] > signal[peaks.last()]) {
                // Reemplazar si este pico es mayor
                peaks[peaks.lastIndex] = i
            }
        }
        
        return peaks
    }
    
    private fun findLeftMin(signal: FloatArray, peakIdx: Int): Float {
        var minVal = signal[peakIdx]
        for (i in peakIdx - 1 downTo 0) {
            if (signal[i] < minVal) minVal = signal[i]
            if (signal[i] > signal[peakIdx]) break
        }
        return minVal
    }
    
    private fun findRightMin(signal: FloatArray, peakIdx: Int): Float {
        var minVal = signal[peakIdx]
        for (i in peakIdx + 1 until signal.size) {
            if (signal[i] < minVal) minVal = signal[i]
            if (signal[i] > signal[peakIdx]) break
        }
        return minVal
    }
    
    private fun standardDeviation(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        var sumSq = 0f
        for (v in values) sumSq += (v - mean) * (v - mean)
        return sqrt(sumSq / values.size)
    }
}
