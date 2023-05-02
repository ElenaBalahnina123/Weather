package com.example.weather

import android.annotation.SuppressLint
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface WeatherRepository {
    suspend fun getWeather(
        lat: Float,
        lon: Float
    ): WeatherResponse

    suspend fun getLocation() : LatLng?
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WeatherRepoModule(weatherApi: WeatherApi) {

    @Binds
    abstract fun weatherRepoBinds(weatherRepositoryImpl: WeatherRepositoryImpl): WeatherRepository
}

class WeatherRepositoryImpl @Inject constructor(
    private val weatherApi: WeatherApi,
    private val locationProviderClient: FusedLocationProviderClient,
) : WeatherRepository {

    override suspend fun getWeather(
        lat: Float,
        lon: Float
    ): WeatherResponse {
        return weatherApi.getWeather(
            apiKey = "ce95491a05db40e1b92121744232004",
            query = "$lat,$lon",
            aqi = "no",
            days = 7
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLocation(): LatLng? {
        return suspendCoroutine<LatLng?> { continuation ->
            locationProviderClient.lastLocation
                .addOnSuccessListener {
                    continuation.resume(LatLng(it.latitude,it.longitude))
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
    }
}
