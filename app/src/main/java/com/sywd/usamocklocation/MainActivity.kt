package com.sywd.usamocklocation

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.sywd.usamocklocation.ui.MainScreen
import com.sywd.usamocklocation.ui.MainViewModel
import com.sywd.usamocklocation.ui.theme.UsaMockLocationTheme

data class EnvironmentStatus(
    val hasFineLocationPermission: Boolean,
    val isLocationEnabled: Boolean,
    val isSelectedMockApp: Boolean,
)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var environmentStatus by mutableStateOf(
        EnvironmentStatus(
            hasFineLocationPermission = false,
            isLocationEnabled = false,
            isSelectedMockApp = false,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshEnvironmentStatus()

        setContent {
            UsaMockLocationTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { grants ->
                    refreshEnvironmentStatus()
                    if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                        viewModel.consumePendingStart()?.let(::startIfReady)
                    } else {
                        viewModel.showError("需要精确定位权限才能运行前台模拟定位服务。")
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        environmentStatus = environmentStatus,
                        onStart = { code ->
                            if (!environmentStatus.hasFineLocationPermission) {
                                viewModel.setPendingStart(code)
                                permissionLauncher.launch(requiredRuntimePermissions())
                            } else {
                                startIfReady(code)
                            }
                        },
                        onStop = viewModel::stopMocking,
                        onOpenDeveloperSettings = ::openDeveloperSettings,
                        onOpenLocationSettings = ::openLocationSettings,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshEnvironmentStatus()
    }

    private fun startIfReady(code: String) {
        refreshEnvironmentStatus()
        when {
            !environmentStatus.hasFineLocationPermission ->
                viewModel.showError("需要精确定位权限才能启动。")
            !environmentStatus.isLocationEnabled ->
                viewModel.showError("系统定位服务未开启，请先开启定位。")
            !environmentStatus.isSelectedMockApp ->
                viewModel.showError("请先在开发者选项中将本应用设为“模拟位置信息应用”。")
            else -> viewModel.startMocking(code)
        }
    }

    private fun refreshEnvironmentStatus() {
        val locationManager = getSystemService(LocationManager::class.java)
        environmentStatus = EnvironmentStatus(
            hasFineLocationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
            isLocationEnabled = locationManager.isLocationEnabled,
            isSelectedMockApp = isSelectedAsMockLocationApp(),
        )
    }

    private fun isSelectedAsMockLocationApp(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        return appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun requiredRuntimePermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            PermissionChecker.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun openDeveloperSettings() {
        openSettings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    }

    private fun openLocationSettings() {
        openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}
