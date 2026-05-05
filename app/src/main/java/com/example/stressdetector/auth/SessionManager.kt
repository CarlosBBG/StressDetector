package com.example.stressdetector.auth

import android.content.Context
import android.content.SharedPreferences

object SessionManager {

    private const val PREFS_NAME = "stress_detector_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NOMBRE = "nombre"
    private const val KEY_APELLIDO = "apellido"
    private const val KEY_CEDULA = "cedula"
    private const val KEY_FECHA_NACIMIENTO = "fecha_nacimiento"
    private const val KEY_OFFLINE_MODE = "offline_mode"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(
        token: String,
        userId: Int,
        nombre: String,
        apellido: String,
        cedula: String,
        fechaNacimiento: String? = null
    ) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_NOMBRE, nombre)
            .putString(KEY_APELLIDO, apellido)
            .putString(KEY_CEDULA, cedula)
            .putString(KEY_FECHA_NACIMIENTO, fechaNacimiento)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun isLoggedIn(): Boolean = getToken() != null

    fun getUserName(): String {
        val nombre = prefs.getString(KEY_NOMBRE, "") ?: ""
        val apellido = prefs.getString(KEY_APELLIDO, "") ?: ""
        return "$nombre $apellido".trim()
    }

    fun getCedula(): String {
        return prefs.getString(KEY_CEDULA, "") ?: ""
    }

    fun getFechaNacimiento(): String {
        return prefs.getString(KEY_FECHA_NACIMIENTO, "") ?: ""
    }

    fun setOfflineMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply()
    }

    fun isOfflineMode(): Boolean = prefs.getBoolean(KEY_OFFLINE_MODE, false)

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
