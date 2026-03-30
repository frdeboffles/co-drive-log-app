package com.codrivelog.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.codrivelog.app.R
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.repository.DriveRouteRepository
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.location.LatLng
import com.codrivelog.app.location.LocationProvider
import com.codrivelog.app.ui.MainActivity
import com.codrivelog.app.util.ElapsedTimeFormatter
import com.codrivelog.app.util.NightMinutesCalculator
import com.codrivelog.app.util.SunCalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * Foreground service that drives the live drive timer.
 *
 * ### Lifecycle
 * - Start with an [Intent] whose action is [ACTION_START] and extras containing
 *   [EXTRA_SUPERVISOR_NAME], [EXTRA_SUPERVISOR_INITIALS], and optionally
 *   [EXTRA_COMMENTS].
 * - Stop by sending an [Intent] with action [ACTION_STOP].  The service will
 *   compute the session's [DriveSession.totalMinutes] and [DriveSession.nightMinutes],
 *   persist the record via [DriveSessionRepository], and call [stopSelf].
 *
 * ### State sharing
 * The service updates [DriveTimerRepository.timerState] every [TICK_INTERVAL_MS]
 * so the UI layer can observe elapsed time and day/night status in real-time
 * without binding to the service.
 *
 * ### GPS polling
 * Every [LOCATION_POLL_INTERVAL_MS] the service calls [LocationProvider.getLastLocation]
 * and uses [SunCalculator] to classify the current moment as day or night.  The
 * accumulated [nightSeconds] counter is updated accordingly.
 */
@AndroidEntryPoint
class DriveTimerService : Service() {

    @Inject lateinit var sessionRepository: DriveSessionRepository
    @Inject lateinit var routeRepository:   DriveRouteRepository
    @Inject lateinit var timerRepository:   DriveTimerRepository
    @Inject lateinit var locationProvider:  LocationProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var tickJob:     Job? = null
    private var locationJob: Job? = null

    // Mutable session-level accumulators (only written from serviceScope)
    private var startTime:        LocalDateTime? = null
    private var supervisorName:   String         = ""
    private var supervisorInitials: String       = ""
    private var comments:         String?        = null
    private var nightSeconds:     Long           = 0L
    private var lastLocation:     LatLng?        = null
    private var isCurrentlyNight: Boolean        = false
    private var manualNightOverride: Boolean     = false
    private var routePointsBuffer: MutableList<DriveRoutePointDraft> = mutableListOf()

    // ---- Service lifecycle ----

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification(getString(R.string.notification_text_idle))
        val fgsType = if (hasLocationPermission()) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP  -> handleStop()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---- Command handlers ----

    private fun handleStart(intent: Intent) {
        if (timerRepository.timerState.value is TimerState.Running) return  // already running

        supervisorName     = intent.getStringExtra(EXTRA_SUPERVISOR_NAME)     ?: ""
        supervisorInitials = intent.getStringExtra(EXTRA_SUPERVISOR_INITIALS) ?: ""
        comments           = intent.getStringExtra(EXTRA_COMMENTS)
        startTime          = LocalDateTime.now()
        nightSeconds       = 0L
        lastLocation       = null
        isCurrentlyNight   = false
        manualNightOverride = false
        routePointsBuffer = mutableListOf()

        serviceScope.launch {
            val fix = locationProvider.getLastLocation()
            if (fix != null) {
                lastLocation = fix
                maybeRecordRoutePoint(fix, LocalDateTime.now(), force = true)
            }
        }

        startTickLoop()
        startLocationLoop()
    }

