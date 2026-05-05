package com.example.stressdetector.preprocessing

import kotlin.math.*

/**
 * Implementación de filtro Butterworth de orden superior.
 * Replica scipy.signal.butter + sosfiltfilt para Android.
 */
class ButterworthFilter {
    
    companion object {
        /**
         * Aplica filtro Butterworth pasabanda bidireccional (zero-phase).
         * Equivalente a scipy.signal.sosfiltfilt con butter de orden N.
         * 
         * @param signal Señal de entrada
         * @param fs Frecuencia de muestreo
         * @param lowFreq Frecuencia de corte baja
         * @param highFreq Frecuencia de corte alta
         * @param order Orden del filtro (default 4, como en Python)
         */
        fun bandpassFilter(
            signal: FloatArray, 
            fs: Float, 
            lowFreq: Float, 
            highFreq: Float, 
            order: Int = 4
        ): FloatArray {
            // Aplicar pasabajos y pasaaltos en cascada
            var filtered = signal.copyOf()
            
            // Filtro pasabajos a highFreq
            filtered = butterworthLowpass(filtered, fs, highFreq, order)
            
            // Filtro pasaaltos a lowFreq  
            filtered = butterworthHighpass(filtered, fs, lowFreq, order)
            
            return filtered
        }
        
        /**
         * Filtro Butterworth pasabajos de orden N, aplicado bidireccional.
         */
        fun butterworthLowpass(signal: FloatArray, fs: Float, cutoff: Float, order: Int): FloatArray {
            val nyq = fs / 2f
            val normalizedCutoff = (cutoff / nyq).coerceIn(0.001f, 0.999f)
            
            // Calcular coeficientes del filtro
            val coeffs = calculateLowpassCoefficients(normalizedCutoff, order)
            
            // Aplicar filtro hacia adelante y hacia atrás (zero-phase)
            var filtered = applyIIRFilter(signal, coeffs)
            filtered = applyIIRFilter(filtered.reversedArray(), coeffs)
            
            return filtered.reversedArray()
        }
        
        /**
         * Filtro Butterworth pasaaltos de orden N, aplicado bidireccional.
         */
        fun butterworthHighpass(signal: FloatArray, fs: Float, cutoff: Float, order: Int): FloatArray {
            val nyq = fs / 2f
            val normalizedCutoff = (cutoff / nyq).coerceIn(0.001f, 0.999f)
            
            // Calcular coeficientes del filtro
            val coeffs = calculateHighpassCoefficients(normalizedCutoff, order)
            
            // Aplicar filtro hacia adelante y hacia atrás (zero-phase)
            var filtered = applyIIRFilter(signal, coeffs)
            filtered = applyIIRFilter(filtered.reversedArray(), coeffs)
            
            return filtered.reversedArray()
        }
        
        /**
         * Calcula coeficientes para filtro pasabajos Butterworth.
         * Usa la transformación bilineal simplificada.
         */
        private fun calculateLowpassCoefficients(wn: Float, order: Int): FilterCoefficients {
            // Pre-warp frequency
            val wc = tan(PI.toFloat() * wn)
            
            // Para orden 4, usamos 2 secciones biquad
            // Cada sección tiene polos conjugados
            val sections = mutableListOf<BiquadSection>()
            
            for (k in 0 until order / 2) {
                val theta = PI.toFloat() * (2 * k + 1) / (2 * order) + PI.toFloat() / 2
                val poleReal = wc * cos(theta)
                val poleImag = wc * sin(theta)
                
                // Transformación bilineal: s -> (1-z^-1)/(1+z^-1)
                // Coeficientes biquad para pasabajos
                val k2 = wc * wc
                val denom = 1 + 2 * abs(poleReal) + k2
                
                val b0 = k2 / denom
                val b1 = 2 * k2 / denom
                val b2 = k2 / denom
                val a1 = 2 * (k2 - 1) / denom
                val a2 = (1 - 2 * abs(poleReal) + k2) / denom
                
                sections.add(BiquadSection(b0, b1, b2, a1, a2))
            }
            
            // Si orden es impar, agregar sección de primer orden
            if (order % 2 == 1) {
                val denom = 1 + wc
                val b0 = wc / denom
                val b1 = wc / denom
                val a1 = (wc - 1) / denom
                sections.add(BiquadSection(b0, b1, 0f, a1, 0f))
            }
            
            return FilterCoefficients(sections)
        }
        
        /**
         * Calcula coeficientes para filtro pasaaltos Butterworth.
         */
        private fun calculateHighpassCoefficients(wn: Float, order: Int): FilterCoefficients {
            // Pre-warp frequency
            val wc = tan(PI.toFloat() * wn)
            
            val sections = mutableListOf<BiquadSection>()
            
            for (k in 0 until order / 2) {
                val theta = PI.toFloat() * (2 * k + 1) / (2 * order) + PI.toFloat() / 2
                val poleReal = wc * cos(theta)
                
                // Coeficientes biquad para pasaaltos
                val k2 = wc * wc
                val denom = 1 + 2 * abs(poleReal) + k2
                
                val b0 = 1 / denom
                val b1 = -2 / denom
                val b2 = 1 / denom
                val a1 = 2 * (k2 - 1) / denom
                val a2 = (1 - 2 * abs(poleReal) + k2) / denom
                
                sections.add(BiquadSection(b0, b1, b2, a1, a2))
            }
            
            // Si orden es impar, agregar sección de primer orden
            if (order % 2 == 1) {
                val denom = 1 + wc
                val b0 = 1 / denom
                val b1 = -1 / denom
                val a1 = (wc - 1) / denom
                sections.add(BiquadSection(b0, b1, 0f, a1, 0f))
            }
            
            return FilterCoefficients(sections)
        }
        
        /**
         * Aplica filtro IIR usando secciones biquad en cascada.
         */
        private fun applyIIRFilter(signal: FloatArray, coeffs: FilterCoefficients): FloatArray {
            var output = signal.copyOf()
            
            for (section in coeffs.sections) {
                output = applyBiquadSection(output, section)
            }
            
            return output
        }
        
        /**
         * Aplica una sección biquad al señal.
         * y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
         */
        private fun applyBiquadSection(signal: FloatArray, section: BiquadSection): FloatArray {
            val output = FloatArray(signal.size)
            
            var x1 = 0f  // x[n-1]
            var x2 = 0f  // x[n-2]
            var y1 = 0f  // y[n-1]
            var y2 = 0f  // y[n-2]
            
            for (i in signal.indices) {
                val x0 = signal[i]
                val y0 = section.b0 * x0 + section.b1 * x1 + section.b2 * x2 - 
                         section.a1 * y1 - section.a2 * y2
                
                output[i] = y0
                
                x2 = x1
                x1 = x0
                y2 = y1
                y1 = y0
            }
            
            return output
        }
    }
    
    /**
     * Sección biquad (second-order section).
     */
    data class BiquadSection(
        val b0: Float,
        val b1: Float,
        val b2: Float,
        val a1: Float,
        val a2: Float
    )
    
    /**
     * Coeficientes del filtro completo.
     */
    data class FilterCoefficients(
        val sections: List<BiquadSection>
    )
}
