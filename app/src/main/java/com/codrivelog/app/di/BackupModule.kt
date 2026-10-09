package com.codrivelog.app.di

import com.codrivelog.app.backup.BackupFileStore
import com.codrivelog.app.backup.MediaStoreBackupFileStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    abstract fun bindBackupFileStore(impl: MediaStoreBackupFileStore): BackupFileStore
}
