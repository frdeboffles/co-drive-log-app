package com.codrivelog.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point annotated with [HiltAndroidApp] to trigger Hilt's
 * code generation and set up the application-level dependency injection graph.
 */
@HiltAndroidApp
class CoDriveLogApplication : Application()
