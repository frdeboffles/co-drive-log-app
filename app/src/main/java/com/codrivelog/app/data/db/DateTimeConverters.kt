package com.codrivelog.app.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Room [TypeConverter]s for `java.time` types used in database entities.
 *
 * Dates and date-times are persisted as ISO-8601 text strings so they remain
 * human-readable in the SQLite file and sort correctly via lexicographic order.
 *
 * | Kotlin type       | SQLite column type | Example stored value         |
 * |-------------------|--------------------|------------------------------|
 * | [LocalDate]       | TEXT               | `"2025-06-15"`               |
 * | [LocalDateTime]   | TEXT               | `"2025-06-15T14:30:00"`      |
 */
class DateTimeConverters {

    // ---- LocalDate ----

    /**
     * Converts an ISO-8601 date string (e.g. `"2025-06-15"`) read from the
     * database back into a [LocalDate].
     *
     * @param value The raw string value stored in the column, or `null`.
     * @return The parsed [LocalDate], or `null` if [value] is `null`.
     */
    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? =
        value?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }

    /**
     * Converts a [LocalDate] to its ISO-8601 string representation for storage.
     *
     * @param date The date to convert, or `null`.
     * @return The ISO-8601 string (e.g. `"2025-06-15"`), or `null`.
     */
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? =
        date?.format(DateTimeFormatter.ISO_LOCAL_DATE)

    // ---- LocalDateTime ----

    /**
     * Converts an ISO-8601 date-time string (e.g. `"2025-06-15T14:30:00"`)
     * read from the database back into a [LocalDateTime].
     *
     * @param value The raw string value stored in the column, or `null`.
     * @return The parsed [LocalDateTime], or `null` if [value] is `null`.
     */
    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? =
        value?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }

    /**
     * Converts a [LocalDateTime] to its ISO-8601 string representation for storage.
     *
     * @param dateTime The date-time to convert, or `null`.
     * @return The ISO-8601 string (e.g. `"2025-06-15T14:30:00"`), or `null`.
     */
    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? =
        dateTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
