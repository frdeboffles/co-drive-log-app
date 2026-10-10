package com.codrivelog.app.util

/**
 * Night rules of earlier app versions, reproduced exactly.
 *
 * They are never used to count night. [NightSourceClassifier] uses them to
 * recognize a stored value: if one of these rules gives exactly the stored
 * night minutes at a location and zone the app could have used, the value
 * came from the sun and can be recalculated with the current rule.
 */
object LegacyNightRules {

    /**
     * App 1.0.0 to 1.0.2 (before commit 5f0380f): night was
     * `[UTC midnight, sunrise)` plus `[sunset, next UTC midnight)`. When the
     * UTC sunset wrapped past midnight, as in Colorado, the evening window
     * was empty and the pre-sunrise window started at UTC midnight.
     */
    val BEFORE_1_0_3 = NightMinutesCalculator.NightRule { date, sunTimes, _ ->
        val midnight = date.atStartOfDay()
        val nextMidnight = date.plusDays(1).atStartOfDay()
        val sunrise = sunTimes.sunrise?.let { date.atTime(it) }
        val sunset = NightMinutesCalculator.sunsetDateTime(date, sunTimes)
        when {
            sunrise == null -> listOf(midnight to nextMidnight)
            sunset == null -> emptyList()
            else -> buildList {
                if (sunrise.isAfter(midnight)) add(midnight to sunrise)
                val effectiveNextMidnight = if (sunset.isAfter(nextMidnight)) sunset else nextMidnight
                if (sunset.isBefore(effectiveNextMidnight)) add(sunset to effectiveNextMidnight)
            }
        }
    }

    /**
     * App 1.0.3 to 1.1.0 (commit 5f0380f): night was from one hour after
     * sunset to one hour before sunrise.
     */
    val BUFFERED_1_0_3 = NightMinutesCalculator.NightRule { date, sunTimes, previousDaySunTimes ->
        NightMinutesCalculator.buildNightWindows(
            midnight       = date.atStartOfDay(),
            nextMidnight   = date.plusDays(1).atStartOfDay(),
            previousSunset = NightMinutesCalculator.sunsetDateTime(date.minusDays(1), previousDaySunTimes)?.plusHours(1),
            sunrise        = sunTimes.sunrise?.let { date.atTime(it).minusHours(1) },
            sunset         = NightMinutesCalculator.sunsetDateTime(date, sunTimes)?.plusHours(1),
        )
    }
}
