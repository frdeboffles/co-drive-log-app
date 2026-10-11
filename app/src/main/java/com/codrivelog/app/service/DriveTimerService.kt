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
import com.codrivelog.app.data.db.TimedDriveDao
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.NightSource
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
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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

    @Inject lateinit var timedDriveDao:     TimedDriveDao
    @Inject lateinit var timerRepository:   DriveTimerRepository
    @Inject lateinit var locationProvider:  LocationProvider

    // One thread for all of the drive's state (route buffer, last fix, night
    // seconds): the location stream, the tick loop and the save never run at
    // the same time. A failure is logged instead of crashing the service.
    private val serviceScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default.limitedParallelism(1) +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Drive timer coroutine failed", e) },
    )

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
    /** UTC time [lastLocation] arrived, to tell whether it is fresh at stop. */
    private var lastFixAtUtc:     LocalDateTime? = null
    /** Guards Start and Stop: a new drive starts only from IDLE, a save only from RUNNING. */
    private var phase: Phase = Phase.IDLE
    /** Whether the service runs with the location foreground type. Written on the main thread. */
    @Volatile private var locationForeground: Boolean = false

    private enum class Phase { IDLE, RUNNING, SAVING }

    // ---- Service lifecycle ----

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification(getString(R.string.notification_text_idle))
        locationForeground = hasLocationPermission()
        val fgsType = if (locationForeground) {
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
        val name     = intent.getStringExtra(EXTRA_SUPERVISOR_NAME)     ?: ""
        val initials = intent.getStringExtra(EXTRA_SUPERVISOR_INITIALS) ?: ""
        val note     = intent.getStringExtra(EXTRA_COMMENTS)
        // On the service's single thread, like everything that touches the
        // drive. Only from IDLE: not while a drive runs, nor while the
        // previous one is still saving (its save would clear this drive).
        serviceScope.launch {
            if (phase != Phase.IDLE) return@launch
            phase = Phase.RUNNING
            supervisorName     = name
            supervisorInitials = initials
            comments           = note
            startInstant       = Instant.now()
            startLocal         = LocalDateTime.ofInstant(startInstant, ZoneId.systemDefault())
            nightSeconds       = 0L
            lastLocation       = null
            lastFixAtUtc       = null
            isCurrentlyNight   = false
            manualNightOverride = false
            manualSwitchUsed   = false
            routePointsBuffer = mutableListOf()

            startTickLoop()
            startLocationLoop()
        }
    }

    private fun handleStop() {
        val endInstant = Instant.now()
        serviceScope.launch {
            // Only a running drive is saved: a second Stop while saving must
            // not insert the drive again.
            if (phase != Phase.RUNNING) {
                if (phase == Phase.IDLE) stopSelf()
                return@launch
            }
            phase = Phase.SAVING
            tickJob?.cancel()
            locationJob?.cancel()
            timerRepository.update(TimerState.Saving)
            updateNotification(getString(R.string.notification_text_saving))
            try {
                saveDrive(startInstant ?: endInstant, endInstant)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Drive could not be saved", e)
                notifySaveFailed()
            } finally {
                // Never left in Saving: the timer, the import and the next
                // drive would all stay blocked.
                routePointsBuffer.clear()
                phase = Phase.IDLE
                timerRepository.update(TimerState.Idle)
                stopSelf()
            }
        }
    }

    private suspend fun saveDrive(startInstant: Instant, endInstant: Instant) {
        val zone = ZoneId.systemDefault()
        // Local times are stored for display; durations and night minutes
        // come from the instants, so the fall-back hour cannot skew them.
        val start = LocalDateTime.ofInstant(startInstant, zone)
        val end = LocalDateTime.ofInstant(endInstant, zone)
        val endUtc = LocalDateTime.ofInstant(endInstant, ZoneOffset.UTC)
        val sunLocation = timedDriveNightLocation(lastLocation, manualSwitchUsed) { locationProvider.lastKnownLocation() }
            ?.let { NightRuleRecalculation.Location(it.latitude, it.longitude) }
        val nightSource = timedDriveNightSource(hasFix = sunLocation != null, manualSwitchUsed = manualSwitchUsed)
        val (totalMinutes, computedNightMinutes) =
            timedDriveMinutes(startInstant, endInstant, sunLocation, nightSeconds)

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
            nightLatitude      = sunLocation?.latitude,
            nightLongitude     = sunLocation?.longitude,
            timeZone           = zone.id,
        )

        // The stream's last fix ends the route, only if it is fresh; the
        // movement rule keeps it out when it is already the last point.
        val lastFix = lastLocation
        val lastFixAt = lastFixAtUtc
        if (lastFix != null && lastFixAt != null &&
            Duration.between(lastFixAt, endUtc).seconds <= FINAL_POINT_MAX_AGE_SECONDS
        ) {
            maybeRecordRoutePoint(lastFix, endUtc, force = false)
        }

        // One transaction: a failed save leaves nothing behind, so adding the
        // drive by hand afterwards cannot duplicate it.
        timedDriveDao.insertDriveWithRoute(
            session = session,
            routePoints = routePointsBuffer.map { draft ->
                DriveRoutePoint(
                    sessionId = 0,
                    timestamp = draft.timestamp,
                    latitude = draft.latitude,
                    longitude = draft.longitude,
                    accuracyMeters = draft.accuracyMeters,
                )
            },
        )
    }

    private fun notifySaveFailed() {
        notificationManager().notify(
            SAVE_FAILED_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_save_failed))
                .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_save_failed)))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .build(),
        )
    }

    /**
     * Promotes the service to the location foreground type once location
     * permission exists, so a request restarted after the user granted it
     * also works with the screen locked. Runs before each restart.
     */
    private suspend fun ensureLocationForeground() {
        if (locationForeground || !hasLocationPermission()) return
        withContext(Dispatchers.Main) {
            try {
                ServiceCompat.startForeground(
                    this@DriveTimerService,
                    NOTIFICATION_ID,
                    buildNotification(getString(R.string.notification_text_idle)),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
                locationForeground = true
            } catch (e: Exception) {
                Log.w(TAG, "Could not switch to the location foreground type", e)
            }
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
     * Collects a continuous location request for the whole drive, restarted
     * whenever it ends or fails. One-shot requests every 10 s, as before,
     * kept the GPS idle in between, and with the screen off the phone
     * deferred them: routes got about one point a minute.
     */
    private fun startLocationLoop() {
        locationJob = serviceScope.launch {
            collectLocationWithRestart(
                updates        = { locationProvider.locationUpdates(LOCATION_UPDATE_INTERVAL_MS) },
                restartDelayMs = LOCATION_RESTART_DELAY_MS,
                beforeAttempt  = { ensureLocationForeground() },
                onError        = { e -> Log.w(TAG, "Location updates stopped; retrying", e) },
            ) { fix ->
                lastLocation = fix
                val now = nowUtc()
                lastFixAtUtc = now
                // Not forced: the first point must pass the accuracy filter
                // too, or a coarse network fix would start the route.
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

        private const val TAG             = "DriveTimerService"
        private const val CHANNEL_ID      = "drive_timer_channel"
        private const val NOTIFICATION_ID = 1
        private const val SAVE_FAILED_NOTIFICATION_ID = 2

        /** The last fix ends the route only if it is at most this old at stop. */
        private const val FINAL_POINT_MAX_AGE_SECONDS = 30L

        /** How often the notification + StateFlow are refreshed. */
        internal const val TICK_INTERVAL_MS     = 1_000L

        /** Interval of the continuous location request during a drive. */
        internal const val LOCATION_UPDATE_INTERVAL_MS = 10_000L

        /** Wait before the location request is retried after it ended or failed. */
        internal const val LOCATION_RESTART_DELAY_MS = 10_000L

        internal const val ROUTE_MAX_ACCURACY_METERS = 80f
        internal const val ROUTE_FALLBACK_MAX_ACCURACY_METERS = 120f
        internal const val ROUTE_FALLBACK_STALE_MINUTES = 3L
        /**
         * A fix closer to the last point than the larger of these is GPS
         * jitter, not movement: 10 m, or twice the combined accuracy of the
         * fix and the last point.
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

/**
 * Collects [updates] into [onFix] until cancelled. When the flow ends (no
 * permission, location off) or fails (permission revoked), it is started
 * again after [restartDelayMs], so a drive recovers once location works.
 */
internal suspend fun collectLocationWithRestart(
    updates: () -> Flow<LatLng>,
    restartDelayMs: Long,
    onError: (Exception) -> Unit,
    beforeAttempt: suspend () -> Unit = {},
    onFix: suspend (LatLng) -> Unit,
) {
    while (currentCoroutineContext().isActive) {
        try {
            beforeAttempt()
            updates().collect { onFix(it) }
        } catch (e: CancellationException) {
            // Only the drive ending stops the loop; a cancellation from
            // inside the flow is retried like any other failure.
            if (!currentCoroutineContext().isActive) throw e
            onError(e)
        } catch (e: Exception) {
            onError(e)
        }
        delay(restartDelayMs)
    }
}

/**
 * Location for the night calculation of a timed drive: the stream's last
 * fix; without one, the phone's last known location, unless the night switch
 * was used, which then decides. Never used for the route.
 */
internal fun timedDriveNightLocation(
    streamFix: LatLng?,
    manualSwitchUsed: Boolean,
    lastKnown: () -> LatLng?,
): LatLng? = streamFix ?: if (manualSwitchUsed) null else lastKnown()

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
        // Jitter scales with the accuracy of both points: precise points
        // count as movement sooner; after or before a coarse one, the
        // distance must be larger.
        val combinedAccuracy = kotlin.math.hypot(fix.accuracyMeters.toDouble(), recentAccepted.accuracyMeters.toDouble())
        val jitterMeters = maxOf(dedupeMinDistanceMeters, dedupeAccuracyFactor * combinedAccuracy)
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

