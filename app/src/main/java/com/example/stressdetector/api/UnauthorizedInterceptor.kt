package com.example.stressdetector.api

import android.content.Context
import android.content.Intent
import com.example.stressdetector.auth.SessionManager
import com.example.stressdetector.ui.LoginActivity
import okhttp3.Interceptor
import okhttp3.Response

class UnauthorizedInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // Don't intercept 401 on auth endpoints — those mean wrong credentials, not expired token
        val path = request.url.encodedPath
        if (response.code == 401 && !path.contains("/auth/")) {
            SessionManager.clearSession()
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
        return response
    }
}
