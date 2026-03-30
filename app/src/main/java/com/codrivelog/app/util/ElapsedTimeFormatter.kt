package com.codrivelog.app.util

/**
 * Formats raw elapsed-second counts into human-readable strings for the
 * drive-timer notification and UI.
 *
 * This is a stateless object of pure functions with no Android or coroutine
 * dependencies, making it straightforward to unit-test.
 */
object ElapsedTimeFormatter {

    /**
     * Formats [totalSeconds] as `H:MM:SS` (hours may be more than one digit).
     *
     * Examples:
     * - `0` → `"0:00:00"`
     * - `65` → `"0:01:05"`
     * - `3661` → `"1:01:01"`
     * - `36000` → `"10:00:00"`
     *
     * @param totalSeconds Non-negative elapsed time in seconds.
     * @return Formatted string in `H:MM:SS` form.
     */
    fun formatHms(totalSeconds: Long): String {
        require(totalSeconds >= 0) { "totalSeconds must be non-negative, was $totalSeconds" }
        val hours   = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%d:%02d:%02d".format(hours, minutes, seconds)
    }

    /**
     * Formats [totalSeconds] as `H:MM` (truncated to whole minutes).
     *
     * Examples:
     * - `0` → `"0:00"`
     * - `3599` → `"0:59"`
     * - `3600` → `"1:00"`
     *
     * @param totalSeconds Non-negative elapsed time in seconds.
     * @return Formatted string in `H:MM` form.
     */
    fun formatHm(totalSeconds: Long): String {
        require(totalSeconds >= 0) { "totalSeconds must be non-negative, was $totalSeconds" }
        val hours   = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return "%d:%02d".format(hours, minutes)
    }
}
