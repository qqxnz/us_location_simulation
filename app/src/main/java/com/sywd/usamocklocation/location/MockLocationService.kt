package com.sywd.usamocklocation.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sywd.usamocklocation.MainActivity
import com.sywd.usamocklocation.R
import com.sywd.usamocklocation.data.StateCapital
import com.sywd.usamocklocation.data.StateCapitals
import com.sywd.usamocklocation.state.AppStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MockLocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var stateStore: AppStateStore
    private lateinit var session: MockLocationSession
    private var injectionJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        stateStore = AppStateStore.get(this)
        session = MockLocationSession(
            SystemLocationInjector(getSystemService(LocationManager::class.java)),
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopMocking()
            ACTION_START -> startMocking(intent.getStringExtra(EXTRA_STATE_CODE))
            else -> startMocking(stateStore.state.value.activeCode)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMocking(code: String?) {
        val capital = StateCapitals.find(code)
        if (capital == null) {
            failAndStop("找不到所选州府，请重新选择。")
            return
        }
        if (!hasLocationPermission()) {
            failAndStop("定位权限已被撤销，请重新授权后再启动。")
            return
        }

        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(capital),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        }.onFailure {
            failAndStop("无法启动定位前台服务：${it.message ?: it.javaClass.simpleName}")
            return
        }

        injectionJob?.cancel()
        runCatching {
            session.start(capital)
        }.onFailure {
            failAndStop(friendlyError(it))
            return
        }

        stateStore.markRunning(capital.code)
        acquireWakeLock()
        injectionJob = scope.launch {
            while (isActive) {
                delay(INJECTION_INTERVAL_MS)
                try {
                    session.tick()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    failAndStop(friendlyError(error))
                    break
                }
            }
        }
    }

    private fun stopMocking() {
        injectionJob?.cancel()
        injectionJob = null
        runCatching { session.stop() }
        releaseWakeLock()
        stateStore.markStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun failAndStop(message: String) {
        injectionJob?.cancel()
        injectionJob = null
        runCatching { session.stop() }
        releaseWakeLock()
        stateStore.markStopped(message)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun friendlyError(error: Throwable): String = when (error) {
        is SecurityException -> "系统拒绝模拟定位。请在开发者选项中将本应用设为“模拟位置信息应用”。"
        is MockLocationUnavailableException -> error.message ?: "模拟定位不可用。"
        else -> "模拟定位已停止：${error.message ?: error.javaClass.simpleName}"
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildNotification(capital: StateCapital): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("${capital.stateNameZh} · ${capital.capitalNameZh} (${capital.code})")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:mock-location")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        injectionJob?.cancel()
        runCatching { session.stop() }
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.sywd.usamocklocation.action.START"
        const val ACTION_STOP = "com.sywd.usamocklocation.action.STOP"
        const val EXTRA_STATE_CODE = "state_code"

        private const val CHANNEL_ID = "mock_location_status"
        private const val NOTIFICATION_ID = 1001
        private const val INJECTION_INTERVAL_MS = 1_000L

        fun startIntent(context: Context, stateCode: String) =
            Intent(context, MockLocationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_STATE_CODE, stateCode)

        fun stopIntent(context: Context) =
            Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)
    }
}
