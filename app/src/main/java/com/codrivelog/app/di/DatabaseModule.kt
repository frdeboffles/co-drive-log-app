package com.codrivelog.app.di

import android.content.Context
import androidx.room.Room
import com.codrivelog.app.backup.BackupJson
import com.codrivelog.app.backup.DatabaseBackup
import com.codrivelog.app.backup.MediaStoreBackupFileStore
import com.codrivelog.app.data.db.BackupDao
import com.codrivelog.app.data.db.CoDriveLogDatabase
import com.codrivelog.app.data.db.DatabaseMigrations
import com.codrivelog.app.data.db.DriveRoutePointDao
import com.codrivelog.app.data.db.DriveSessionDao
import com.codrivelog.app.data.db.SupervisorDao
import com.codrivelog.app.export.DownloadsWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
            .addMigrations(
                DatabaseMigrations.MIGRATION_1_2,
                DatabaseMigrations.migration2To3 { backup -> saveBackupBeforeUpdate(context, backup) },
            )
            .build()

    /**
     * Saves the data as it was before the 1.2.0 migration to Downloads, in
     * the Export backup format, so Import backup can restore it.
     */
    private fun saveBackupBeforeUpdate(context: Context, backup: DatabaseBackup) {
        val fileName = "CoDriveLog_backup_before_update_${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        }.json"
        DownloadsWriter.save(context, fileName, MediaStoreBackupFileStore.MIME_TYPE) { stream ->
            BackupJson.write(backup, stream)
        } ?: error("Could not write $fileName")
    }

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

    @Provides
    fun provideBackupDao(db: CoDriveLogDatabase): BackupDao =
        db.backupDao()
}
