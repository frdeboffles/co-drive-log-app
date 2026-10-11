package com.codrivelog.app.location

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
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
        every { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns true
        every { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } returns true
        every { locationManager.requestLocationUpdates(any<String>(), any<LocationRequest>(), any(), capture(listener)) } just runs
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `updates come from a continuous request that stops with the collector`() = runTest(UnconfinedTestDispatcher()) {
        val fixes = mutableListOf<LatLng>()
        val job = launch { FusedLocationProvider(context).locationUpdates(10_000L).toList(fixes) }

        listener.captured.onLocationChanged(location(39.74, -104.99, 5f))
        listener.captured.onLocationChanged(location(39.75, -104.98, 6f))
        job.cancel()

        assertEquals(listOf(LatLng(39.74, -104.99, 5f), LatLng(39.75, -104.98, 6f)), fixes)
        // GPS only, even with the network on: network fixes made routes zig-zag.
        // The GPS stops with the drive.
        verify(exactly = 1) { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, any<LocationRequest>(), any(), any<LocationListener>()) }
        verify(exactly = 0) { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, any<LocationRequest>(), any(), any<LocationListener>()) }
        verify { locationManager.removeUpdates(listener.captured) }
    }

    @Test
    fun `the network is used only when GPS is off`() = runTest(UnconfinedTestDispatcher()) {
        every { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns false

        val job = launch { FusedLocationProvider(context).locationUpdates(10_000L).toList(mutableListOf()) }
        job.cancel()

        verify(exactly = 1) { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, any<LocationRequest>(), any(), any<LocationListener>()) }
        verify(exactly = 0) { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, any<LocationRequest>(), any(), any<LocationListener>()) }
    }

    @Test
    fun `without location permission the flow is empty and nothing is requested`() = runTest(UnconfinedTestDispatcher()) {
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED

        val fixes = FusedLocationProvider(context).locationUpdates(10_000L).toList()

        assertEquals(emptyList<LatLng>(), fixes)
        verify(exactly = 0) { locationManager.requestLocationUpdates(any<String>(), any<LocationRequest>(), any(), any<LocationListener>()) }
    }

    private fun location(lat: Double, lng: Double, accuracy: Float) = mockk<Location> {
        every { latitude } returns lat
        every { longitude } returns lng
        every { this@mockk.accuracy } returns accuracy
    }
}
