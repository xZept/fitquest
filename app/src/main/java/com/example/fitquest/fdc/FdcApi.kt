package com.example.fitquest.fdc

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object FdcApi {
    private const val BASE_URL = "https://api.nal.usda.gov/fdc/"

    fun create(apiKeyProvider: () -> String): FdcService {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("X-Api-Key", apiKeyProvider())
                    .build()
                val resp = chain.proceed(req)
                Log.d("FDC", "X-RateLimit-Remaining=${resp.header("X-RateLimit-Remaining")}")
                resp
            }
            .addInterceptor(logger)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FdcService::class.java)
    }
}
