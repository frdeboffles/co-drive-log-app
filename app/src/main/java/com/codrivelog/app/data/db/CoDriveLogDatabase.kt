package com.codrivelog.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.Supervisor

/**
 * Room database for the CO Drive Log app.
 *
 * [DateTimeConverters] is registered here so Room knows how to persist
 * [java.time.LocalDate] and [java.time.LocalDateTime] fields used in the
 * [DriveSession] entity.
 *
 * ### Schema changes
 * Increment [version] for any entity change and supply a [androidx.room.migration.Migration]
 * in [com.codrivelog.app.di.DatabaseModule], or call
 * `fallbackToDestructiveMigration()` during early development.
 */
@Database(
    entities = [DriveSession::class, Supervisor::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DateTimeConverters::class)
abstract class CoDriveLogDatabase : RoomDatabase() {

    /** DAO for drive-session operations. */
    abstract fun driveSessionDao(): DriveSessionDao

    /** DAO for supervisor operations. */
    abstract fun supervisorDao(): SupervisorDao
}
