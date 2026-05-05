package com.example.stressdetector.preprocessing

import kotlin.math.*

/**
 * Configuración para el gate de calidad de señal.
 * Valores basados en el pipeline Python.
 */
data class QualityConfig(
    val fs: Float = 400f,
    
    // Rango de HR válido (BPM)
    val hrRangeMin: Float = 45f,
    val hrRangeMax: Float = 160f,
    
    // Mínimo de picos por segmento
    val minPeaks: Int = 10,
    
    // Ratio máximo de ruido de alta frecuencia
    val maxHfRatio: Float = 0.4f,
    
    // Ratio máximo de spikes
    val maxSpikeRatio: Float = 3.0f,
    
    // Rango de amplitud pico a pico
    val minP2p: Float = 0.05f,
    val maxP2p: Float = 3.0f,
    
    // Bandas de frecuencia para análisis de ruido
    val signalBandLow: Float = 0.5f,
    val signalBandHigh: Float = 40f,
    val noiseBandLow: Float = 40f,
    val noiseBandHigh: Float = 120f
)

/**
 * Resultado del análisis de calidad de un segmento.
 */
data class QualityResult(
    val isOk: Boolean,                    // Pasa el gate de calidad
    val score: Float,                     // Score de calidad (mayor = mejor)
    val hrBpm: Float,                     // Heart rate
    val numPeaks: Int,                    // Número de picos R
    val p2p: Float,                       // Amplitud pico a pico
    val hfRatio: Float,                   // Ratio de ruido HF
    val spikeRatio: Float,                // Ratio de spikes
    val failReasons: List<String>         // Razones de fallo (si aplica)
) {
    companion object {
        fun failed(reason: String): QualityResult {
            return QualityResult(
                isOk = false,
                score = 0f,
                hrBpm = Float.NaN,
                numPeaks = 0,
                p2p = 0f,
                hfRatio = Float.NaN,
                spikeRatio = Float.NaN,
                failReasons = listOf(reason)
            )
        }
    }
}

/**
 * Verificador de calidad de señal ECG.
 * Implementa el "strict gate" del código Python.
 */
class QualityChecker(private val config: QualityConfig = QualityConfig()) {
    
    private val rPeakDetector = RPeakDetector(RPeakConfig(fs = config.fs))
    
    /**
     * Evalúa la calidad de un segmento ECG.
     */
    fun checkQuality(ecgFiltered: FloatArray, peaks: List<Int>? = null, hrvMetrics: HRVMetrics? = null): QualityResult {
        val failReasons = mutableListOf<String>()
        
        // Obtener picos y métricas si no se proporcionaron
        val actualPeaks = peaks ?: rPeakDetector.detectRPeaks(ecgFiltered)
        val metrics = hrvMetrics ?: rPeakDetector.computeHRVMetrics(actualPeaks)
        
        val hr = metrics.hrBpm
        val numPeaks = actualPeaks.size
        
        // Calcular métricas de ruido
        val p2p = p2pRobust(ecgFiltered)
        val hfRatio = bandpowerRatio(ecgFiltered, config.fs)
        val spikeRatio = spikeRatio(ecgFiltered)
        
        // Verificar cada condición
        val okHr = hr.isFinite() && hr >= config.hrRangeMin && hr <= config.hrRangeMax
        if (!okHr) {
            failReasons.add("HR fuera de rango (${hr.format(1)} BPM, esperado ${config.hrRangeMin}-${config.hrRangeMax})")
        }
        
        val okPeaks = numPeaks >= config.minPeaks
        if (!okPeaks) {
            failReasons.add("Pocos picos R ($numPeaks, mínimo ${config.minPeaks})")
        }
        
        val okAmp = p2p >= config.minP2p && p2p <= config.maxP2p
        if (!okAmp) {
            failReasons.add("Amplitud P2P fuera de rango (${p2p.format(3)}, esperado ${config.minP2p}-${config.maxP2p})")
        }
        
        val okHf = hfRatio <= config.maxHfRatio
        if (!okHf) {
            failReasons.add("Ruido HF alto (${hfRatio.format(3)}, máximo ${config.maxHfRatio})")
        }
        
        val okSpike = spikeRatio <= config.maxSpikeRatio
        if (!okSpike) {
            failReasons.add("Spike ratio alto (${spikeRatio.format(2)}, máximo ${config.maxSpikeRatio})")
        }
        
        val isOk = okHr && okPeaks && okAmp && okHf && okSpike
        
        // Calcular score (mayor = mejor calidad)
        var score = 0f
        score += if (hr.isFinite()) 1f else 0f
        score += minOf(numPeaks / 40f, 1f)
        score += 1f / (1f + hfRatio)
        score += 1f / (1f + maxOf(0f, spikeRatio - 1f))
        
        return QualityResult(
            isOk = isOk,
            score = score,
            hrBpm = hr,
            numPeaks = numPeaks,
            p2p = p2p,
            hfRatio = hfRatio,
            spikeRatio = spikeRatio,
            failReasons = failReasons
        )
    }
    