    private fun handleStop() {
        tickJob?.cancel()
        locationJob?.cancel()

        val start = startTime ?: run { stopSelf(); return }
        val end   = LocalDateTime.now()

        timerRepository.update(TimerState.Saving)
        updateNotification(getString(R.string.notification_text_saving))

        serviceScope.launch {
            val totalMinutes = Duration.between(start, end).toMinutes().toInt()
                .coerceAtLeast(0)

            // Re-compute night minutes from the full interval + location history.
            // If GPS was available, use the precise NOAA calculation.
            // If GPS was never available, fall back to the manually-accumulated nightSeconds
            // (which respects any manual night override the user toggled during the drive).
            val computedNightMinutes = lastLocation?.let { loc ->
                val startUtc = toUtc(start)
                val endUtc = toUtc(end)
                NightMinutesCalculator.computeNightMinutesForSession(
                    start         = startUtc,
                    end           = endUtc,
                    latitudeDeg   = loc.latitude,
                    longitudeDeg  = loc.longitude,
                )
            } ?: (nightSeconds / 60).toInt()

            val session = DriveSession(
                date               = start.toLocalDate(),
                startTime          = start,
                endTime            = end,
                totalMinutes       = totalMinutes,
                nightMinutes       = computedNightMinutes,
                supervisorName     = supervisorName,
                supervisorInitials = supervisorInitials,
                comments           = comments,
                isManualEntry      = false,
            )
            val sessionId = sessionRepository.insert(session)

            val finalFix = locationProvider.getLastLocation()
            if (finalFix != null) {
                maybeRecordRoutePoint(finalFix, end, force = true)
            }

            routePointsBuffer.forEach { draft ->
                routeRepository.insert(
                    DriveRoutePoint(
                        sessionId = sessionId,
                        timestamp = toUtc(draft.timestamp),
                        latitude = draft.latitude,
                        longitude = draft.longitude,
                        accuracyMeters = draft.accuracyMeters,
                    )
                )
            }
            routePointsBuffer.clear()
            timerRepository.update(TimerState.Idle)
            stopSelf()
        }
    }

    // ---- Background loops ----

