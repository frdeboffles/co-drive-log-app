package com.codrivelog.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Material DatePicker millis are normalized to UTC midnight. */
fun LocalDate.toDatePickerUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Convert Material DatePicker UTC millis back to a calendar date. */
fun Long.toLocalDateFromDatePickerUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
