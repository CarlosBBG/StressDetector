package com.example.stressdetector.api

import com.example.stressdetector.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/measurements")
    suspend fun createMeasurement(@Body request: MeasurementRequest): Response<MeasurementResponse>

    @GET("api/measurements")
    suspend fun getMeasurements(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<MeasurementListResponse>

    @GET("api/measurements/{id}")
    suspend fun getMeasurement(@Path("id") id: Int): Response<MeasurementDetail>

    @DELETE("api/measurements/{id}")
    suspend fun deleteMeasurement(@Path("id") id: Int): Response<MessageResponse>
}
