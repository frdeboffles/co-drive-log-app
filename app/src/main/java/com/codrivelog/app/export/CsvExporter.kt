package com.codrivelog.app.export

import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Generates a CSV export of drive log sessions.
 *
 * The output matches the columns on the Colorado DR 2324 log sheet:
 * `Date, Driving Time, Night Driving, Supervisor Initials, Comments`
 *
 * A grand-total row is appended at the bottom.
 *
 * This class is intentionally framework-free so it can be unit-tested
 * on the JVM without an Android runtime.
 */
object CsvExporter {

    /** Header columns — matches DR 2324 table columns. */
    val HEADER = listOf(
        "Date",
        "Driving Time",
        "Night Driving",
        "Supervisor Initials",
        "Comments",
    )

    /**
     * Write [rows] as a CSV to [outputStream].
     *
     * The stream is flushed but **not** closed so the caller controls
     * the lifecycle (important for `MediaStore` `openOutputStream`).
     *
     * @param rows         Session rows to export.
     * @param studentName  Optional student name written as a comment at the
     *                     top of the file (empty string → line is omitted).
     * @param outputStream Destination stream.
     */
    fun write(
        rows: List<DriveLogRow>,
        studentName: String = "",
        outputStream: OutputStream,
    ) {
        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)

        // Optional student name comment
        if (studentName.isNotBlank()) {
            writer.write("# Student: ${studentName}\n")
        }
        writer.write("# Colorado Drive Time Log Sheet (DR 2324)\n")

        // Header row
        writer.write(toCsvLine(HEADER))

        // Data rows
        for (row in rows) {
            writer.write(
                toCsvLine(
                    listOf(
                        row.date,
                        row.drivingTime,
                        row.nightDriving,
                        row.supervisorInitials,
                        row.comments,
                    )
                )
            )
        }

        // Grand-total row
        val totalMinutes = rows.sumOf { it.totalMinutes }
        val nightMinutes = rows.sumOf { it.nightMinutes }
        writer.write(
            toCsvLine(
                listOf(
                    "GRAND TOTAL",
                    DriveLogRow.formatMinutes(totalMinutes),
                    DriveLogRow.formatMinutes(nightMinutes),
                    "",
                    "",
                )
            )
        )

        writer.flush()
    }

    /**
     * Convert a list of field values to a single RFC 4180-compliant CSV line.
     *
     * Fields that contain a comma, double-quote, or newline are wrapped in
     * double-quotes with internal double-quotes escaped as `""`.
     */
    fun toCsvLine(fields: List<String>): String =
        fields.joinToString(",") { field ->
            if (field.contains(',') || field.contains('"') || field.contains('\n')) {
                "\"${field.replace("\"", "\"\"")}\""
            } else {
                field
            }
        } + "\n"
}
