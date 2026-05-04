package com.virtualtryon.app.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Service class for DashScope API calls
 */
object ApiService {
    private const val BASE_URL = "https://dashscope-intl.aliyuncs.com/api/v1/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: DashScopeApi = retrofit.create(DashScopeApi::class.java)

    /**
     * Creates authorization header value
     */
    fun getAuthHeader(apiKey: String): String {
        return "Bearer $apiKey"
    }
}
