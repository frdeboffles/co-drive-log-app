package com.codrivelog.app.data.model

/** Where a drive's night minutes come from. Stored by name in `drive_sessions.nightSource`. */
enum class NightSource {
    /** Calculated from sunset and sunrise at `nightLatitude`/`nightLongitude`. */
    SUN,

    /** Accumulated from the manual night switch of a timed drive without GPS. */
    MANUAL,

    /** Stored before the source was recorded, and not recognizable from the value. */
    UNKNOWN,
}
