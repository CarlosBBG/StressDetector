package com.example.stressdetector

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encapsula la carga del modelo y una prediccion simple.
 */
class TFLiteRunner(private val context: Context) {

    private var interpreter: Interpreter? = null

    /**
     * Carga el modelo TFLite desde assets.
     */
    fun loadModelFromAssets(modelName: String = "stress_detector.tflite") {
        val modelBuffer = context.assets.open(modelName).readBytes()
        val byteBuffer = ByteBuffer.allocateDirect(modelBuffer.size)
            .order(ByteOrder.nativeOrder())
        byteBuffer.put(modelBuffer)
        byteBuffer.rewind()

        interpreter = Interpreter(byteBuffer)
        Log.d("TFLITE_TEST", "Modelo cargado OK: $modelName")
        
        // Inspeccionar shapes del modelo
        logModelInfo()
    }
    
    /**
     * Imprime información sobre los tensores de entrada/salida del modelo.
     */
    /**
     * Muestra en logs el tamano de entrada y salida del modelo.
     */
    fun logModelInfo() {
        val tflite = interpreter ?: return
        
        val inputCount = tflite.inputTensorCount
        val outputCount = tflite.outputTensorCount
        
        Log.d("TFLITE_INFO", "=== INFORMACIÓN DEL MODELO ===")
        Log.d("TFLITE_INFO", "Tensores de entrada: $inputCount")
        
        for (i in 0 until inputCount) {
            val tensor = tflite.getInputTensor(i)
            Log.d("TFLITE_INFO", "  Input[$i]: shape=${tensor.shape().contentToString()}, dtype=${tensor.dataType()}")
        }
        
        Log.d("TFLITE_INFO", "Tensores de salida: $outputCount")
        for (i in 0 until outputCount) {
            val tensor = tflite.getOutputTensor(i)
            Log.d("TFLITE_INFO", "  Output[$i]: shape=${tensor.shape().contentToString()}, dtype=${tensor.dataType()}")
        }
    }
    
    /**
     * Obtiene el shape de entrada esperado por el modelo.
     */
    /**
     * Devuelve el formato de entrada esperado por el modelo.
     */
    fun getInputShape(): IntArray {
        val tflite = interpreter ?: return intArrayOf()
        return tflite.getInputTensor(0).shape()
    }

    /**
     * input: FloatArray con tamaño = 4000*2 (ECG,BVP)
     * output: probabilidad [0..1]
     */
    /**
     * Ejecuta una prediccion y devuelve la probabilidad de estres.
     */
    fun predict(input: FloatArray): Float {
        val tflite = interpreter ?: error("Modelo no cargado. Llama loadModelFromAssets()")
        
        // Obtener shape esperado del modelo
        val inputShape = tflite.getInputTensor(0).shape()
        Log.d("TFLITE_PREDICT", "Input shape esperado: ${inputShape.contentToString()}")
        Log.d("TFLITE_PREDICT", "Input proporcionado: ${input.size} floats")

        // Input shape esperado: [1, 4000, 2]
        val inputBuffer = ByteBuffer.allocateDirect(4 * 1 * 4000 * 2)
            .order(ByteOrder.nativeOrder())

        for (v in input) inputBuffer.putFloat(v)
        inputBuffer.rewind()

        val output = Array(1) { FloatArray(1) }
        tflite.run(inputBuffer, output)
        
        Log.d("TFLITE_PREDICT", "Output: ${output[0][0]}")

        return output[0][0]
    }

    /**
     * Libera memoria del interprete cuando ya no se usa.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}