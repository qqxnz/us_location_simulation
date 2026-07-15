package com.sywd.usamocklocation.location

import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.SystemClock
import com.sywd.usamocklocation.data.StateCapital

@Suppress("DEPRECATION")
class SystemLocationInjector(
    private val locationManager: LocationManager,
) : LocationInjector {
    private val activeProviders = linkedSetOf<String>()

    @Synchronized
    override fun start() {
        stop()

        val failures = mutableListOf<Throwable>()
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            FUSED_PROVIDER,
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

    @Synchronized
    override fun inject(capital: StateCapital) {
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

    @Synchronized
    override fun stop() {
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

private const val FUSED_PROVIDER = "fused"
