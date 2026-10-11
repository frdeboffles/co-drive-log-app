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
import com.codrivelog.app.data.model.NightSource
import com.codrivelog.app.data.repository.DriveRouteRepository
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.location.LatLng
import com.codrivelog.app.location.LocationProvider
import com.codrivelog.app.ui.MainActivity
import com.codrivelog.app.util.ElapsedTimeFormatter
import com.codrivelog.app.util.DriveMinutes
import com.codrivelog.app.util.NightMinutesCalculator
import com.codrivelog.app.util.NightRuleRecalculation
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
import java.time.Instant
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
 * ### Location updates
 * A continuous location request delivers a fix about every
 * [LOCATION_UPDATE_INTERVAL_MS]. The service records it as a route point and
 * uses [SunCalculator] to classify the current moment as day or night; the
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
    // Instants, not local times: across the daylight-saving fall-back hour
    // local times repeat, and converting them back to UTC guesses the offset.
    private var startInstant:     Instant?       = null
    /** [startInstant] on the local clock, for the UI. */
    private var startLocal:       LocalDateTime? = null
    private var supervisorName:   String         = ""
    private var supervisorInitials: String       = ""
    private var comments:         String?        = null
    private var nightSeconds:     Long           = 0L
    private var lastLocation:     LatLng?        = null
    private var isCurrentlyNight: Boolean        = false
    private var manualNightOverride: Boolean     = false
    /** `true` once the night switch counted a second, i.e. it was on without a GPS fix. */
    private var manualSwitchUsed: Boolean        = false
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
        startInstant       = Instant.now()
        startLocal         = LocalDateTime.ofInstant(startInstant, ZoneId.systemDefault())
        nightSeconds       = 0L
        lastLocation       = null
        isCurrentlyNight   = false
        manualNightOverride = false
        manualSwitchUsed   = false
        routePointsBuffer = mutableListOf()

        serviceScope.launch {
            val fix = locationProvider.getLastLocation()
            if (fix != null) {
                lastLocation = fix
                maybeRecordRoutePoint(fix, nowUtc(), force = true)
            }
        }

        startTickLoop()
        startLocationLoop()
    }

    private fun handleStop() {
        tickJob?.cancel()
        locationJob?.cancel()

        val startInstant = startInstant ?: run { stopSelf(); return }
        val endInstant   = Instant.now()

        timerRepository.update(TimerState.Saving)
        updateNotification(getString(R.string.notification_text_saving))

        serviceScope.launch {
            val zone = ZoneId.systemDefault()
            // Local times are stored for display; durations and night minutes
            // come from the instants, so the fall-back hour cannot skew them.
            val start = LocalDateTime.ofInstant(startInstant, zone)
            val end = LocalDateTime.ofInstant(endInstant, zone)
            val endUtc = LocalDateTime.ofInstant(endInstant, ZoneOffset.UTC)
            val fixLocation = lastLocation?.let { NightRuleRecalculation.Location(it.latitude, it.longitude) }
            val nightSource = timedDriveNightSource(hasFix = fixLocation != null, manualSwitchUsed = manualSwitchUsed)
            val (totalMinutes, computedNightMinutes) =
                timedDriveMinutes(startInstant, endInstant, fixLocation, nightSeconds)

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
                nightSource        = nightSource,
                nightLatitude      = fixLocation?.latitude,
                nightLongitude     = fixLocation?.longitude,
                timeZone           = zone.id,
            )
            val sessionId = sessionRepository.insert(session)

            val finalFix = locationProvider.getLastLocation()
            if (finalFix != null) {
                maybeRecordRoutePoint(finalFix, endUtc, force = true)
            }

            routePointsBuffer.forEach { draft ->
                routeRepository.insert(
                    DriveRoutePoint(
                        sessionId = sessionId,
                        timestamp = draft.timestamp,
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
                val startInstant = startInstant ?: break
                val start = startLocal ?: break
                val elapsed = Duration.between(startInstant, Instant.now()).seconds
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
                if (lastLocation == null && manualNightOverride) manualSwitchUsed = true

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

    /**
     * Collects a continuous location request for the whole drive. One-shot
     * requests every 10 s, as before, kept the GPS idle in between, and with
     * the screen off the phone deferred them: routes got about one point a
     * minute.
     */
    private fun startLocationLoop() {
        locationJob = serviceScope.launch {
            locationProvider.locationUpdates(LOCATION_UPDATE_INTERVAL_MS).collect { fix ->
                lastLocation = fix
                val now = nowUtc()
                maybeRecordRoutePoint(fix, now, force = false)
                val date = now.toLocalDate()
                val times = SunCalculator.calculate(fix.latitude, fix.longitude, date)
                val previousDayTimes = SunCalculator.calculate(fix.latitude, fix.longitude, date.minusDays(1))

                isCurrentlyNight = NightMinutesCalculator.isNightUtc(now, times, previousDayTimes)
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


    /** Route points are stamped in UTC, like the stored timestamps. */
    private fun nowUtc(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

    private fun maybeRecordRoutePoint(fix: LatLng, now: LocalDateTime, force: Boolean) {
        val recentAccepted = routePointsBuffer.lastOrNull()
        val shouldRecord = shouldRecordRoutePoint(
            recentAccepted = recentAccepted,
            fix = fix,
            now = now,
            force = force,
            strictAccuracyMeters = ROUTE_MAX_ACCURACY_METERS,
            fallbackAccuracyMeters = ROUTE_FALLBACK_MAX_ACCURACY_METERS,
            fallbackStaleMinutes = ROUTE_FALLBACK_STALE_MINUTES,
            dedupeMinDistanceMeters = ROUTE_DEDUPE_MIN_DISTANCE_METERS,
            dedupeAccuracyFactor = ROUTE_DEDUPE_ACCURACY_FACTOR,
            dedupeMinIntervalMinutes = ROUTE_DEDUPE_MIN_INTERVAL_MINUTES,
        )
        if (!shouldRecord) return

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
        internal const val LOCATION_UPDATE_INTERVAL_MS = 10_000L

        internal const val ROUTE_MAX_ACCURACY_METERS = 80f
        internal const val ROUTE_FALLBACK_MAX_ACCURACY_METERS = 120f
        internal const val ROUTE_FALLBACK_STALE_MINUTES = 3L
        /**
         * A fix closer to the last point than the larger of these is GPS
         * jitter, not movement: 10 m, or twice the fix's accuracy.
         */
        internal const val ROUTE_DEDUPE_MIN_DISTANCE_METERS = 10.0
        internal const val ROUTE_DEDUPE_ACCURACY_FACTOR = 2.0
        internal const val ROUTE_DEDUPE_MIN_INTERVAL_MINUTES = 2L
    }
}

/**
 * Where a timed drive's night minutes come from. [NightSource.MANUAL] only
 * when the switch counted night: a drive without a fix where nobody used the
 * switch is [NightSource.UNKNOWN], so editing its times can still give it
 * night credit.
 */
internal fun timedDriveNightSource(hasFix: Boolean, manualSwitchUsed: Boolean): NightSource = when {
    hasFix           -> NightSource.SUN
    manualSwitchUsed -> NightSource.MANUAL
    else             -> NightSource.UNKNOWN
}

/**
 * Total and night minutes of a timed drive, from its start and stop instants.
 *
 * With a GPS fix, night minutes are the sun calculation at that fix;
 * without one, the seconds counted with the manual night switch. Night
 * minutes never exceed the total.
 */
internal fun timedDriveMinutes(
    startInstant: Instant,
    endInstant: Instant,
    fixLocation: NightRuleRecalculation.Location?,
    manualNightSeconds: Long,
): Pair<Int, Int> {
    val minutes = DriveMinutes.fromInstants(startInstant, endInstant, fixLocation)
    val night = minutes.night ?: (manualNightSeconds / 60).toInt().coerceAtMost(minutes.total)
    return minutes.total to night
}

internal data class DriveRoutePointDraft(
    /** UTC, as stored in `drive_route_points.timestamp`. */
    val timestamp: LocalDateTime,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
)

internal fun shouldRecordRoutePoint(
    recentAccepted: DriveRoutePointDraft?,
    fix: LatLng,
    now: LocalDateTime,
    force: Boolean,
    strictAccuracyMeters: Float,
    fallbackAccuracyMeters: Float,
    fallbackStaleMinutes: Long,
    dedupeMinDistanceMeters: Double,
    dedupeAccuracyFactor: Double,
    dedupeMinIntervalMinutes: Long,
): Boolean {
    if (force) return true

    val minutesSinceLast = recentAccepted
        ?.let { Duration.between(it.timestamp, now).toMinutes() }
        ?: Long.MAX_VALUE
    val withinStrictAccuracy = fix.accuracyMeters <= strictAccuracyMeters
    val withinFallbackAccuracy = fix.accuracyMeters <= fallbackAccuracyMeters
    val acceptForFallback = withinFallbackAccuracy && minutesSinceLast >= fallbackStaleMinutes
    if (!(withinStrictAccuracy || acceptForFallback)) return false

    if (recentAccepted != null) {
        val distanceMeters = distanceMeters(
            recentAccepted.latitude,
            recentAccepted.longitude,
            fix.latitude,
            fix.longitude,
        )
        // Jitter scales with the fix's accuracy: a precise fix counts as
        // movement sooner, a coarse one needs to move further.
        val jitterMeters = maxOf(dedupeMinDistanceMeters, dedupeAccuracyFactor * fix.accuracyMeters)
        if (distanceMeters < jitterMeters && minutesSinceLast < dedupeMinIntervalMinutes) {
            return false
        }
    }

    return true
}

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

