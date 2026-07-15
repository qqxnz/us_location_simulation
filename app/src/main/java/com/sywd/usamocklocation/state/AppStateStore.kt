package com.sywd.usamocklocation.state

import android.content.Context
import com.sywd.usamocklocation.data.StateCapitals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MockLocationState(
    val selectedCode: String,
    val activeCode: String? = null,
    val errorMessage: String? = null,
) {
    val isRunning: Boolean get() = activeCode != null
}

class AppStateStore private constructor(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<MockLocationState> = _state.asStateFlow()

    @Synchronized
    fun select(code: String) {
        if (StateCapitals.find(code) == null) return
        preferences.edit().putString(KEY_SELECTED_CODE, code).apply()
        _state.value = _state.value.copy(selectedCode = code, errorMessage = null)
    }

    @Synchronized
    fun markRunning(code: String) {
        preferences.edit()
            .putString(KEY_SELECTED_CODE, code)
            .putString(KEY_ACTIVE_CODE, code)
            .remove(KEY_ERROR)
            .commit()
        _state.value = MockLocationState(selectedCode = code, activeCode = code)
    }

    @Synchronized
    fun markStopped(errorMessage: String? = null) {
        val editor = preferences.edit().remove(KEY_ACTIVE_CODE)
        if (errorMessage == null) editor.remove(KEY_ERROR) else editor.putString(KEY_ERROR, errorMessage)
        editor.commit()
        _state.value = _state.value.copy(activeCode = null, errorMessage = errorMessage)
    }

    @Synchronized
    fun clearError() {
        preferences.edit().remove(KEY_ERROR).apply()
        _state.value = _state.value.copy(errorMessage = null)
    }

    @Synchronized
    fun setError(message: String) {
        preferences.edit().putString(KEY_ERROR, message).apply()
        _state.value = _state.value.copy(errorMessage = message)
    }

    private fun readState(): MockLocationState {
        val selectedCode = preferences.getString(KEY_SELECTED_CODE, DEFAULT_STATE_CODE)
            ?.takeIf { StateCapitals.find(it) != null }
            ?: DEFAULT_STATE_CODE
        val activeCode = preferences.getString(KEY_ACTIVE_CODE, null)
            ?.takeIf { StateCapitals.find(it) != null }
        return MockLocationState(
            selectedCode = selectedCode,
            activeCode = activeCode,
            errorMessage = preferences.getString(KEY_ERROR, null),
        )
    }

    companion object {
        private const val PREFS_NAME = "mock_location_state"
        private const val KEY_SELECTED_CODE = "selected_code"
        private const val KEY_ACTIVE_CODE = "active_code"
        private const val KEY_ERROR = "error"
        private const val DEFAULT_STATE_CODE = "CA"

        @Volatile
        private var instance: AppStateStore? = null

        fun get(context: Context): AppStateStore = instance ?: synchronized(this) {
            instance ?: AppStateStore(context.applicationContext).also { instance = it }
        }
    }
}
