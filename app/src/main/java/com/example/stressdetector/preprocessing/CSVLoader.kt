package com.example.stressdetector.preprocessing

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class para representar los datos cargados del sensor MAX86150.
 */
data class SensorData(
    val timestamps: LongArray,  // Timestamps en milisegundos
    val bvp: FloatArray,        // Señal BVP (fotopletismografía)
    val ecg: FloatArray         // Señal ECG
) {
    val size: Int get() = timestamps.size
    
    fun isEmpty(): Boolean = timestamps.isEmpty()
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SensorData
        return timestamps.contentEquals(other.timestamps) &&
               bvp.contentEquals(other.bvp) &&
               ecg.contentEquals(other.ecg)
    }
    
    override fun hashCode(): Int {
        var result = timestamps.contentHashCode()
        result = 31 * result + bvp.contentHashCode()
        result = 31 * result + ecg.contentHashCode()
        return result
    }
}

/**
 * Cargador de archivos CSV del sensor MAX86150.
 * Soporta formato con coma decimal (europeo) y punto decimal (estándar).
 */
class CSVLoader(private val context: Context) {
    
    companion object {
        private const val TAG = "CSVLoader"
        
        // Formatos de fecha soportados
        private val DATE_FORMATS = arrayOf(
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss.SSSSSS",
            "yyyy/MM/dd HH:mm:ss.SSS",
            "dd/MM/yyyy HH:mm:ss.SSS"
        )
    }
    
    /**
     * Carga un archivo CSV desde un URI (seleccionado por file picker).
     */
    fun loadFromUri(uri: Uri): SensorData {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se puede abrir el archivo: $uri")
        
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        return parseCSV(reader)
    }
    
    /**
     * Carga un archivo CSV desde assets (para testing).
     */
    fun loadFromAssets(fileName: String): SensorData {
        val inputStream = context.assets.open(fileName)
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        return parseCSV(reader)
    }
    
    private fun parseCSV(reader: BufferedReader): SensorData {
        val timestamps = mutableListOf<Long>()
        val bvpValues = mutableListOf<Float>()
        val ecgValues = mutableListOf<Float>()
        
        var lineNumber = 0
        var dateFormatter: SimpleDateFormat? = null
        
        reader.use { br ->
            br.lineSequence().forEach { line ->
                lineNumber++
                
                // Saltar líneas vacías
                if (line.isBlank()) return@forEach
                
                val trimmedLine = line.trim()
                
                // Saltar cabeceras
                if (trimmedLine.lowercase().startsWith("time") || 
                    trimmedLine.lowercase().startsWith("timestamp")) {
                    return@forEach
                }
                
                try {
                    val parsed = parseLine(trimmedLine, dateFormatter)
                    if (parsed != null) {
                        val (timestamp, bvp, ecg, formatter) = parsed
                        timestamps.add(timestamp)
                        bvpValues.add(bvp)
                        ecgValues.add(ecg)
                        dateFormatter = formatter
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Error parseando línea $lineNumber: ${e.message}")
                }
            }
        }
        
        android.util.Log.d(TAG, "CSV cargado: ${timestamps.size} muestras")
        
        return SensorData(
            timestamps = timestamps.toLongArray(),
            bvp = bvpValues.toFloatArray(),
            ecg = ecgValues.toFloatArray()
        )
    }
    
    /**
     * Parsea una línea del CSV.
     * Soporta formatos:
     * - time,bvp,ecg (punto decimal)
     * - time,bvp_int,bvp_dec,ecg_int,ecg_dec (coma decimal splitteada)
     */
    private fun parseLine(
        line: String, 
        cachedFormatter: SimpleDateFormat?
    ): ParsedLine? {
        val parts = line.split(",").map { it.trim() }
        
        if (parts.size < 3) return null
        
        val timestamp: Long
        val bvp: Float
        val ecg: Float
        var formatter = cachedFormatter
        
        if (parts.size >= 5) {
            // Formato coma decimal: time, bvp_int, bvp_dec, ecg_int, ecg_dec
            val timeStr = parts[0]
            timestamp = parseTimestamp(timeStr, formatter).also { 
                if (formatter == null) formatter = findDateFormatter(timeStr)
            }
            
            bvp = "${parts[1]}.${parts[2]}".toFloatOrNull() ?: return null
            ecg = "${parts[3]}.${parts[4]}".toFloatOrNull() ?: return null
        } else if (parts.size == 3) {
            // Formato punto decimal: time, bvp, ecg
            val timeStr = parts[0]
            timestamp = parseTimestamp(timeStr, formatter).also {
                if (formatter == null) formatter = findDateFormatter(timeStr)
            }
            
            bvp = parts[1].toFloatOrNull() ?: return null
            ecg = parts[2].toFloatOrNull() ?: return null
        } else {
            return null
        }
        
        return ParsedLine(timestamp, bvp, ecg, formatter)
    }
    
    private fun parseTimestamp(timeStr: String, formatter: SimpleDateFormat?): Long {
        // Si tenemos un formatter cacheado, usarlo
        if (formatter != null) {
            return try {
                formatter.parse(timeStr)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }
        
        // Probar con los diferentes formatos
        for (format in DATE_FORMATS) {
            try {
                val df = SimpleDateFormat(format, Locale.US)
                val date = df.parse(timeStr)
                if (date != null) return date.time
            } catch (e: Exception) {
                continue
            }
        }
        
        // Fallback: intentar parsear como timestamp numérico
        return timeStr.toLongOrNull() ?: System.currentTimeMillis()
    }
    
    private fun findDateFormatter(timeStr: String): SimpleDateFormat? {
        for (format in DATE_FORMATS) {
            try {
                val df = SimpleDateFormat(format, Locale.US)
                df.parse(timeStr)
                return df
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }
    
    private data class ParsedLine(
        val timestamp: Long,
        val bvp: Float,
        val ecg: Float,
        val formatter: SimpleDateFormat?
    )
}
