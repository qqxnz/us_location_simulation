package com.sywd.usamocklocation.ui

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.sywd.usamocklocation.location.MockLocationService
import com.sywd.usamocklocation.state.AppStateStore
import com.sywd.usamocklocation.state.MockLocationState
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val stateStore = AppStateStore.get(application)
    val state: StateFlow<MockLocationState> = stateStore.state
    private var pendingStartCode: String? = null

    fun select(code: String) = stateStore.select(code)

    fun startMocking(code: String) {
        stateStore.clearError()
        ContextCompat.startForegroundService(
            getApplication(),
            MockLocationService.startIntent(getApplication(), code),
        )
    }

    fun stopMocking() {
        getApplication<Application>().startService(
            MockLocationService.stopIntent(getApplication()),
        )
    }

    fun showError(message: String) = stateStore.setError(message)

    fun clearError() = stateStore.clearError()

    fun setPendingStart(code: String) {
        pendingStartCode = code
    }

    fun consumePendingStart(): String? = pendingStartCode.also { pendingStartCode = null }
}
