package com.example.stressdetector.models

data class LoginRequest(val cedula: String, val password: String)

data class LoginResponse(
    val token: String,
    val user_id: Int,
    val nombre: String,
    val apellido: String,
    val cedula: String,
    val fecha_nacimiento: String? = null
)

data class RegisterRequest(
    val cedula: String,
    val password: String,
    val nombre: String,
    val apellido: String,
    val fecha_nacimiento: String
)

data class RegisterResponse(val message: String, val user_id: Int)
