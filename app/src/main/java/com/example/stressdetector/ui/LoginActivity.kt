package com.example.stressdetector.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stressdetector.MainActivity
import com.example.stressdetector.api.ApiConfig
import com.example.stressdetector.api.ApiService
import com.example.stressdetector.auth.SessionManager
import com.example.stressdetector.databinding.ActivityLoginBinding
import com.example.stressdetector.models.LoginRequest
import com.example.stressdetector.models.parseError
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(this)
        apiService = ApiConfig.getApiService(this)

        if (SessionManager.isLoggedIn() || SessionManager.isOfflineMode()) {
            navigateToMain()
            return
        }

        binding.btnLogin.setOnClickListener { performLogin() }
        binding.txtRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.txtOfflineMode.setOnClickListener {
            SessionManager.setOfflineMode(true)
            navigateToMain()
        }
    }

    private fun performLogin() {
        val cedula = binding.etCedula.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (cedula.isEmpty() || password.isEmpty()) {
            showError("Completa todos los campos")
            return
        }

        showLoading(true)
        hideError()

        lifecycleScope.launch {
            try {
                val response = apiService.login(LoginRequest(cedula, password))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    SessionManager.saveSession(
                        body.token,
                        body.user_id,
                        body.nombre,
                        body.apellido,
                        body.cedula,
                        body.fecha_nacimiento
                    )
                    navigateToMain()
                } else {
                    showError(response.parseError())
                }
            } catch (e: Exception) {
                showError("Error de conexión: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.txtError.text = message
        binding.txtError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.txtError.visibility = View.GONE
    }
}
