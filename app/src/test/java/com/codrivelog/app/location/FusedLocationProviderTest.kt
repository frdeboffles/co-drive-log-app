package com.codrivelog.app.location

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
class FusedLocationProviderTest {

    private val locationManager = mockk<LocationManager>(relaxed = true)
    private val context = mockk<Context> {
        every { getSystemService(Context.LOCATION_SERVICE) } returns locationManager
        every { mainExecutor } returns Executor { it.run() }
    }
    private val listener = slot<LocationListener>()

    @BeforeEach
    fun setUp() {
        // Android framework classes are stubs in JVM tests.
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        mockkConstructor(LocationRequest.Builder::class)
        every { anyConstructed<LocationRequest.Builder>().setQuality(any()) } answers { self as LocationRequest.Builder }
        every { anyConstructed<LocationRequest.Builder>().build() } returns mockk()
        every { locationManager.hasProvider(any()) } returns true
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtimeNanos() } returns BOOT
        every { locationManager.requestLocationUpdates(any<String>(), any<LocationRequest>(), any(), capture(listener)) } just runs
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `both providers are requested, even when switched off, and released on stop`() = runTest(UnconfinedTestDispatcher()) {
        // A provider that is off starts delivering when location is switched on.
        every { locationManager.isProviderEnabled(any()) } returns false

        val job = launch { FusedLocationProvider(context).locationUpdates(10_000L).toList(mutableListOf()) }
        job.cancel()

        verify(exactly = 1) { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, any<LocationRequest>(), any(), any<LocationListener>()) }
        verify(exactly = 1) { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, any<LocationRequest>(), any(), any<LocationListener>()) }
        verify { locationManager.removeUpdates(listener.captured) }
    }

    @Test
    fun `network fixes are dropped while GPS delivers`() = runTest(UnconfinedTestDispatcher()) {
        val fixes = mutableListOf<LatLng>()
        val job = launch { FusedLocationProvider(context).locationUpdates(10_000L).toList(fixes) }
        listener.captured.onLocationChanged(fix(LocationManager.GPS_PROVIDER, 1, 39.740, 4f))
        listener.captured.onLocationChanged(fix(LocationManager.NETWORK_PROVIDER, 2, 39.745, 30f))
        listener.captured.onLocationChanged(fix(LocationManager.GPS_PROVIDER, 11, 39.741, 4f))
        job.cancel()

        assertEquals(listOf(LatLng(39.740, -104.99, 4f), LatLng(39.741, -104.99, 4f)), fixes)
    }

    @Test
    fun `network fixes are used after GPS has been silent for 30 s`() = runTest(UnconfinedTestDispatcher()) {
        // Review finding 1: in a garage or between tall buildings GPS gives
        // no fix; the network takes over.
        val fixes = mutableListOf<LatLng>()
        val job = launch { FusedLocationProvider(context).locationUpdates(10_000L).toList(fixes) }
        listener.captured.onLocationChanged(fix(LocationManager.NETWORK_PROVIDER, 5, 39.745, 30f))
        listener.captured.onLocationChanged(fix(LocationManager.NETWORK_PROVIDER, 35, 39.746, 30f))
        job.cancel()

        assertEquals(listOf(LatLng(39.746, -104.99, 30f)), fixes)
    }

    @Test
    fun `a phone clock far ahead does not let network fixes in while GPS works`() = runTest(UnconfinedTestDispatcher()) {
        // Review finding 1: GPS fixes carry satellite time, network fixes the
        // phone clock. Silence is measured on time since boot instead.
        val fixes = mutableListOf<LatLng>()
        val job = launch { FusedLocationProvider(context).locationUpdates(10_000L).toList(fixes) }

        listener.captured.onLocationChanged(fix(LocationManager.GPS_PROVIDER, 1, 39.740, 4f, wallClockSkewMs = 0))
        listener.captured.onLocationChanged(fix(LocationManager.NETWORK_PROVIDER, 2, 39.745, 30f, wallClockSkewMs = 120_000))
        job.cancel()

        assertEquals(listOf(LatLng(39.740, -104.99, 4f)), fixes)
    }

    @Test
    fun `last known location is the freshest cached fix`() {
        every { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } returns fix(LocationManager.GPS_PROVIDER, -600, 39.70, 5f)
        every { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } returns fix(LocationManager.NETWORK_PROVIDER, -60, 39.71, 40f)

        assertEquals(LatLng(39.71, -104.99, 40f), FusedLocationProvider(context).lastKnownLocation())
    }

    @Test
    fun `a refused request fails the flow instead of crashing`() = runTest(UnconfinedTestDispatcher()) {
        // Review finding 3: permission revoked during the drive.
        every { locationManager.requestLocationUpdates(any<String>(), any<LocationRequest>(), any(), any<LocationListener>()) } throws SecurityException("revoked")

        assertThrows<SecurityException> { FusedLocationProvider(context).locationUpdates(10_000L).toList() }
    }

    @Test
    fun `a failed registration removes the listener it already registered`() = runTest(UnconfinedTestDispatcher()) {
        // Review finding 3: GPS registers, then the network throws; the GPS
        // listener must not stay active, or each retry would add another.
        every {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, any<LocationRequest>(), any(), any<LocationListener>())
        } throws SecurityException("revoked")

        assertThrows<SecurityException> { FusedLocationProvider(context).locationUpdates(10_000L).toList() }

        verify(exactly = 1) { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, any<LocationRequest>(), any(), any<LocationListener>()) }
        verify(exactly = 1) { locationManager.removeUpdates(listener.captured) }
    }

    @Test
    fun `without location permission the flow completes and nothing is requested`() = runTest(UnconfinedTestDispatcher()) {
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED

        assertEquals(emptyList<LatLng>(), FusedLocationProvider(context).locationUpdates(10_000L).toList())
        verify(exactly = 0) { locationManager.requestLocationUpdates(any<String>(), any<LocationRequest>(), any(), any<LocationListener>()) }
    }

    /** A fix [secondsAfterStart] after the stream started, on the time-since-boot clock. */
    private fun fix(provider: String, secondsAfterStart: Long, lat: Double, accuracy: Float, wallClockSkewMs: Long = 0) = mockk<Location> {
        every { this@mockk.provider } returns provider
        every { elapsedRealtimeNanos } returns BOOT + secondsAfterStart * 1_000_000_000L
        every { this@mockk.time } returns System.currentTimeMillis() + secondsAfterStart * 1_000 + wallClockSkewMs
        every { latitude } returns lat
        every { longitude } returns -104.99
        every { this@mockk.accuracy } returns accuracy
    }

    private companion object {
        const val BOOT = 1_000_000_000_000L
    }
}