    private fun startTickLoop() {
        tickJob = serviceScope.launch {
            while (true) {
                val start = startTime ?: break
                val elapsed = Duration.between(start, LocalDateTime.now()).seconds
                    .coerceAtLeast(0L)

                // Read any manual override the user may have toggled via the UI.
                val currentState = timerRepository.timerState.value
                if (currentState is TimerState.Running) {
                    manualNightOverride = currentState.manualNightOverride
                }

                // When there is no GPS fix, apply the manual override; otherwise use NOAA calc.
                val effectivelyNight = if (lastLocation == null) manualNightOverride else isCurrentlyNight

                // Accumulate night seconds each tick (1 s granularity).
                if (effectivelyNight) nightSeconds += TICK_INTERVAL_MS / 1_000L

                timerRepository.update(
                    TimerState.Running(
                        startTime           = start,
                        elapsedSeconds      = elapsed,
                        nightSeconds        = nightSeconds,
                        currentlyNight      = effectivelyNight,
                        latitude            = lastLocation?.latitude,
                        longitude           = lastLocation?.longitude,
                        manualNightOverride = manualNightOverride,
                    )
                )

                val label = ElapsedTimeFormatter.formatHms(elapsed)
                updateNotification(
                    getString(R.string.notification_text_running, label)
                )

                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun startLocationLoop() {
        locationJob = serviceScope.launch {
            while (true) {
                val fix = locationProvider.getLastLocation()
                if (fix != null) {
                    lastLocation = fix
                    maybeRecordRoutePoint(fix, LocalDateTime.now(), force = false)
                    val nowUtc = LocalDateTime.now(ZoneOffset.UTC)
                    val times = SunCalculator.calculate(fix.latitude, fix.longitude, nowUtc.toLocalDate())

                    isCurrentlyNight = isNightUtc(nowUtc, times)
                }
                delay(LOCATION_POLL_INTERVAL_MS)
            }
        }
    }

    // ---- Helpers ----

    /**
     * Returns `true` when the user has granted at least coarse location permission,
     * which is required to promote this service to [ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION]
     * on Android 14+ (targetSdk 34+).
     */
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun toUtc(localDateTime: LocalDateTime): LocalDateTime =
        localDateTime
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime()

    private fun maybeRecordRoutePoint(fix: LatLng, now: LocalDateTime, force: Boolean) {
        val recentAccepted = routePointsBuffer.lastOrNull()
        val withinStrictAccuracy = fix.accuracyMeters <= ROUTE_MAX_ACCURACY_METERS
        val withinFallbackAccuracy = fix.accuracyMeters <= ROUTE_FALLBACK_MAX_ACCURACY_METERS
        val minutesSinceLast = recentAccepted?.let { Duration.between(it.timestamp, now).toMinutes() } ?: Long.MAX_VALUE
        val acceptForFallback = withinFallbackAccuracy && minutesSinceLast >= ROUTE_FALLBACK_STALE_MINUTES

        if (!force && !(withinStrictAccuracy || acceptForFallback)) return

        if (!force && recentAccepted != null) {
            val distanceMeters = distanceMeters(
                recentAccepted.latitude,
                recentAccepted.longitude,
                fix.latitude,
                fix.longitude,
            )
            if (distanceMeters < ROUTE_DEDUPE_DISTANCE_METERS && minutesSinceLast < ROUTE_DEDUPE_MIN_INTERVAL_MINUTES) {
                return
            }
        }

        routePointsBuffer.add(
            DriveRoutePointDraft(
                timestamp = now,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters,
            )
        )
    }

    // ---- Notification ----

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DriveTimerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause,
                getString(R.string.notification_action_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        notificationManager().notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // ---- Constants ----

    companion object {
        /** Intent action: start a new drive session. */
        const val ACTION_START = "com.codrivelog.app.ACTION_START_DRIVE"

        /** Intent action: stop the current drive session and persist it. */
        const val ACTION_STOP  = "com.codrivelog.app.ACTION_STOP_DRIVE"

        /** Extra: supervisor full name (String). */
        const val EXTRA_SUPERVISOR_NAME     = "supervisor_name"

        /** Extra: supervisor initials (String). */
        const val EXTRA_SUPERVISOR_INITIALS = "supervisor_initials"

        /** Extra: optional comments for the session (String?). */
        const val EXTRA_COMMENTS            = "comments"

        private const val CHANNEL_ID      = "drive_timer_channel"
        private const val NOTIFICATION_ID = 1

        /** How often the notification + StateFlow are refreshed. */
        internal const val TICK_INTERVAL_MS     = 1_000L

        /** How often the GPS cache is polled to update day/night status. */
        internal const val LOCATION_POLL_INTERVAL_MS = 60_000L

        internal const val ROUTE_MAX_ACCURACY_METERS = 80f
        internal const val ROUTE_FALLBACK_MAX_ACCURACY_METERS = 120f
        internal const val ROUTE_FALLBACK_STALE_MINUTES = 3L
        internal const val ROUTE_DEDUPE_DISTANCE_METERS = 25.0
        internal const val ROUTE_DEDUPE_MIN_INTERVAL_MINUTES = 2L
    }
}

private data class DriveRoutePointDraft(
    val timestamp: LocalDateTime,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
)

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
}

internal fun isNightUtc(nowUtc: LocalDateTime, sunTimes: SunCalculator.SunTimes): Boolean {
    val date = nowUtc.toLocalDate()
    val sunrise = sunTimes.sunrise?.let { date.atTime(it) } ?: return true
    val sunsetLt = sunTimes.sunset ?: return false
    val sunriseLt = sunTimes.sunrise ?: return true
    val sunset = if (sunsetLt < sunriseLt) {
        date.plusDays(1).atTime(sunsetLt)
    } else {
        date.atTime(sunsetLt)
    }
    return nowUtc.isBefore(sunrise) || nowUtc.isEqual(sunset) || nowUtc.isAfter(sunset)
}
