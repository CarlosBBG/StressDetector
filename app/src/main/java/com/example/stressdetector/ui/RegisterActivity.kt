package com.example.stressdetector.ui

import android.content.Intent
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stressdetector.api.ApiConfig
import com.example.stressdetector.api.ApiService
import com.example.stressdetector.auth.SessionManager
import com.example.stressdetector.databinding.ActivityRegisterBinding
import com.example.stressdetector.models.RegisterRequest
import com.example.stressdetector.models.parseError
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Pantalla de registro de usuario.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(this)
        apiService = ApiConfig.getApiService(this)

        setupInputFilters()
        setupDatePicker()

        binding.btnRegister.setOnClickListener { performRegister() }
        binding.txtLoginLink.setOnClickListener { finish() }
    }

    /**
     * Limita campos a letras y aplica mayuscula inicial.
     */
    private fun setupInputFilters() {
        // Filter: only letters (no spaces, numbers, or special chars)
        val lettersOnlyFilter = InputFilter { source, _, _, _, _, _ ->
            source.filter { it.isLetter() }
        }

        binding.etNombre.filters = arrayOf(lettersOnlyFilter)
        binding.etApellido.filters = arrayOf(lettersOnlyFilter)

        // Auto-capitalize first letter
        binding.etNombre.addAutoCapitalizeWatcher()
        binding.etApellido.addAutoCapitalizeWatcher()
    }

    /**
     * Configura el selector de fecha de nacimiento.
     */
    private fun setupDatePicker() {
        binding.etFechaNacimiento.apply {
            isFocusable = false
            isClickable = true
            isCursorVisible = false
            setOnClickListener { showDatePicker() }
        }
    }

    /**
     * Muestra un calendario para elegir la fecha.
     */
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formattedDate = String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    year,
                    month + 1,
                    dayOfMonth
                )
                binding.etFechaNacimiento.setText(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Prevent selecting future dates.
        datePickerDialog.datePicker.maxDate = calendar.timeInMillis
        datePickerDialog.show()
    }

    /**
     * Convierte la primera letra a mayuscula mientras el usuario escribe.
     */
    private fun EditText.addAutoCapitalizeWatcher() {
        addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating || s.isNullOrEmpty()) return

                val text = s.toString()
                val capitalized = text.replaceFirstChar { it.uppercase() }

                if (text != capitalized) {
                    isUpdating = true
                    setText(capitalized)
                    setSelection(capitalized.length)
                    isUpdating = false
                }
            }
        })
    }

    /**
     * Valida datos y llama a la API para crear la cuenta.
     */
    private fun performRegister() {
        val nombre = binding.etNombre.text.toString().trim()
        val apellido = binding.etApellido.text.toString().trim()
        val cedula = binding.etCedula.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val fechaNacimiento = binding.etFechaNacimiento.text.toString().trim()

        // Validate empty fields
        if (nombre.isEmpty() || apellido.isEmpty() || cedula.isEmpty() || password.isEmpty() || fechaNacimiento.isEmpty()) {
            showError("Completa todos los campos")
            return
        }

        // Validate nombre (min 2 letters)
        if (nombre.length < 2) {
            showError("El nombre debe tener al menos 2 letras")
            return
        }

        // Validate apellido (min 2 letters)
        if (apellido.length < 2) {
            showError("El apellido debe tener al menos 2 letras")
            return
        }

        // Validate cédula (exactly 10 digits)
        if (!isValidCedula(cedula)) {
            showError("La cédula debe tener 10 dígitos válidos")
            return
        }

        // Validate password
        if (password.length < 8) {
            showError("La contraseña debe tener al menos 8 caracteres")
            return
        }

        showLoading(true)
        hideError()

        lifecycleScope.launch {
            try {
                val response = apiService.register(
                    RegisterRequest(cedula, password, nombre, apellido, fechaNacimiento)
                )
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Registro exitoso. Inicia sesión.",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(this@RegisterActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                    finish()
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

    /**
     * Valida una cedula ecuatoriana con reglas basicas.
     */
    private fun isValidCedula(cedula: String): Boolean {
        // Must be exactly 10 digits
        if (cedula.length != 10 || !cedula.all { it.isDigit() }) {
            return false
        }

        // Validate province code (first 2 digits: 01-24)
        val province = cedula.substring(0, 2).toIntOrNull() ?: return false
        if (province < 1 || province > 24) {
            return false
        }

        // Validate third digit (0-5 for natural persons, 6 for public, 9 for legal)
        val thirdDigit = cedula[2].digitToIntOrNull() ?: return false
        if (thirdDigit > 6 && thirdDigit != 9) {
            return false
        }

        // Validate check digit (Módulo 10 algorithm for natural persons)
        if (thirdDigit < 6) {
            val coefficients = intArrayOf(2, 1, 2, 1, 2, 1, 2, 1, 2)
            var sum = 0
            for (i in 0 until 9) {
                var value = cedula[i].digitToInt() * coefficients[i]
                if (value >= 10) value -= 9
                sum += value
            }
            val checkDigit = (10 - (sum % 10)) % 10
            if (checkDigit != cedula[9].digitToInt()) {
                return false
            }
        }

        return true
    }

    /**
     * Muestra u oculta el indicador de carga.
     */
    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !loading
    }

    /**
     * Muestra un error claro en pantalla.
     */
    private fun showError(message: String) {
        binding.txtError.text = message
        binding.txtError.visibility = View.VISIBLE
    }

    /**
     * Oculta el mensaje de error.
     */
    private fun hideError() {
        binding.txtError.visibility = View.GONE
    }
}
