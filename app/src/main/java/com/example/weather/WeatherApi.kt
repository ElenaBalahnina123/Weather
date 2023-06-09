package com.example.weather

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Singleton

interface WeatherApi {

    @GET("v1/forecast.json")
    suspend fun getWeather(
        @Query("key") apiKey: String,
        @Query("q", encoded = true) query: String,
        @Query("aqi") aqi: String,
        @Query("days") days: Int,
        @Query("alerts") alerts: String
    ): WeatherResponse
}


@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(Json {
                ignoreUnknownKeys = true
            }.asConverterFactory("application/json".toMediaType()))
            .baseUrl("https://api.weatherapi.com/")
            .build()
    }

    @Provides
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofitService(retrofit: Retrofit): WeatherApi {
        return retrofit.create()
    }

}


@Serializable
data class WeatherResponse(
    val location: RspLocation,
    @SerialName("current")
    val currentWeather: RspWeather,
    @SerialName("forecast")
    val forecastWeather: RspForecast
)

@Serializable
data class RspLocation(
    val name: String,
    val lat: Float,
    val lon: Float,
    val localtime: String
)

@Serializable
data class RspWeather(
    @SerialName("temp_c")
    val tempC: Float,
    val condition: Condition,
    @SerialName("feelslike_c")
    val feelsLikeC: Float
)

@Serializable
data class RspForecast(
    val forecastday: List<ForecastDay>
)

@Serializable
data class ForecastDay(
    val day: Day,
    val astro: Astro,
    val date: String
)

@Serializable
data class Day(
    val maxtemp_c: Float,
    val mintemp_c: Float,
    val avgvis_km: Float
)

@Serializable
data class Astro(
    val sunrise: String,
    val sunset: String
)

@Serializable
data class Condition(
    val text: String,
    val icon: String
)