package com.hackathon.powergaurd.network

import com.google.gson.GsonBuilder
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Singleton to manage Retrofit client for API communication. */
object RetrofitClient {

    private const val BASE_URL = "https://powerguardbackend.onrender.com/"
    private const val TIMEOUT_SECONDS = 60L

    private val gson = GsonBuilder().setLenient().create()

    private val loggingInterceptor =
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    private val okHttpClient =
            OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()

    private val retrofit =
            Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()

    /** Returns an instance of the PowerGuardApiService interface. */
    val apiService: PowerGuardApiService by lazy {
        retrofit.create(PowerGuardApiService::class.java)
    }
}
