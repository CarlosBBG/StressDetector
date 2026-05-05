package com.example.stressdetector.models

import com.google.gson.Gson
import retrofit2.Response

/**
 * Estructura simple para errores devueltos por la API.
 */
data class ApiError(val error: String)

/**
 * Convierte un error HTTP en un texto claro para la UI.
 */
fun <T> Response<T>.parseError(): String {
    return try {
        val errorBody = this.errorBody()?.string()
        if (errorBody != null) {
            Gson().fromJson(errorBody, ApiError::class.java).error
        } else {
            "Error desconocido"
        }
    } catch (e: Exception) {
        "Error de conexión: ${e.message}"
    }
}

/**
 * Respuesta generica con un mensaje corto del servidor.
 */
data class MessageResponse(val message: String)
