package com.codrivelog.app.di

import android.content.Context
import androidx.room.Room
import com.codrivelog.app.data.db.CoDriveLogDatabase
import com.codrivelog.app.data.db.DatabaseMigrations
import com.codrivelog.app.data.db.DriveRoutePointDao
import com.codrivelog.app.data.db.DriveSessionDao
import com.codrivelog.app.data.db.SupervisorDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides database-related dependencies at the
 * [SingletonComponent] scope (application lifetime).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the singleton [CoDriveLogDatabase] Room instance.
     *
     * @param context Application context used by Room to open the database file.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CoDriveLogDatabase =
        Room.databaseBuilder(
            context,
            CoDriveLogDatabase::class.java,
            "co_drive_log.db",
        )
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .build()

    /**
     * Provides [DriveSessionDao] sourced from the singleton database.
     *
     * @param db The application database.
     */
    @Provides
    fun provideDriveSessionDao(db: CoDriveLogDatabase): DriveSessionDao =
        db.driveSessionDao()

    /**
     * Provides [SupervisorDao] sourced from the singleton database.
     *
     * @param db The application database.
     */
    @Provides
    fun provideSupervisorDao(db: CoDriveLogDatabase): SupervisorDao =
        db.supervisorDao()

    @Provides
    fun provideDriveRoutePointDao(db: CoDriveLogDatabase): DriveRoutePointDao =
        db.driveRoutePointDao()
}
