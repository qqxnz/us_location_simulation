package com.sywd.usamocklocation.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.SystemClock
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.sywd.usamocklocation.data.StateCapital
import kotlinx.coroutines.tasks.await

/** Directly controls Google Play services FLP, which is used by Chrome and many apps. */
class FusedLocationInjector(context: Context) : LocationInjector {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var mockModeEnabled = false
    private var pendingLocationTask: Task<Void>? = null

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        stop()
        client.setMockMode(true).await()
        mockModeEnabled = true
    }

    @SuppressLint("MissingPermission")
    override suspend fun inject(capital: StateCapital) {
        if (!mockModeEnabled) {
            throw MockLocationUnavailableException("Google 融合定位模拟模式尚未启动。")
        }
        val task = pendingLocationTask
            ?.takeUnless(Task<Void>::isComplete)
            ?: client.setMockLocation(capital.toLocation()).also { pendingLocationTask = it }
        try {
            task.await()
        } finally {
            // Task.await() cancellation does not cancel the Google Task. Forget it after a
            // coroutine timeout so the next heartbeat can submit a fresh location instead of
            // waiting on the same permanently stuck Binder call.
            if (pendingLocationTask === task) pendingLocationTask = null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stop() {
        if (!mockModeEnabled) return
        try {
            client.setMockMode(false).await()
        } finally {
            mockModeEnabled = false
            pendingLocationTask = null
        }
    }

    private fun StateCapital.toLocation() = Location(FUSED_PROVIDER).apply {
        latitude = this@toLocation.latitude
        longitude = this@toLocation.longitude
        accuracy = 3f
        altitude = 0.0
        speed = 0f
        bearing = 0f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    private companion object {
        const val FUSED_PROVIDER = "fused"
    }
}