    /**
     * Encuentra la mejor ventana de N segundos dentro del segmento.
     */
    fun findBestWindow(
        ecgFiltered: FloatArray,
        windowS: Float = 60f,
        stepS: Float = 5f
    ): Pair<IntRange, QualityResult>? {
        val windowSamples = (windowS * config.fs).toInt()
        val stepSamples = (stepS * config.fs).toInt()
        
        if (ecgFiltered.size < windowSamples) return null
        
        var bestWindow: IntRange? = null
        var bestResult: QualityResult? = null
        var bestKey: Pair<Int, Float>? = null  // (not ok, -score)
        
        var start = 0
        while (start + windowSamples <= ecgFiltered.size) {
            val end = start + windowSamples
            val window = ecgFiltered.sliceArray(start until end)
            
            val result = checkQuality(window)
            val key = Pair(if (result.isOk) 0 else 1, -result.score)
            
            // Comparar manualmente: primero por first, luego por second
            val isBetter = bestKey == null || 
                key.first < bestKey!!.first || 
                (key.first == bestKey!!.first && key.second < bestKey!!.second)
            
            if (isBetter) {
                bestKey = key
                bestWindow = start until end
                bestResult = result
            }
            
            start += stepSamples
        }
        
        return if (bestWindow != null && bestResult != null) {
            bestWindow to bestResult
        } else null
    }
    
    /**
     * Calcula amplitud pico a pico robusta (percentil 99 - percentil 1).
     */
    private fun p2pRobust(signal: FloatArray): Float {
        val sorted = signal.sortedArray()
        val idx1 = (sorted.size * 0.01).toInt()
        val idx99 = (sorted.size * 0.99).toInt().coerceAtMost(sorted.size - 1)
        return sorted[idx99] - sorted[idx1]
    }
    
    /**
     * Calcula ratio de potencia entre banda de ruido y banda de señal.
     * Usa método simple de Welch (estimación de PSD).
     */
    private fun bandpowerRatio(signal: FloatArray, fs: Float): Float {
        // Implementación simplificada de Welch
        val nfft = minOf(signal.size, (fs * 4).toInt())
        
        if (nfft < 8) return 0f
        
        val psd = computeSimplePSD(signal, nfft)
        val freqResolution = fs / nfft
        
        // Calcular potencia en banda de señal
        val signalStart = (config.signalBandLow / freqResolution).toInt()
        val signalEnd = (config.signalBandHigh / freqResolution).toInt().coerceAtMost(psd.size - 1)
        var signalPower = 0f
        for (i in signalStart..signalEnd) {
            signalPower += psd[i]
        }
        
        // Calcular potencia en banda de ruido
        val noiseStart = (config.noiseBandLow / freqResolution).toInt()
        val noiseEnd = (config.noiseBandHigh / freqResolution).toInt().coerceAtMost(psd.size - 1)
        var noisePower = 0f
        for (i in noiseStart..noiseEnd) {
            if (i < psd.size) noisePower += psd[i]
        }
        
        return noisePower / (signalPower + 1e-12f)
    }
    
    /**
     * Calcula PSD simple usando periodograma.
     */
    private fun computeSimplePSD(signal: FloatArray, nfft: Int): FloatArray {
        // Aplicar ventana Hanning
        val windowed = FloatArray(nfft)
        for (i in 0 until nfft) {
            val window = 0.5f * (1f - cos(2f * PI.toFloat() * i / (nfft - 1)))
            windowed[i] = signal[i % signal.size] * window
        }
        
        // FFT simple (solo magnitud, para N pequeño)
        val psd = FloatArray(nfft / 2)
        for (k in 0 until nfft / 2) {
            var real = 0f
            var imag = 0f
            for (n in 0 until nfft) {
                val angle = 2f * PI.toFloat() * k * n / nfft
                real += windowed[n] * cos(angle)
                imag -= windowed[n] * sin(angle)
            }
            psd[k] = (real * real + imag * imag) / nfft
        }
        
        return psd
    }
    
    /**
     * Calcula ratio de spikes (max absoluto / percentil 95).
     */
    private fun spikeRatio(signal: FloatArray): Float {
        val absSignal = signal.map { abs(it) }
        val amax = absSignal.maxOrNull() ?: 0f
        
        val sorted = absSignal.sorted()
        val p95idx = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
        val p95 = sorted[p95idx] + 1e-12f
        
        return amax / p95
    }
    
    private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
}
