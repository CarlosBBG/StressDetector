package com.example.stressdetector.models

import com.google.gson.Gson
import retrofit2.Response

data class ApiError(val error: String)

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

data class MessageResponse(val message: String)
