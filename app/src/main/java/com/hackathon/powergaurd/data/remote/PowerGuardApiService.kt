package com.hackathon.powergaurd.data.remote

import android.util.Log
import com.google.gson.Gson
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import okhttp3.Interceptor
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

class LoggingInterceptor : Interceptor {
    private val gson = Gson()
    
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        
        // Log request
        request.body?.let {
            val buffer = okio.Buffer()
            it.writeTo(buffer)
            val requestBody = buffer.readUtf8()
            try {
                val prettyJson = JSONObject(requestBody).toString(2)
                Log.d("Hitesh", "Request Body:\n$prettyJson")
            } catch (e: Exception) {
                Log.d("Hitesh", "Request Body: $requestBody")
            }
        }
        
        // Proceed with the request
        val response = chain.proceed(request)
        
        // Log response
        response.body?.let {
            val responseBody = it.string()
            try {
                val prettyJson = JSONObject(responseBody).toString(2)
                Log.d("Hitesh", "Response Body:\n$prettyJson")
            } catch (e: Exception) {
                Log.d("Hitesh", "Response Body: $responseBody")
            }
            
            // We need to create a new response since we consumed the original response body
            return response.newBuilder()
                .body(responseBody.toResponseBody(it.contentType()))
                .build()
        }
        
        return response
    }
}

/** API interface for PowerGuard backend */
interface PowerGuardApiService {
    
    /**
     * Analyzes device data and returns optimization recommendations
     *
     * @param deviceData Device data to analyze
     * @return Response containing analysis results
     */
    @POST("/api/analyze")
    suspend fun analyzeDeviceData(@Body deviceData: DeviceData): Response<AnalysisResponse>
}