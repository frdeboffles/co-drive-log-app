package com.codrivelog.app.data.fake

import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.data.model.DriveSession

/**
 * Thin [DriveSessionRepository] backed by [FakeDriveSessionDao] for
 * Compose UI tests.
 *
 * @param initialSessions Pre-populated sessions for test setup.
 */
class FakeDriveSessionRepository(
    initialSessions: List<DriveSession> = emptyList(),
) : DriveSessionRepository(dao = FakeDriveSessionDao(initial = initialSessions))
