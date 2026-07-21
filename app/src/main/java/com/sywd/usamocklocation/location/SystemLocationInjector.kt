package com.sywd.usamocklocation.location

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.sywd.usamocklocation.data.StateCapital

@Suppress("DEPRECATION")
class SystemLocationInjector(
    private val locationManager: LocationManager,
) : LocationInjector {
    private val activeProviders = linkedSetOf<String>()
    private val keepAliveListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override suspend fun start() {
        stop()

        val failures = mutableListOf<Throwable>()
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).forEach { provider ->
            runCatching {
                locationManager.addTestProvider(
                    provider,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE,
                )
                locationManager.setTestProviderEnabled(provider, true)
                // A registered client keeps the provider active while this location foreground
                // service is in the background. This is important on Samsung firmware, which can
                // otherwise freeze an apparently idle mock-provider process despite a WakeLock.
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    keepAliveListener,
                    Looper.getMainLooper(),
                )
                activeProviders += provider
            }.onFailure(failures::add)
        }

        if (activeProviders.isEmpty()) {
            throw MockLocationUnavailableException(
                "无法创建模拟位置 Provider。请在开发者选项中将本应用设为“模拟位置信息应用”。",
                failures.firstOrNull(),
            )
        }
    }

    override suspend fun inject(capital: StateCapital) {
        if (activeProviders.isEmpty()) {
            throw MockLocationUnavailableException("模拟位置 Provider 尚未启动。")
        }
        activeProviders.forEach { provider ->
            val location = Location(provider).apply {
                latitude = capital.latitude
                longitude = capital.longitude
                accuracy = 3f
                altitude = 0.0
                speed = 0f
                bearing = 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            locationManager.setTestProviderLocation(provider, location)
        }
    }

    override suspend fun stop() {
        runCatching { locationManager.removeUpdates(keepAliveListener) }
        val providersToRemove = activeProviders.toList()
        activeProviders.clear()
        providersToRemove.forEach { provider ->
            runCatching { locationManager.setTestProviderEnabled(provider, false) }
            runCatching { locationManager.removeTestProvider(provider) }
        }
    }
}

class MockLocationUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
