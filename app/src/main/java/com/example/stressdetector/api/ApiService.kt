package com.example.stressdetector.api

import com.example.stressdetector.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Endpoints disponibles en el servidor.
 */
interface ApiService {

    // Registro de usuario.
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    // Inicio de sesion.
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // Guardar una medicion.
    @POST("api/measurements")
    suspend fun createMeasurement(@Body request: MeasurementRequest): Response<MeasurementResponse>

    // Listar mediciones con paginacion.
    @GET("api/measurements")
    suspend fun getMeasurements(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<MeasurementListResponse>

    // Detalle de una medicion.
    @GET("api/measurements/{id}")
    suspend fun getMeasurement(@Path("id") id: Int): Response<MeasurementDetail>

    // Eliminar una medicion.
    @DELETE("api/measurements/{id}")
    suspend fun deleteMeasurement(@Path("id") id: Int): Response<MessageResponse>
}
