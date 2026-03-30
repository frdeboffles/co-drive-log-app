package com.codrivelog.core.dr2324

object Dr2324Mapper {
    const val ROWS_PER_PAGE = 13

    fun map(
        studentProfile: StudentProfile,
        sessions: List<DriveSession>,
    ): Dr2324Document {
        val rows = sessions
            .groupBy { it.date }
            .toList()
            .sortedBy { it.first }
            .map { (date, groupedSessions) ->
                val totalMinutes = groupedSessions.sumOf { it.totalMinutes }
                val nightMinutes = groupedSessions.sumOf { it.nightMinutes }
                val initials = groupedSessions
                    .map { it.verifierInitials.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString("/")
                val comments = groupedSessions
                    .map { it.comments.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(" | ")

                Dr2324Row(
                    date = Dr2324Formatters.formatDate(date),
                    verifierInitials = initials,
                    drivingTime = Dr2324Formatters.formatTime(totalMinutes),
                    nightDrivingTime = if (nightMinutes == 0) "0h 00m" else Dr2324Formatters.formatTime(nightMinutes),
                    comments = comments,
                    totalMinutes = totalMinutes,
                    nightMinutes = nightMinutes,
                )
            }

        val pages = if (rows.isEmpty()) {
            listOf(
                Dr2324Page(
                    rows = emptyList(),
                    pageTotalMinutes = 0,
                    pageNightMinutes = 0,
                )
            )
        } else {
            rows.chunked(ROWS_PER_PAGE).map { chunk ->
                Dr2324Page(
                    rows = chunk,
                    pageTotalMinutes = chunk.sumOf { it.totalMinutes },
                    pageNightMinutes = chunk.sumOf { it.nightMinutes },
                )
            }
        }

        return Dr2324Document(
            studentProfile = studentProfile,
            pages = pages,
            grandTotalMinutes = rows.sumOf { it.totalMinutes },
            grandNightMinutes = rows.sumOf { it.nightMinutes },
        )
    }
}
