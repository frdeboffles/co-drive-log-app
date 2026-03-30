package com.codrivelog.app.di

import com.codrivelog.app.location.FusedLocationProvider
import com.codrivelog.app.location.LocationProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds the [LocationProvider] interface to its production
 * implementation at the [SingletonComponent] scope.
 *
 * Separating this from [DatabaseModule] keeps each module focused on a single
 * concern and makes it easy to swap the implementation in tests.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    /**
     * Binds [FusedLocationProvider] as the singleton [LocationProvider].
     *
     * @param impl The concrete GPS-backed implementation.
     */
    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider
}
