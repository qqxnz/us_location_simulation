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
import android.os.SystemClock
import android.util.Log
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class MockLocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val systemMutex = Mutex()
    private val fusedMutex = Mutex()
    private val healthMonitor = InjectionHealthMonitor()
    private val ticker = IndependentInjectionTicker(
        intervalMs = INJECTION_INTERVAL_MS,
        fusedTimeoutMs = FUSED_CALL_TIMEOUT_MS,
    )

    private lateinit var stateStore: AppStateStore
    private lateinit var systemInjector: LocationInjector
    private lateinit var fusedInjector: LocationInjector

    private var systemJob: Job? = null
    private var fusedJob: Job? = null
    private var watchdogJob: Job? = null
    private var wakeLockJob: Job? = null
    private var cleanupJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var runGeneration = 0L
    private var currentCapital: StateCapital? = null

    @Volatile private var lastSystemSuccessMs = 0L
    @Volatile private var lastFusedSuccessMs = 0L
    @Volatile private var systemStatus = ChannelStatus.STARTING
    @Volatile private var fusedStatus = ChannelStatus.STARTING

    override fun onCreate() {
        super.onCreate()
        stateStore = AppStateStore.get(this)
        systemInjector = SystemLocationInjector(getSystemService(LocationManager::class.java))
        fusedInjector = newFusedInjector()
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
                buildNotification(capital, "系统启动中 · Fused启动中"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        }.onFailure {
            failAndStop("无法启动定位前台服务：${it.message ?: it.javaClass.simpleName}")
            return
        }

        cleanupJob?.cancel()
        cancelRuntimeJobs()
        val generation = ++runGeneration
        currentCapital = capital
        val now = SystemClock.elapsedRealtime()
        lastSystemSuccessMs = now
        lastFusedSuccessMs = now
        systemStatus = ChannelStatus.STARTING
        fusedStatus = ChannelStatus.STARTING
        acquireWakeLock()
        stateStore.markRunning(capital.code)

        launchSystemLoop(capital, generation)
        launchFusedLoop(capital, generation, rebuildClient = true)
        launchWatchdog(capital, generation)
        launchWakeLockMonitor(generation)
    }

    private fun launchSystemLoop(capital: StateCapital, generation: Long) {
        systemJob?.cancel()
        systemJob = scope.launch {
            try {
                systemMutex.withLock {
                    ignoreFailure { systemInjector.stop() }
                    systemInjector.start()
                    systemInjector.inject(capital)
                }
                recordSystemSuccess()
                ticker.runSystem(
                    tick = { systemMutex.withLock { systemInjector.inject(capital) } },
                    onSuccess = ::recordSystemSuccess,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                systemStatus = ChannelStatus.RECOVERING
                Log.e(TAG, "System provider injection stopped; watchdog will restart it", error)
                updateNotification(capital)
            }
        }
    }

    private fun launchFusedLoop(
        capital: StateCapital,
        generation: Long,
        rebuildClient: Boolean,
    ) {
        fusedJob?.cancel()
        fusedJob = scope.launch {
            if (rebuildClient) rebuildFusedClient()
            val failureTracker = ConsecutiveFailureTracker(FUSED_REBUILD_FAILURES)
            var needsStart = true

            ticker.runFused(
                tick = {
                    if (!isCurrentRun(generation)) throw CancellationException("stale run")
                    fusedMutex.withLock {
                        if (needsStart) {
                            fusedInjector.start()
                            needsStart = false
                        }
                        fusedInjector.inject(capital)
                    }
                },
                onSuccess = {
                    failureTracker.recordSuccess()
                    recordFusedSuccess()
                },
                onFailure = { error ->
                    fusedStatus = ChannelStatus.RECOVERING
                    Log.w(TAG, "Fused injection failed or timed out", error)
                    updateNotification(capital)

                    if (failureTracker.recordFailure()) {
                        Log.w(TAG, "Rebuilding Fused mock client after repeated failures")
                        rebuildFusedClient()
                        needsStart = true
                    }
                },
            )
        }
    }

    private suspend fun rebuildFusedClient() {
        fusedMutex.withLock {
            withTimeoutOrNull(FUSED_CALL_TIMEOUT_MS) { ignoreFailure { fusedInjector.stop() } }
            fusedInjector = newFusedInjector()
        }
    }

    private fun launchWatchdog(capital: StateCapital, generation: Long) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isCurrentRun(generation) && isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val actions = healthMonitor.evaluate(
                    nowElapsedMs = SystemClock.elapsedRealtime(),
                    lastSystemSuccessMs = lastSystemSuccessMs,
                    lastFusedSuccessMs = lastFusedSuccessMs,
                    wakeLockHeld = true,
                )
                Log.i(
                    TAG,
                    "Health systemAge=${SystemClock.elapsedRealtime() - lastSystemSuccessMs}ms " +
                        "fusedAge=${SystemClock.elapsedRealtime() - lastFusedSuccessMs}ms " +
                        "wakeLock=${wakeLock?.isHeld == true}",
                )
                if (actions.restartSystem) {
                    Log.w(TAG, "System provider heartbeat stale; restarting channel")
                    systemStatus = ChannelStatus.RECOVERING
                    launchSystemLoop(capital, generation)
                }
                if (actions.restartFused) {
                    Log.w(TAG, "Fused heartbeat stale; restarting channel")
                    fusedStatus = ChannelStatus.RECOVERING
                    launchFusedLoop(capital, generation, rebuildClient = true)
                    lastFusedSuccessMs = SystemClock.elapsedRealtime()
                }
                updateNotification(capital)
            }
        }
    }

    private fun launchWakeLockMonitor(generation: Long) {
        wakeLockJob?.cancel()
        wakeLockJob = scope.launch {
            while (isCurrentRun(generation) && isActive) {
                delay(WAKE_LOCK_CHECK_INTERVAL_MS)
                val actions = healthMonitor.evaluate(
                    nowElapsedMs = SystemClock.elapsedRealtime(),
                    lastSystemSuccessMs = lastSystemSuccessMs,
                    lastFusedSuccessMs = lastFusedSuccessMs,
                    wakeLockHeld = wakeLock?.isHeld == true,
                )
                if (actions.reacquireWakeLock) {
                    Log.w(TAG, "WakeLock was released by the system; acquiring it again")
                    acquireWakeLock()
                }
            }
        }
    }

    private fun recordSystemSuccess() {
        lastSystemSuccessMs = SystemClock.elapsedRealtime()
        systemStatus = ChannelStatus.HEALTHY
    }

    private fun recordFusedSuccess() {
        lastFusedSuccessMs = SystemClock.elapsedRealtime()
        fusedStatus = ChannelStatus.HEALTHY
    }

    private fun updateNotification(capital: StateCapital) {
        if (currentCapital?.code != capital.code) return
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(capital, notificationStatusText()),
        )
    }

    private fun notificationStatusText(): String {
        val now = SystemClock.elapsedRealtime()
        val latestSuccess = maxOf(lastSystemSuccessMs, lastFusedSuccessMs)
        val ageSeconds = ((now - latestSuccess).coerceAtLeast(0L) / 1_000L)
        return "系统${systemStatus.label} · Fused${fusedStatus.label} · 最近${ageSeconds}秒"
    }

    private fun stopMocking() {
        ++runGeneration
        currentCapital = null
        cancelRuntimeJobs()
        cleanupJob?.cancel()
        cleanupJob = scope.launch { cleanupAndStopService() }
    }

    private fun failAndStop(message: String) {
        ++runGeneration
        currentCapital = null
        cancelRuntimeJobs()
        cleanupJob?.cancel()
        cleanupJob = scope.launch { cleanupAndStopService(message) }
    }

    private suspend fun cleanupAndStopService(message: String? = null) {
        systemMutex.withLock { ignoreFailure { systemInjector.stop() } }
        fusedMutex.withLock {
            withTimeoutOrNull(FUSED_CALL_TIMEOUT_MS) { ignoreFailure { fusedInjector.stop() } }
        }
        releaseWakeLock()
        stateStore.markStopped(message)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cancelRuntimeJobs() {
        systemJob?.cancel()
        fusedJob?.cancel()
        watchdogJob?.cancel()
        wakeLockJob?.cancel()
        systemJob = null
        fusedJob = null
        watchdogJob = null
        wakeLockJob = null
    }

    private fun isCurrentRun(generation: Long) = generation == runGeneration

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildNotification(capital: StateCapital, statusText: String): Notification {
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
            .setContentTitle("${capital.stateNameZh} · ${capital.capitalNameZh} (${capital.code})")
            .setContentText(statusText)
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
        Log.i(TAG, "WakeLock acquired; held=${wakeLock?.isHeld}")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        wakeLock = null
    }

    private fun newFusedInjector(): LocationInjector = FusedLocationInjector(applicationContext)

    override fun onDestroy() {
        ++runGeneration
        cancelRuntimeJobs()
        cleanupJob?.cancel()
        runBlocking(Dispatchers.IO) {
            systemMutex.withLock { ignoreFailure { systemInjector.stop() } }
            fusedMutex.withLock {
                withTimeoutOrNull(FUSED_CALL_TIMEOUT_MS) { ignoreFailure { fusedInjector.stop() } }
            }
        }
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private enum class ChannelStatus(val label: String) {
        STARTING("启动中"),
        HEALTHY("正常"),
        RECOVERING("恢复中"),
    }

    private suspend inline fun ignoreFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w(TAG, "Best-effort cleanup failed", error)
        }
    }

    companion object {
        const val ACTION_START = "com.sywd.usamocklocation.action.START"
        const val ACTION_STOP = "com.sywd.usamocklocation.action.STOP"
        const val EXTRA_STATE_CODE = "state_code"

        private const val TAG = "MockLocationService"
        private const val CHANNEL_ID = "mock_location_status"
        private const val NOTIFICATION_ID = 1001
        private const val INJECTION_INTERVAL_MS = 1_000L
        private const val FUSED_CALL_TIMEOUT_MS = 3_000L
        private const val FUSED_REBUILD_FAILURES = 3
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        private const val WAKE_LOCK_CHECK_INTERVAL_MS = 10_000L

        fun startIntent(context: Context, stateCode: String) =
            Intent(context, MockLocationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_STATE_CODE, stateCode)

        fun stopIntent(context: Context) =
            Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)
    }
}
