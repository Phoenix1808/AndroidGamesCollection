package com.example.uploadingscreen.network


import com.example.uploadingscreen.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://creworcrook.onrender.com/"

    // BODY logging prints passwords and tokens to logcat, so only in debug builds, and never the token
    private val login = HttpLoggingInterceptor().apply{
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            // 401 on a request that carried our token = token expired/invalid (a wrong password on login has no token)
            if (response.code == 401 && request.header("Authorization") != null) {
                SessionManager.onTokenRejected()
            }
            response
        }
        .addInterceptor(login)
        .build()
    val api: ApiService by lazy{
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
