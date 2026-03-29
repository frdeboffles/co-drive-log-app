package com.codrivelog.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.Supervisor

/**
 * Room database for the CO Drive Log app.
 *
 * Increment [version] whenever the schema changes and provide a migration
 * strategy or `fallbackToDestructiveMigration()` during development.
 */
@Database(
    entities = [DriveSession::class, Supervisor::class],
    version = 1,
    exportSchema = true,
)
abstract class CoDriveLogDatabase : RoomDatabase() {
    /** DAO for drive session operations. */
    abstract fun driveSessionDao(): DriveSessionDao

    /** DAO for supervisor operations. */
    abstract fun supervisorDao(): SupervisorDao
}
