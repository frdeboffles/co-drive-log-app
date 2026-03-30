package com.codrivelog.app.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Extension property providing a single DataStore instance per process. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

/**
 * Persists the one-time onboarding data — student name and whether onboarding
 * has been completed — using Jetpack DataStore Preferences.
 *
 * After onboarding is marked complete the app skips the onboarding screen on
 * every subsequent launch.
 */
@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        val KEY_COMPLETED    = booleanPreferencesKey("onboarding_completed")
        val KEY_STUDENT_NAME = stringPreferencesKey("student_name")
    }

    /** Emits `true` once onboarding has been completed. */
    val isOnboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_COMPLETED] ?: false
        }

    /** Emits the stored student name, or empty string if not yet set. */
    val studentName: Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_STUDENT_NAME] ?: ""
        }

    /**
     * Save the student name and mark onboarding as complete.
     *
     * @param studentName The student's full name.
     */
    suspend fun completeOnboarding(studentName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STUDENT_NAME] = studentName.trim()
            prefs[KEY_COMPLETED]    = true
        }
    }

    /** Reset onboarding — useful for testing or a "reset app data" flow. */
    suspend fun resetOnboarding() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_STUDENT_NAME)
            prefs.remove(KEY_COMPLETED)
        }
    }
}
