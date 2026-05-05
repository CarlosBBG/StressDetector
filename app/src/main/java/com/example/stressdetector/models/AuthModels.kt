package com.example.stressdetector.models

/**
 * Datos que se envian para iniciar sesion.
 */
data class LoginRequest(
    val cedula: String,
    val password: String
)

/**
 * Respuesta del servidor al iniciar sesion.
 */
data class LoginResponse(
    val token: String,
    val user_id: Int,
    val nombre: String,
    val apellido: String,
    val cedula: String,
    val fecha_nacimiento: String? = null
)

/**
 * Datos que se envian para crear una cuenta nueva.
 */
data class RegisterRequest(
    val cedula: String,
    val password: String,
    val nombre: String,
    val apellido: String,
    val fecha_nacimiento: String
)

/**
 * Respuesta del servidor cuando el registro fue exitoso.
 */
data class RegisterResponse(
    val message: String,
    val user_id: Int
)
