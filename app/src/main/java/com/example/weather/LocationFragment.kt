package com.example.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.example.weather.databinding.LocationFragmentBinding
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class LocationFragment : Fragment(R.layout.location_fragment) {

    private val viewModel by viewModels<WeatherViewModel>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onRequestPermissionResult
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.eventFlow()
            .onEach { onEvent(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        LocationFragmentBinding.bind(view).run {
            viewModel.uiFlow()
                .onEach { renderUiState(it) }
                .launchIn(viewLifecycleOwner.lifecycleScope)

            btnLocation.setOnClickListener {
                viewModel.getLocation()
            }
        }
    }

    private fun LocationFragmentBinding.renderUiState(uiState: WeatherUiState) {
        name.text = uiState.name
        tempC.text = uiState.tempC.toString()
        feelsLikeC.text = uiState.feelsLikeC.toString()
        localtime.text = uiState.localtime

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.RUSSIAN)
            .build()

        val translator = Translation.getClient(options)
        lifecycle.addObserver(translator)
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(uiState.text_condition)
                    .addOnSuccessListener {
                        textCondition.text = it
                        when (textCondition.text) {
                            "Частично облачно" -> {
                                weatherBackground.setImageResource(R.drawable.fluffy_clouds)
                            }
                            "Облачно" -> {
                                weatherBackground.setImageResource(R.drawable.clouds)
                            }
                            "Умеренный дождь" -> {
                                weatherBackground.setImageResource(R.drawable.rain_autumn)
                            }
                            "Солнечный" -> {
                                weatherBackground.setImageResource(R.drawable.sunny)
                            }
                            "Небольшой дождь моросит" -> {
                                weatherBackground.setImageResource(R.drawable.light_rain)
                            }
                            else -> {
                                weatherBackground.setImageResource(R.drawable.white_cloud)
                            }
                        }
                    }
                    .addOnFailureListener {
                        //Error
                        Log.e("err", "Error: " + it.localizedMessage)
                    }
            }
            .addOnFailureListener {
                Log.e("err", "Download Error: " + it.localizedMessage)

            }

        Glide.with(icon)
            .load("https:${uiState.iconURL}")
            .into(icon)
        Log.d("IconURL", "https:${uiState.iconURL}")
    }

    private fun onEvent(event: WeatherVMEvent) {
        when (event) {
            WeatherVMEvent.RequestLocationPermission -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            WeatherVMEvent.RequestEnableLocation -> {
                Toast.makeText(requireContext(), "Включи геопозицию", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onRequestPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.onLocationPermissionGranted()
        }
    }
}


sealed class WeatherVMEvent {
    object RequestLocationPermission : WeatherVMEvent()
    object RequestEnableLocation : WeatherVMEvent()
}

data class WeatherUiState(
    val name: String = "",
    val tempC: Float? = 0.0F,
    val text_condition: String = "",
    val feelsLikeC: Float = 0.0F,
    val localtime: String = "",
    val iconURL: String = ""
)

data class WeatherVmState(
    val isLocationEnabled: Boolean = false,
    val isLocationPermissionGranted: Boolean = false,
    val latLng: LatLng? = null,
)

@HiltViewModel
class WeatherViewModel @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val repository: WeatherRepository,
    private val geocoder: Geocoder,
) : ViewModel() {

    private val mutableStateFlow = MutableStateFlow(WeatherVmState())

    private val mutableEventFlow = MutableSharedFlow<WeatherVMEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun eventFlow() = mutableEventFlow.asSharedFlow()

    fun uiFlow() = mutableStateFlow.map { createUiState(it) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    private suspend fun createUiState(vmState: WeatherVmState): WeatherUiState =
        withContext(Dispatchers.IO) {
            val getCityTask = async {
                vmState.latLng?.let { getCity(it) } ?: "---"
            }

            val weatherTask = async {
                vmState.latLng?.let {
                    kotlin.runCatching {
                        repository.getWeather(it.latitude.toFloat(), it.longitude.toFloat())
                    }.onFailure {
                        Log.e("WeatherVM", "cannot get weather", it)
                    }.getOrNull()
                }
            }

            val weather = weatherTask.await()

            WeatherUiState(
                name = getCityTask.await(),
                tempC = weather?.currentWeather?.tempC ?: 0.0F ,
                text_condition = weather?.currentWeather?.condition?.text ?: "",
                feelsLikeC = weather?.currentWeather?.feelsLikeC ?: 0.0F,
                localtime = weather?.location?.localtime ?: "",
                iconURL = weather?.currentWeather?.condition?.icon ?: "-"
            )

        }

    private suspend fun getCity(latLng: LatLng): String? = withContext(Dispatchers.IO) {
        geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            ?.firstOrNull()
            ?.locality
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService<LocationManager>() ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getLocation() {
        if (hasLocationPermission()) {
            onLocationPermissionGranted()
        } else {
            mutableEventFlow.tryEmit(WeatherVMEvent.RequestLocationPermission)
        }
    }


    fun onLocationPermissionGranted() {
        if (isLocationEnabled()) {
            onLocationEnabled()
        } else {
            mutableEventFlow.tryEmit(WeatherVMEvent.RequestEnableLocation)
        }
    }

    private fun onLocationEnabled() {
        viewModelScope.launch {
            repository.getLocation()?.let {
                mutableStateFlow.value = mutableStateFlow.value.copy(
                    latLng = it
                )
            }
        }
    }
}


data class LatLng(
    val latitude: Double,
    val longitude: Double,
)





