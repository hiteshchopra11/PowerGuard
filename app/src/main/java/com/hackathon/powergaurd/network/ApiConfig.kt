package com.hackathon.powergaurd.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Configuration for the PowerGuard API. */
class ApiConfig @Inject constructor(private val mockApiService: MockApiService) {
    companion object {
        /** The base URL for the API. Change this to your actual backend URL. */
        private const val BASE_URL = "https://api.powergaurd.example.com/"

        /** Whether to use the mock API service. Set to false to use the real API service. */
        const val USE_MOCK_API = true

        /** Connection timeout in seconds. */
        private const val CONNECTION_TIMEOUT = 30L

        /** Read timeout in seconds. */
        private const val READ_TIMEOUT = 30L
    }

    /** Creates an instance of the API service. */
    fun createApiService(): ApiService {
        if (USE_MOCK_API) {
            return mockApiService
        }

        val loggingInterceptor =
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

        val client =
            OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .build()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        val retrofit =
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

        return retrofit.create(ApiService::class.java)
    }
}
